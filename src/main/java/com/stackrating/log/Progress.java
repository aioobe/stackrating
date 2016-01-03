package com.stackrating.log;

import org.slf4j.Logger;

import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

public class Progress {

    final Logger logger;
    final String msg;
    long progress;
    long max;
    long startTime;

    // For throttling log requests
    long lastPrintTime = 0;
    long lastPrintedProgress = -1;
    long sleep = 30000;
    int speedWidth = 1;

    public Progress(Logger logger, String msg, long max) {
        this.logger = logger;
        this.msg = msg;
        this.max = max;
        this.progress = 0;
        this.startTime = System.currentTimeMillis();
    }

    public void incProgress(long steps) {
        setProgress(progress + steps);
    }

    public void setProgress(long newProgress, String... extras) {
        progress = Math.min(max, newProgress);
        long now = System.currentTimeMillis();
        if (lastPrintTime + sleep < now || progress == max) {
            long stepsPerSec = 1000 * (progress - lastPrintedProgress) / (now - lastPrintTime);
            logger.debug(format("%s @ %" + speedWidth + "d/s, %3.0f%%, est. time left: %s%s",
                                msg,
                                stepsPerSec,
                                100.0 * progress / max,
                                timeLeft(now),
                                Stream.of(extras).collect(joining(", ", ", ", ""))));
            lastPrintedProgress = progress;
            lastPrintTime = now;
            speedWidth = Math.max(speedWidth, String.valueOf(stepsPerSec).length());
        }
    }

    private String timeLeft(long t) {
        long elapsedMs = t - startTime;
        if (progress == 0 || elapsedMs < 100) {
            return "n/a";
        }
        long estEndTime = elapsedMs * max / progress + startTime;
        long msLeft = estEndTime - t;
        return msLeft > 3600000 ? format("%2.0f hrs", msLeft / 3600000.0)
             : msLeft >   60000 ? format("%2.0f min", msLeft / 60000.0)
             : format("%2.0f sec", msLeft / 1000.0);
    }
}
