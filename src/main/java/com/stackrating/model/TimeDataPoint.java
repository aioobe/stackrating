package com.stackrating.model;

public class TimeDataPoint {

    final long timestamp;
    final double val;

    public TimeDataPoint(long timestamp, double val) {
        this.timestamp = timestamp;
        this.val = val;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getVal() {
        return val;
    }
}
