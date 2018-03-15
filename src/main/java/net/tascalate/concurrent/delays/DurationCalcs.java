package net.tascalate.concurrent.delays;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.LongBinaryOperator;

class DurationCalcs {
    private DurationCalcs() {}
    
    static Duration safeTransform(Duration duration, LongBinaryOperator isConversionSafe, LongBinaryOperator conversion) {
        long amount;
        int dimIdx;
        // Try to get value with best precision without throwing ArythmeticException due to overflow
        if (duration.compareTo(MAX_BY_NANOS) < 0) {
            amount = duration.toNanos();
            dimIdx = 0;
        } else if (duration.compareTo(MAX_BY_MILLIS) < 0) {
            amount = duration.toMillis();
            dimIdx = 2;
        } else {
            amount = duration.getSeconds();
            dimIdx = 3;
        }
        int count = TIME_DIMENSIONS.length;
        for (; dimIdx < count; dimIdx++) {
            if (toBoolean(isConversionSafe.applyAsLong(amount, dimIdx))) {
                amount = conversion.applyAsLong(amount, dimIdx);
                return Duration.of(amount, TIME_DIMENSIONS[dimIdx]);
            } else {
                amount /= 1000;
                // try again on next iteration
            }
        }
        // return max possible value if doesn't fit
        return MAX_DURATION;
       
    }
    
    static long safeExtractAmount(Duration duration, int targetDimIdx) {
        long amount;
        int sourceDimIdx;
        if (duration.compareTo(MAX_BY_NANOS) < 0) {
            amount = duration.toNanos();
            sourceDimIdx = 0;
        } else if (duration.compareTo(MAX_BY_MILLIS) < 0) {
            amount = duration.toMillis();
            sourceDimIdx = 2;
        } else {
            amount = duration.getSeconds();
            sourceDimIdx = 3;
        }
        // No conversion necessary
        if (sourceDimIdx == targetDimIdx) {
            return amount;
        }
        double factor = Math.pow(1000, sourceDimIdx - targetDimIdx);
        if ((double)Long.MAX_VALUE / amount > factor) {
            return  (long)(amount * factor);
        } else {
            return Long.MAX_VALUE;
        }
    }
    
    static long toBoolean(boolean v) {
        return v ? 1 : 0;
    }
    
    private static boolean toBoolean(long v) {
        return v != 0;
    }
    
    private static final ChronoUnit[] TIME_DIMENSIONS = new ChronoUnit[]{
        ChronoUnit.NANOS, ChronoUnit.MICROS, ChronoUnit.MILLIS, ChronoUnit.SECONDS
    };
    
    private static final Duration MAX_BY_NANOS  = Duration.ofNanos(Long.MAX_VALUE);
    private static final Duration MAX_BY_MILLIS = Duration.ofMillis(Long.MAX_VALUE);
    private static final Duration MAX_DURATION  = Duration.ofSeconds(Long.MAX_VALUE).withNanos(999_999_999);
}
