/*
 * Copyright 2026 OpenAI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.microedition.shell;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Optional virtual clock for J2ME MIDlet bytecode.
 *
 * <p>It accelerates common timing primitives (Thread.sleep, Object.wait,
 * System.currentTimeMillis and System.nanoTime for converted game classes only.
 * It does not change Android/framework threads. Date/Calendar constructors,
 * native clocks and game-specific native timing code are not fully virtualized.</p>
 */
public final class GameTime {
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final double MIN_MULTIPLIER = 1.0d;
    private static final double MAX_MULTIPLIER = 4.0d;

    private static volatile ClockState state = new ClockState(
            System.nanoTime(), System.currentTimeMillis() * NANOS_PER_MILLI, 1.0d);

    private GameTime() {
    }

    /** Configure the active MIDlet profile. Disabled mode is exactly 1x. */
    public static void configure(boolean enabled, float requestedMultiplier) {
        double next = enabled ? requestedMultiplier : 1.0d;
        if (Double.isNaN(next) || Double.isInfinite(next)) {
            next = 1.0d;
        }
        next = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, next));
        long realNow = System.nanoTime();
        // Each profile load starts from the real wall clock so an earlier game's
        // virtual-time offset cannot leak into the next game's timers.
        long gameNow = System.currentTimeMillis() * NANOS_PER_MILLI;
        state = new ClockState(realNow, gameNow, next);
    }

    /** Returns virtual game wall-clock time in epoch milliseconds. */
    public static long currentTimeMillis() {
        ClockState snapshot = state;
        // Preserve exact platform semantics whenever acceleration is disabled.
        if (snapshot.multiplier <= 1.0d) {
            return System.currentTimeMillis();
        }
        return snapshot.gameTimeAt(System.nanoTime()) / NANOS_PER_MILLI;
    }

    /** Returns a monotonic virtual game clock in nanoseconds. */
    public static long nanoTime() {
        ClockState snapshot = state;
        if (snapshot.multiplier <= 1.0d) {
            return System.nanoTime();
        }
        return snapshot.gameTimeAt(System.nanoTime());
    }

    /** Convert a game-time delay into the real wait duration. */
    public static long toRealDelayMillis(long gameMillis) {
        if (gameMillis <= 0) {
            return gameMillis;
        }
        double speed = state.multiplier;
        if (speed <= 1.0d) {
            return gameMillis;
        }
        return Math.max(1L, (long) Math.ceil(gameMillis / speed));
    }

    /** Preserve Thread.yield() semantics; yielding is a scheduler hint, not a timed wait. */
    public static void yield() {
        Thread.yield();
    }

    /** Thread.sleep(long) replacement. */
    public static void sleep(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (millis > Long.MAX_VALUE / NANOS_PER_MILLI) {
            // This duration exceeds the nanosecond representation. It is practically
            // unreachable in a game loop; keep the original positive sleep semantics.
            Thread.sleep(millis);
            return;
        }
        sleepNanos(millis * NANOS_PER_MILLI);
    }

    /** Thread.sleep(long, int) replacement. */
    public static void sleep(long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (nanos < 0 || nanos > 999_999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
        long total = durationNanos(millis, nanos);
        sleepNanos(total);
    }

    /** TimeUnit.sleep(long) replacement with Thread.sleep-compatible interruption. */
    public static void sleep(TimeUnit unit, long timeout) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (timeout > 0) {
            long nanos = unit.toNanos(timeout);
            if (nanos > 0) {
                sleepNanos(nanos);
            }
        }
    }

    /** Thread.join() replacement. An unbounded join remains unbounded. */
    public static void join(Thread thread) throws InterruptedException {
        if (thread == null) {
            throw new NullPointerException("thread");
        }
        thread.join();
    }

    /** Thread.join(long) replacement. */
    public static void join(Thread thread, long millis) throws InterruptedException {
        if (thread == null) {
            throw new NullPointerException("thread");
        }
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        thread.join(millis == 0 ? 0 : toRealDelayMillis(millis));
    }

    /** Thread.join(long, int) replacement. */
    public static void join(Thread thread, long millis, int nanos) throws InterruptedException {
        if (thread == null) {
            throw new NullPointerException("thread");
        }
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (nanos < 0 || nanos > 999_999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
        long requested = durationNanos(millis, nanos);
        if (requested == 0) {
            thread.join(0, 0);
            return;
        }
        long real = scaleNanos(requested, state.multiplier);
        thread.join(real / NANOS_PER_MILLI, (int) (real % NANOS_PER_MILLI));
    }

    /** LockSupport.parkNanos(long) replacement. */
    public static void parkNanos(long nanos) {
        if (nanos > 0) {
            LockSupport.parkNanos(scaleNanos(nanos, state.multiplier));
        } else {
            LockSupport.parkNanos(nanos);
        }
    }

    /** LockSupport.parkNanos(Object, long) replacement. */
    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            LockSupport.parkNanos(blocker, scaleNanos(nanos, state.multiplier));
        } else {
            LockSupport.parkNanos(blocker, nanos);
        }
    }

    /** LockSupport.parkUntil(long) replacement using the virtual wall clock when enabled. */
    public static void parkUntil(long deadlineMillis) {
        ClockState snapshot = state;
        if (snapshot.multiplier <= 1.0d) {
            LockSupport.parkUntil(deadlineMillis);
            return;
        }
        long remainingMillis = deadlineMillis - currentTimeMillis();
        if (remainingMillis > 0) {
            long requestedNanos = durationNanos(remainingMillis, 0);
            LockSupport.parkNanos(scaleNanos(requestedNanos, snapshot.multiplier));
        }
    }

    /** LockSupport.parkUntil(Object, long) replacement. */
    public static void parkUntil(Object blocker, long deadlineMillis) {
        ClockState snapshot = state;
        if (snapshot.multiplier <= 1.0d) {
            LockSupport.parkUntil(blocker, deadlineMillis);
            return;
        }
        long remainingMillis = deadlineMillis - currentTimeMillis();
        if (remainingMillis > 0) {
            long requestedNanos = durationNanos(remainingMillis, 0);
            LockSupport.parkNanos(blocker, scaleNanos(requestedNanos, snapshot.multiplier));
        }
    }

    private static long scaleNanos(long requestedNanos, double speed) {
        if (requestedNanos <= 0 || speed <= 1.0d) {
            return requestedNanos;
        }
        return Math.max(1L, (long) Math.ceil(requestedNanos / speed));
    }

    private static void sleepNanos(long requestedNanos) throws InterruptedException {
        ClockState snapshot = state;
        if (requestedNanos == 0 || snapshot.multiplier <= 1.0d) {
            long millis = requestedNanos / NANOS_PER_MILLI;
            int nanos = (int) (requestedNanos % NANOS_PER_MILLI);
            Thread.sleep(millis, nanos);
            return;
        }
        long realNanos = scaleNanos(requestedNanos, snapshot.multiplier);
        long millis = realNanos / NANOS_PER_MILLI;
        int nanos = (int) (realNanos % NANOS_PER_MILLI);
        Thread.sleep(millis, nanos);
    }

    /** Object.wait() replacement. The caller must already own the monitor. */
    public static void waitOn(Object monitor) throws InterruptedException {
        requireMonitorOwnership(monitor);
        synchronized (monitor) {
            monitor.wait();
        }
    }

    /** Object.wait(long) replacement. */
    public static void waitOn(Object monitor, long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        requireMonitorOwnership(monitor);
        long realMillis = millis == 0 ? 0 : toRealDelayMillis(millis);
        synchronized (monitor) {
            monitor.wait(realMillis);
        }
    }

    /** Object.wait(long, int) replacement. */
    public static void waitOn(Object monitor, long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (nanos < 0 || nanos > 999_999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
        requireMonitorOwnership(monitor);
        long requestedNanos = durationNanos(millis, nanos);
        long realNanos = requestedNanos;
        double speed = state.multiplier;
        if (requestedNanos > 0 && speed > 1.0d) {
            realNanos = scaleNanos(requestedNanos, speed);
        }
        long realMillis = realNanos / NANOS_PER_MILLI;
        int realNanosRemainder = (int) (realNanos % NANOS_PER_MILLI);
        synchronized (monitor) {
            monitor.wait(realMillis, realNanosRemainder);
        }
    }

    private static long durationNanos(long millis, int nanos) {
        if (millis > (Long.MAX_VALUE - nanos) / NANOS_PER_MILLI) {
            return Long.MAX_VALUE;
        }
        return millis * NANOS_PER_MILLI + nanos;
    }

    private static void requireMonitorOwnership(Object monitor) {
        if (monitor == null) {
            throw new NullPointerException("monitor");
        }
        if (!Thread.holdsLock(monitor)) {
            throw new IllegalMonitorStateException();
        }
    }

    private static final class ClockState {
        final long realAnchorNanos;
        final long gameAnchorNanos;
        final double multiplier;

        ClockState(long realAnchorNanos, long gameAnchorNanos, double multiplier) {
            this.realAnchorNanos = realAnchorNanos;
            this.gameAnchorNanos = gameAnchorNanos;
            this.multiplier = multiplier;
        }

        long gameTimeAt(long realNanos) {
            long elapsed = realNanos - realAnchorNanos;
            return gameAnchorNanos + (long) (elapsed * multiplier);
        }
    }
}
