package org.apache.solr.benchmarks;


import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A custom executor that blocks on `run` until one of the below condition fulfills:
 * <ol>
 *  <li>If duration is defined, such duration is reached since run is invoked</li>
 *  <li>If taskSupplier no longer provides any more tasks</li>
 *  <li>If execution count has reached maxExecution if provided</li>
 *  <li>If there is any uncaught exception</li>
 * </ol>
 *
 * More precisely, the executor would stop drawing tasks from task supplier but would still complete tasks that are
 * currently executing. For tasks that are submitted to the underlying executor but not yet executed, they will be
 * dropped iff the `StopReason` is `Duration`.
 *
 * Tasks will be submitted adhering to the rpm if provided.
 */
public class ControlledExecutor {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Supplier<Callable> taskSupplier;
    private final ExecutorService executor;
    private String label;
    private final Integer duration; //if defined, this executor should cease executing more tasks once duration is reached
    private Long endTime; //this executor should cease executing more tasks once this time is reached, computed from duration
    private final Long maxExecution; //max execution count, once this read the executor should no longer execute more tasks
    private final int warmCount; //executions before this would not be tracked in stats

    final SynchronizedDescriptiveStatistics stats;
    private final RateLimiter rateLimiter;
    private final BackPressureLimiter backPressureLimiter;
    private long startTime;

    public ControlledExecutor(String label, int threads, Integer duration, Integer rpm, Long maxExecution, int warmCount, Supplier<Callable> taskSupplier) {
        this.label = label;
        this.duration = duration;
        this.maxExecution = maxExecution;
        this.warmCount = warmCount;
        this.stats = new SynchronizedDescriptiveStatistics();
        this.taskSupplier = taskSupplier;
        executor = new ThreadPoolExecutor(threads, threads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, ControlledExecutor.class.getSimpleName() + "-" + label);
                    }
                });
        rateLimiter = rpm != null ? new RateLimiter(rpm) : null;

        backPressureLimiter = new BackPressureLimiter(threads * 10); //at most 10 * # of thread pending tasks
    }

    public void run() throws InterruptedException {
        startTime = System.currentTimeMillis();

        if (duration != null) {
            endTime = startTime + (1000 * duration);
        }
        AtomicLong submissionCount = new AtomicLong();
        AtomicLong executionCount = new AtomicLong();

        Timer progressTimer = new Timer();

        progressTimer.schedule( new TimerTask() {
            public void run() {
                log("Submitted " + submissionCount.get() + " task(s), executed " + executionCount.get());
                long timeElapsed = System.currentTimeMillis() - startTime;
                if (timeElapsed > 0) {
                    long currentRpm = executionCount.get() * 1000 * 60 / timeElapsed;
                    log("Current rpm: " + currentRpm + (rateLimiter != null ? (" target rpm: " + rateLimiter.targetRpm) : ""));
                }
            }
        }, 0, 10*1000);

        AtomicBoolean dropTaskFlag = new AtomicBoolean(false);
        try {
            StopReason stopReason;
            while ((stopReason = shouldStop(submissionCount.get())) == null) {
                if (rateLimiter != null) {
                    rateLimiter.waitIfRequired();
                }
                backPressureLimiter.waitIfRequired(submissionCount.get(), executionCount, 10000);

                Callable task = taskSupplier.get();
                if (task == null) { //no more runners available
                    log("Exhausted task supplier.");
                    break;
                }
                executor.submit(() -> {
                    if (dropTaskFlag.get()) { //do not process the rest of this
                        return null;
                    }
                    long start = System.nanoTime();
                    task.call();
                    if (executionCount.incrementAndGet() > warmCount) {
                        stats.addValue((System.nanoTime() - start) / 1000_000.0);
                    }
                    return null;
                });
                submissionCount.incrementAndGet();
            }

            if (stopReason == StopReason.DURATION) { //if it was stopped because of duration, drop the remaining tasks
                dropTaskFlag.set(true);
            }

        } finally {
            executor.shutdown();
            if (dropTaskFlag.get()) {
                log("Now waiting for executing jobs to finish execution. The rest of the submitted jobs will be dropped");
            } else {
                log("Now waiting for all executing/submitted jobs to finish execution.");
            }

            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            progressTimer.cancel();

            long currTime = System.currentTimeMillis();
            long rpm = (currTime - startTime) > 0 ? executionCount.get() * 1000 * 60 / (currTime - startTime) : executionCount.get() * 1000 * 60;
            System.out.println("Time elapsed : " + (currTime - startTime)  + " total execution count : "+ executionCount.get() + " rpm : " + rpm + " benchmarked executions: "+stats.getN()) ;
        }
    }

    enum StopReason {
        DURATION, MAX_EXECUTION
    }
    private synchronized StopReason shouldStop(long currentCount) {
    	if (maxExecution != null && currentCount >= maxExecution) {
            log("Max execution count " + maxExecution + " reached, exiting...");
   			return StopReason.MAX_EXECUTION;
    	}
        if (endTime != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime > endTime) {
                log("Duration " + duration + " secs reached, exiting...");
                return StopReason.DURATION;
            }
        }
        return null;
    }

    private void log(String message) {
        log.info("(" + label + ") " +  message);
    }

    /**
     * Pause job submission if the execution cannot catch up
     */
    private class BackPressureLimiter {
        private final int maxPendingTasks;

        private BackPressureLimiter(int maxPendingTasks) {
            this.maxPendingTasks = maxPendingTasks;
        }

        public void waitIfRequired(long submissionCount, AtomicLong executionCount, long timeout) throws InterruptedException {
            long endTime = System.currentTimeMillis() + timeout;
            while ((submissionCount - executionCount.get()) >= maxPendingTasks) {
                if (System.currentTimeMillis() >= endTime) {
                    return;
                }
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }
    }
}
