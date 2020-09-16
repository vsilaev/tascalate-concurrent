package net.tascalate.concurrent.delays;

import java.time.Duration;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CompletionStage;

@SuppressWarnings("unused")
public class NanosTest {
    
    public static void main(String[] args) {
        
//        Duration dd = Duration.ofMillis(Long.MAX_VALUE);
        System.out.println(Long.MAX_VALUE);
        System.out.println(-Long.MIN_VALUE);
        System.out.println(Long.MIN_VALUE);
        System.out.println(-Long.MAX_VALUE);
        System.out.println("--REF-- = " + Long.MAX_VALUE);
//        System.out.println("NANOS   = " + DurationCalcs.safeExtractAmount(dd, 0));
//        System.out.println("MICROS  = " + DurationCalcs.safeExtractAmount(dd, 1));
//        System.out.println("MILLIS  = " + DurationCalcs.safeExtractAmount(dd, 2));
//        System.out.println("SECONDS = " + DurationCalcs.safeExtractAmount(dd, 3));
//        
//        System.out.println(Duration.ofMillis(Long.MAX_VALUE).getSeconds());
//        System.out.println("++");
        Duration d = Duration.ofSeconds((long)(Long.MAX_VALUE * 0.95));
        System.out.println(d.getSeconds());
        System.out.println(random1(d).getSeconds());
        System.out.println(random1(d).getSeconds());
        System.out.println(random1(d).getSeconds());
        System.out.println(random1(d).getSeconds());
        System.out.println(random1(d).getSeconds());
        System.out.println(random1(d).getSeconds());
    }

    private static Duration exp(Duration initialDelay, double multiplier, int pow) {
        double factor = Math.pow(multiplier, pow);
        return DurationCalcs.safeTransform(
                initialDelay, 
                (amount, dimIdx) -> Long.MAX_VALUE / amount > factor ? 1 : 0, 
                (amount, dimIdx) -> (long)(amount * factor)
        );
    }
    
    private static Duration random1(Duration initialDelay) {
        double randomizer = random.nextDouble();
        return DurationCalcs.safeTransform(
                initialDelay, 
                (amount, dimIdx) -> checkBounds1(amount, randomizer) ? 1 : 0, 
                (amount, dimIdx) -> addRandomJitter1(amount, randomizer)
        );
    }

    private static Duration random2(Duration initialDelay, double multiplier, int pow) {
        double randomizer = random.nextDouble();
        return DurationCalcs.safeTransform(
                initialDelay, 
                (amount, dimIdx) -> checkBounds1(amount, randomizer) ? 1 : 0, 
                (amount, dimIdx) -> addRandomJitter1(amount, randomizer)
        );
    }

    
    static final Random random = new Random();

    
    static long addRandomJitter1(long initialDelay, double randomizer) {
        double randomMultiplier = (1 - 2 * randomizer) * 0.1;
        System.out.println("::MULT:: " + (long)(initialDelay * (1 + randomMultiplier)));
        return Math.max(0, (long) (initialDelay * (1 + randomMultiplier)));
    }
    
    static boolean checkBounds1(long initialDelay, double randomizer) {
        double randomMultiplier = (1 - 2 * randomizer) * 0.1;
        System.out.println("::MULT:: " + (long)(initialDelay * (1 + randomMultiplier)));
        return (double)Long.MAX_VALUE / initialDelay > Math.abs(randomMultiplier + 1);
    }
    
    /*
        if (initialDelay > 0) {
            if (uniformRandom > 0)
                return Long.MAX_VALUE - initialDelay > uniformRandom; //+MAX > +A + +B
            else
                return Long.MIN_VALUE + initialDelay < uniformRandom; //-MIN < -A - (+B) 
        } else {
            if (uniformRandom < 0)
                return Long.MIN_VALUE - initialDelay < uniformRandom; //-MIN < -A + (-B)
            else
                return Long.MAX_VALUE + initialDelay > uniformRandom; //+MAX > +A - (-B)
        }
     
     */
}
