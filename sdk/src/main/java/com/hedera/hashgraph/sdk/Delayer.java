package com.hedera.hashgraph.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

final class Delayer {
    private static final Logger logger = LoggerFactory.getLogger(Delayer.class);

    private static final Duration MIN_DELAY = Duration.ofMillis(500);

    private Delayer() {
    }

    static CompletableFuture<Void> delayBackOff(int attempt, Executor executor) {
        var interval = MIN_DELAY.multipliedBy(ThreadLocalRandom.current().nextLong(1L << attempt));

        return delayFor(interval.toMillis(), executor);
    }

    static CompletableFuture<Void> delayFor(long milliseconds, Executor executor) {
        logger.atTrace()
            .addArgument((double) milliseconds / 1000.0)
            .log("waiting for {} seconds before trying again");

        return CompletableFuture.runAsync(
            () -> {
            },
            new DelayedExecutor(milliseconds, TimeUnit.MILLISECONDS, executor));
    }

    static <T> CompletableFuture<T> orTimeout(CompletableFuture<T> future, long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit);
        return future.whenComplete(new Canceller(FutureDelayer.delay(new Timeout(future), timeout, unit)));
    }

    private static final class DelayedExecutor implements Executor {
        final long delay;
        final TimeUnit unit;
        final Executor executor;

        DelayedExecutor(long delay, TimeUnit unit, Executor executor) {
            this.delay = delay;
            this.unit = unit;
            this.executor = executor;
        }

        @Override
        public void execute(Runnable r) {
            FutureDelayer.delay(new TaskSubmitter(executor, r), delay, unit);
        }
    }

    private static final class FutureDelayer {
        static final ScheduledThreadPoolExecutor delayer;

        static {
            delayer = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory());
        }

        static ScheduledFuture<?> delay(Runnable command, long delay,
                                        TimeUnit unit) {
            return delayer.schedule(command, delay, unit);
        }

        static final class DaemonThreadFactory implements ThreadFactory {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("CompletableFutureDelayScheduler");
                return t;
            }
        }
    }

    private static final class TaskSubmitter implements Runnable {
        final Executor executor;
        final Runnable action;

        TaskSubmitter(Executor executor, Runnable action) {
            this.executor = executor;
            this.action = action;
        }

        @Override
        public void run() {
            executor.execute(action);
        }
    }

    private static final class Timeout implements Runnable {
        @Nullable
        final CompletableFuture<?> f;

        Timeout(@Nullable CompletableFuture<?> f) {
            this.f = f;
        }

        @Override
        public void run() {
            if (f != null && !f.isDone())
                f.completeExceptionally(new TimeoutException());
        }
    }

    private static final class Canceller implements BiConsumer<Object, Throwable> {
        @Nullable
        final Future<?> f;

        Canceller(@Nullable Future<?> f) {
            this.f = f;
        }

        @Override
        public void accept(Object ignore, Throwable ex) {
            if (ex == null && f != null && !f.isDone())
                f.cancel(false);
        }
    }
}
