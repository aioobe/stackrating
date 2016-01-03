package com.stackrating.log;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

import static ch.qos.logback.core.spi.FilterReply.NEUTRAL;

public class ThrottlingFilter extends TurboFilter {

    public static Marker THROTTLED = MarkerFactory.getMarker("throttled");

    long lastLogTime = 0;
    long idlePeriod;

    @Override
    public FilterReply decide(Marker marker,
                              Logger logger,
                              Level level,
                              String format,
                              Object[] params,
                              Throwable t) {

        if (marker != THROTTLED) {
            return NEUTRAL;
        }

        long now = System.currentTimeMillis();
        if (now - lastLogTime < idlePeriod) {
            return FilterReply.DENY;
        }

        lastLogTime = now;
        return NEUTRAL;
    }

    public long getIdlePeriod() {
        return idlePeriod;
    }

    public void setIdlePeriod(long idlePeriod) {
        this.idlePeriod = idlePeriod;
    }
}
