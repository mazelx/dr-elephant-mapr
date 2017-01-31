/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.linkedin.drelephant.spark.fetchers

import java.net.URI
import java.text.SimpleDateFormat
import java.util.{Calendar, SimpleTimeZone}

import scala.async.Async
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.{Exception, NonFatal}
import scala.util.{Try, Success, Failure}
import sys.process._
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.cardlytics.drelephant.spark.data.SparkRestDerivedData
import com.linkedin.drelephant.spark.fetchers.statusapiv1.{ApplicationAttemptInfo, ApplicationInfo, ExecutorSummary, JobData, StageData}
import javax.ws.rs.client.{Client, ClientBuilder, WebTarget}
import javax.ws.rs.core.MediaType
import javax.ws.rs.NotFoundException

import com.cardlytics.drelephant.exceptions.{InvalidJSONResponseException, MissingYarnHistoryServerInfoException}
import org.apache.log4j.Logger
import org.apache.spark.SparkConf


/**
  * A client for getting data from the Spark monitoring REST API, e.g. <https://spark.apache.org/docs/1.4.1/monitoring.html#rest-api>.
  *
  * Jersey classloading seems to be brittle (at least when testing in the console), so some of the implementation is non-lazy
  * or synchronous when needed.
  */
class SparkRestClient(sparkConf: SparkConf) {

  import SparkRestClient._
  import Async.{async, await}

  private val logger: Logger = Logger.getLogger(classOf[SparkRestClient])

  private val client: Client = ClientBuilder.newClient()

  private val historyServerUri: URI = sparkConf.getOption(HISTORY_SERVER_ADDRESS_KEY) match {
    case Some(historyServerAddress) =>
      if (historyServerAddress.slice(0, 4) == s"http") {
        val baseUri = new URI(s"${historyServerAddress}")
        require(baseUri.getPath == "")
        baseUri
      } else {
        val baseUri = new URI(s"http://${historyServerAddress}")
        require(baseUri.getPath == "")
        baseUri
      }
    case None =>
      //throw new IllegalArgumentException("spark.yarn.historyServer.address not provided; can't use Spark REST API")
      val cmd = "mar"
      val baseUri = new URI("maprcli urls -name historyserver | grep http | grep '://'") | grep
  }

  private val apiTarget: WebTarget = client.target(historyServerUri).path(API_V1_MOUNT_PATH)

  def fetchData(appId: String)(implicit ec: ExecutionContext): Future[SparkRestDerivedData] = {
    val appTarget = apiTarget.path(s"applications/${appId}")
    logger.info(s"calling REST API at ${appTarget.getUri}")
    try {
      Option(getApplicationInfo(appTarget)) match {
        case Some(applicationInfo) => {
          // Limit scope of async.
          async {
            val lastAttemptId = applicationInfo.attempts.maxBy {
              _.startTime
            }.attemptId
            lastAttemptId match {
              case Some(attemptId) => {
                // yarn-cluster mode
                val attemptTarget = appTarget.path(attemptId)
                val futureJobDatas = async {
                  getJobDatas(attemptTarget)
                }
                val futureStageDatas = async {
                  getStageDatas(attemptTarget)
                }
                val futureExecutorSummaries = async {
                  getExecutorSummaries(attemptTarget)
                }
                SparkRestDerivedData(
                  applicationInfo,
                  await(futureJobDatas),
                  await(futureStageDatas),
                  await(futureExecutorSummaries)
                )
              }
              case _ => {
                // yarn-client mode
                val attemptTarget = appTarget
                val futureJobDatas = async {
                  getJobDatas(attemptTarget)
                }
                val futureStageDatas = async {
                  getStageDatas(attemptTarget)
                }
                val futureExecutorSummaries = async {
                  getExecutorSummaries(attemptTarget)
                }
                SparkRestDerivedData(
                  applicationInfo,
                  await(futureJobDatas),
                  await(futureStageDatas),
                  await(futureExecutorSummaries)
                )
              }
            }
          }
        }
        case _ => {
          throw new MissingYarnHistoryServerInfoException(s"application ${appId} has aged out of the YARN history server; skipping job and job will not be retried")
        }
      }
    } catch {
      case e: com.fasterxml.jackson.core.JsonParseException => throw new InvalidJSONResponseException(s"error parsing JSON response from ${appTarget.getUri}; skipping job and job will not be retried")
      case e: javax.ws.rs.NotFoundException => throw new MissingYarnHistoryServerInfoException(s"Application ${appId} has aged out of the YARN History Server; skipping job and job will not be retried")
      case NonFatal(e) => throw e
    }
  }

  private def getApplicationInfo(appTarget: WebTarget): ApplicationInfo = {
    get(appTarget, SparkRestObjectMapper.readValue[ApplicationInfo])
  }

  private def getJobDatas(attemptTarget: WebTarget): Seq[JobData] = {
    val target = attemptTarget.path("jobs")
    try {
      get(target, SparkRestObjectMapper.readValue[Seq[JobData]])
    } catch {
      case NonFatal(e) => {
        logger.error(s"error reading JobData for ${target.getUri}, skipping data")
        throw e
      }
    }
  }

  private def getStageDatas(attemptTarget: WebTarget): Seq[StageData] = {
    val target = attemptTarget.path("stages")
    try {
      get(target, SparkRestObjectMapper.readValue[Seq[StageData]])
    } catch {
      case NonFatal(e) => {
        logger.error(s"error reading StageData for ${target.getUri}, skipping data")
        throw e
      }
    }
  }

  private def getExecutorSummaries(attemptTarget: WebTarget): Seq[ExecutorSummary] = {
    val target = attemptTarget.path("executors")
    try {
      get(target, SparkRestObjectMapper.readValue[Seq[ExecutorSummary]])
    } catch {
      case NonFatal(e) => {
        logger.error(s"error reading ExecutorSummaries for ${target.getUri}, skipping data")
        throw e
      }
    }
  }
}

object SparkRestClient {
  val HISTORY_SERVER_ADDRESS_KEY = "spark.yarn.historyServer.address"
  val API_V1_MOUNT_PATH = "api/v1"

  val SparkRestObjectMapper = {
    val dateFormat = {
      val iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'GMT'")
      val cal = Calendar.getInstance(new SimpleTimeZone(0, "GMT"))
      iso8601.setCalendar(cal)
      iso8601
    }

    val objectMapper = new ObjectMapper() with ScalaObjectMapper
    objectMapper.setDateFormat(dateFormat)
    objectMapper.registerModule(DefaultScalaModule)
    objectMapper
  }

  def get[T](webTarget: WebTarget, converter: String => T): T = {
    try {
      converter(webTarget.request(MediaType.APPLICATION_JSON).get(classOf[String]))
    }
    catch {
      case e: com.fasterxml.jackson.core.JsonParseException => throw new InvalidJSONResponseException(s"error parsing JSON response; skipping job and job will not be retried")
      case NonFatal(e) => throw e
    }
  }
}

