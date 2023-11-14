package org.apache.solr.benchmarks;


import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
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
public class ControlledExecutor<R> {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Supplier<Callable<R>> taskSupplier;
    private final ExecutorService executor;
    private final ExecutionListener<BenchmarksMain.OperationKey, R>[] executionListeners;
    private String label;
    private final Integer duration; //if defined, this executor should cease executing more tasks once duration is reached
    private Long endTime; //this executor should cease executing more tasks once this time is reached, computed from duration
    private final Long maxExecution; //max execution count, once this read the executor should no longer execute more tasks
    private final int warmCount; //executions before this would not be tracked in stats

    final SynchronizedDescriptiveStatistics stats;
    private final RateLimiter rateLimiter;
    private final BackPressureLimiter backPressureLimiter;
    private long startTime;

    interface ExecutionListener<T, R> {
        /**
         * Gets invoked when a callable finishes execution by this executor. Take note that execution of warmups will
         * not trigger this. And this might get invoked by multiple threads concurrently
         *
         * Take note that this gets executed within the same thread as the executor worker. Therefore, slow operation
         * can affect the actual RPM (runs per minute)
         *
         * @param typeKey   A key that indicates the type of callable executed, null if the callable is not of {@link CallableWithType}
         * @param result    The result from callable
         * @param duration  The duration of to execute the callable in nano second
         */
        void onExecutionComplete(T typeKey, R result, long duration);
    }

    public interface CallableWithType<V> extends Callable<V> {
        BenchmarksMain.OperationKey getType();
    }

    public ControlledExecutor(String label, int threads, Integer duration, Integer rpm, Long maxExecution, int warmCount, Supplier<Callable<R>> taskSupplier) {
        this(label, threads, duration, rpm, maxExecution, warmCount, taskSupplier, new ExecutionListener[0]);
    }
    public ControlledExecutor(String label, int threads, Integer duration, Integer rpm, Long maxExecution, int warmCount, Supplier<Callable<R>> taskSupplier, ExecutionListener<BenchmarksMain.OperationKey, R>... executionListeners) {
        this.label = label;
        this.duration = duration;
        this.maxExecution = maxExecution;
        this.warmCount = warmCount;
        this.stats = new SynchronizedDescriptiveStatistics();
        this.taskSupplier = taskSupplier;
        this.executionListeners = executionListeners;
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

    public void run() throws InterruptedException, ExecutionException {
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
        List<Future> futures = new ArrayList<>();
        try {
            StopReason stopReason;
            while ((stopReason = shouldStop(submissionCount.get())) == null) {
                if (rateLimiter != null) {
                    rateLimiter.waitIfRequired();
                }
                while (backPressureLimiter.waitIfRequired(submissionCount.get(), executionCount, 1000) && (stopReason = shouldStop(submissionCount.get())) == null) {
                    //keep blocking until either back pressure no longer an issue or the executor is stopped
                    //this could block for quite a while hence should check stop reason
                }
                if (stopReason != null) {
                    break;
                }

                Callable<R> task = taskSupplier.get();
                if (task == null) { //no more runners available
                    log("Exhausted task supplier.");
                    break;
                }
                futures.add(executor.submit(() -> {
                    if (dropTaskFlag.get()) { //do not process the rest of this
                        return null;
                    }
                    long start = System.nanoTime();
                    try {
                        R result = task.call();
                        if (executionCount.incrementAndGet() > warmCount) {
                            long durationInNanoSec = (System.nanoTime() - start);
                            stats.addValue(durationInNanoSec  / 1000_000.0);
                            if (executionListeners.length > 0) {
                                BenchmarksMain.OperationKey key = null;
                                if (task instanceof CallableWithType) {
                                    key = ((CallableWithType<R>) task).getType();
                                }
                                for (ExecutionListener<BenchmarksMain.OperationKey, R> executionListener : executionListeners) {
                                    executionListener.onExecutionComplete(key, result, durationInNanoSec);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to execute task. Message: " + e.getMessage());
                    }
                    return null;
                }));
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

            for (Future future : futures) { //check for exceptions
                try {
                    future.get();
                } catch (InterruptedException | CancellationException e) {
                    //ok
                }
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

        /**
         *
         * @param submissionCount
         * @param executionCount
         * @param timeout
         * @return true  if back pressure still exists and this exits because of timeout. false if back pressure no longer should block
         * @throws InterruptedException
         */
        public boolean waitIfRequired(long submissionCount, AtomicLong executionCount, long timeout) throws InterruptedException {
            long endTime = System.currentTimeMillis() + timeout;
            while ((submissionCount - executionCount.get()) >= maxPendingTasks) {
                if (System.currentTimeMillis() >= endTime) {
                    return true;
                }
                TimeUnit.MILLISECONDS.sleep(100);
            }
            return false;
        }
    }
}
