package com.stackrating;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public enum Util {
    ; // Utility class -- not instantiable

    public static String formatInstant(Instant i) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                .withZone(ZoneOffset.UTC)
                                .format(i);
    }

    public static int clamp(int min, int val, int max) {
        return Math.max(min, Math.min(val, max));
    }

    static Optional<Integer> parseInt(String str) {
        try {
            return Optional.of(Integer.parseInt(str));
        } catch (NumberFormatException nfe) {
            return Optional.empty();
        }
    }
}
