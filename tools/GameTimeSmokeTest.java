import javax.microedition.shell.GameTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class GameTimeSmokeTest {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
    private static long elapsedMillis(Runnable r) {
        long start = System.nanoTime();
        r.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
    private static void bounded(long actual, long low, long high, String name) {
        check(actual >= low && actual <= high, name + " elapsed=" + actual + "ms expected [" + low + "," + high + "]");
    }
    public static void main(String[] args) throws Exception {
        GameTime.configure(false, 4f);
        long real = System.currentTimeMillis();
        long virtual = GameTime.currentTimeMillis();
        check(Math.abs(real - virtual) <= 50, "1x currentTimeMillis must track real clock");
        long n1 = GameTime.nanoTime();
        Thread.sleep(15);
        long n2 = GameTime.nanoTime();
        check(n2 > n1 && n2 - n1 < 100_000_000L, "1x nanoTime must track real monotonic clock");

        GameTime.configure(true, 2f);
        // Thread.yield is a scheduling hint and must not become a checked/interruptible sleep.
        GameTime.yield();

        long wallStart = GameTime.currentTimeMillis();
        long realStart = System.nanoTime();
        Thread.sleep(60);
        long virtualDelta = GameTime.currentTimeMillis() - wallStart;
        long realDelta = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - realStart);
        check(virtualDelta >= realDelta + 25, "virtual currentTimeMillis should advance faster when enabled");

        bounded(elapsedMillis(() -> {
            try { GameTime.sleep(200); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }), 65, 170, "Thread.sleep 2x");

        Object monitor = new Object();
        bounded(elapsedMillis(() -> {
            synchronized (monitor) {
                try { GameTime.waitOn(monitor, 200); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
            }
        }), 65, 170, "Object.wait 2x");
        bounded(elapsedMillis(() -> {
            synchronized (monitor) {
                try { GameTime.waitOn(monitor, 30, 200_000); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
            }
        }), 5, 100, "Object.wait nanos 2x");

        Thread joined = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) { }
        });
        joined.start();
        bounded(elapsedMillis(() -> {
            try { GameTime.join(joined, 200); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }), 65, 170, "Thread.join 2x");
        check(joined.isAlive(), "timed join should return before 300ms worker ends");
        joined.join();

        bounded(elapsedMillis(() -> { try { GameTime.sleep(TimeUnit.MILLISECONDS, 200); } catch (InterruptedException e) { throw new RuntimeException(e); } }), 65, 170, "TimeUnit.sleep 2x");
        bounded(elapsedMillis(() -> GameTime.parkNanos(200_000_000L)), 65, 170, "LockSupport.parkNanos 2x");

        long deadline = GameTime.currentTimeMillis() + 200;
        bounded(elapsedMillis(() -> GameTime.parkUntil(deadline)), 65, 170, "LockSupport.parkUntil 2x");

        // An interrupted thread must keep its interrupt flag across Thread.yield().
        Thread.currentThread().interrupt();
        GameTime.yield();
        check(Thread.currentThread().isInterrupted(), "yield must preserve interrupt status");
        Thread.interrupted();

        GameTime.configure(true, 4f);
        bounded(elapsedMillis(() -> {
            try { GameTime.sleep(200); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }), 25, 125, "Thread.sleep 4x");

        Thread.currentThread().interrupt();
        boolean interruptedThrown = false;
        try { GameTime.sleep(TimeUnit.MILLISECONDS, 50); }
        catch (InterruptedException expected) { interruptedThrown = true; }
        check(interruptedThrown, "TimeUnit.sleep must throw InterruptedException when interrupted");
        check(!Thread.currentThread().isInterrupted(), "Thread.sleep-compatible helper clears interrupt status");

        GameTime.configure(false, 4f);
        bounded(elapsedMillis(() -> {
            try { GameTime.sleep(80); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }), 60, 160, "disabled sleep 1x");

        boolean negativeRejected = false;
        try { GameTime.sleep(-1); } catch (IllegalArgumentException expected) { negativeRejected = true; }
        check(negativeRejected, "negative sleep validation");

        boolean invalidMonitorRejected = false;
        try { GameTime.waitOn(new Object(), 1); } catch (IllegalMonitorStateException expected) { invalidMonitorRejected = true; }
        check(invalidMonitorRejected, "wait must preserve monitor ownership check");

        System.out.println("PASS: 1x clock, virtual wall clock, yield interrupt semantics, sleep, timed wait (ms+nanos), timed join, TimeUnit.sleep, LockSupport parks, 2x/4x scaling, reset, argument validation");
    }
}
