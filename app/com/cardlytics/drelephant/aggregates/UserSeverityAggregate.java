package com.cardlytics.drelephant.aggregates;

import com.linkedin.drelephant.analysis.Severity;

public class UserSeverityAggregate {

    private String _username;
    private Severity _severity;
    private int _count;
    private Long _sortidx;

    private long encode(String input) {
        String rtn = "";
        String first5 = input.toLowerCase().concat("      ").substring(0,5);
        int ascii;
        for (int i = 0; i < 5 ; ++i) {
            ascii = (int)first5.charAt(i);
            rtn = rtn.concat(String.valueOf(ascii));
        }
        return Long.parseLong(rtn);
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
    public String getCountText() {
        return String.valueOf(this._count);
    }
    public Long getSortIndex() {
        return this._sortidx;
    }

    public void set(String username, Severity severity, int count) {
        this._username = username;
        this._severity = severity;
        this._count = count;
        this._sortidx = Long.valueOf(String.valueOf(this._count).concat(String.valueOf(this.encode(this._username)).concat(String.valueOf(this._severity.getValue()))));
    }

}

