package com.bretlowery.drelephant.aggregates;

import com.linkedin.drelephant.analysis.Severity;

import java.text.SimpleDateFormat;

public class UserSeverityAggregate {

    private String _username = "";
    private Severity _severity = Severity.NONE;
    private int _count = 0;
    private Long _startTime = Long.MIN_VALUE;
    private Long _finishTime = Long.MIN_VALUE;

    private String encode(String input) {
        String rtn = "";
        String first5 = input.toLowerCase().concat("      ").substring(0,5);
        for (int i = 0; i < 5 ; ++i) {
            int ascii = (int)first5.charAt(i);
            rtn = rtn.concat(String.valueOf(ascii));
        }
        return String.valueOf(Long.parseLong(rtn));
    }

    public String getUsername() {
        return this._username;
    }
    public Severity getSeverity() {
        return this._severity;
    }
    public String getSeverityText() {
        return this._severity.getText();
    }
    public int getCount() {
        return this._count;
    }
    public String getStartTimeString() { return new SimpleDateFormat("yyyy.MM.dd HH.mm.ss").format(_startTime); }
    public String getFinishTimeString() { return new SimpleDateFormat("yyyy.MM.dd HH.mm.ss").format(_finishTime); }
    public Long getStartTimeLong() { return _startTime; }
    public Long getFinishTimeLong() { return _finishTime; }
    public String getCountText() {
        return String.valueOf(this._count);
    }
    public Long getSortIndex() {
        return Long.valueOf(String.valueOf(this._count).concat(this.encode(this._username).concat(String.valueOf(this._severity.getValue()))));
    }

    public void set(String username, Severity severity, int count, Long startTime, Long finishTime) {
        this._username = username;
        this._severity = severity;
        this._count = count;
        this._startTime = startTime;
        this._finishTime = finishTime;
    }

}
