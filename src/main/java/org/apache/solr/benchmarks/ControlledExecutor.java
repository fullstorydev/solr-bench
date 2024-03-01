package org.apache.solr.benchmarks;


import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A custom executor that executes "actions" provided by a "Task". It blocks on `run` until one of
 * the below conditions fulfills:
 * <ol>
 *  <li>If duration is defined, such duration is reached since run is invoked</li>
 *  <li>If actionSupplier no longer provides any more actions</li>
 *  <li>If execution count has reached maxExecution if provided</li>
 *  <li>If there is any uncaught exception</li>
 * </ol>
 *
 * For actions that are submitted to the underlying executor but not yet executed, they will be
 * cancelled iff the `StopReason` is `Duration` or `Exception`.
 * <p>
 * Actions will be submitted adhering to the rpm if provided.
 */
public class ControlledExecutor<R> {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Supplier<Callable<R>> actionSupplier;
    private final ExecutorService executor;
    private final ExecutionListener<BenchmarksMain.OperationKey, R>[] executionListeners;
    private final int maxPendingActions;
    private final String taskName;
    private final Integer rpm; //action run per min, if defined, the executor would slow down action submission if the rate is higher than this
    private final Integer duration; //if defined, the executor should cease action execution/submission once this duration is reached
    private final Long maxExecution; //max execution count, if defined, executor should only execute actions up to this number
    private final int warmCount; //executions before this would not be tracked in stats

    final SynchronizedDescriptiveStatistics stats;

    public interface ExecutionListener<T, R> {
        /**
         * Gets invoked when a callable finishes execution by this executor. Take note that execution of warmups will
         * not trigger this. And this might get invoked by multiple threads concurrently
         * <p>
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

    public ControlledExecutor(String label, int threads, Integer duration, Integer rpm, Long maxExecution, int warmCount, Supplier<Callable<R>> actionSupplier) {
        this(label, threads, duration, rpm, maxExecution, warmCount, actionSupplier, new ExecutionListener[0]);
    }
    public ControlledExecutor(String taskName, int threads, Integer duration, Integer rpm, Long maxExecution, int warmCount, Supplier<Callable<R>> actionSupplier, ExecutionListener<BenchmarksMain.OperationKey, R>... executionListeners) {
        this.taskName = taskName;
        this.duration = duration;
        this.maxExecution = maxExecution;
        this.warmCount = warmCount;
        this.stats = new SynchronizedDescriptiveStatistics();
        this.actionSupplier = actionSupplier;
        this.executionListeners = executionListeners;
        this.maxPendingActions = threads * 10; //at most 10 * # of thread pending actions
        this.rpm = rpm;
        executor = new ThreadPoolExecutor(threads, threads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> new Thread(r, ControlledExecutor.class.getSimpleName() + "-" + taskName));
    }

    public void run() throws InterruptedException, ExecutionException {
        long startTime = System.currentTimeMillis();
        Long endTime = duration != null ?  startTime + (1000 * duration) : null;

        List<Future<Object>> actionFutures = new ArrayList<>();

        ExecutorService monitoringExecutorService = Executors.newCachedThreadPool(); //to monitor the action future
        StopReason stopReason;

        StatusChecker checker = new StatusChecker(rpm, endTime, maxPendingActions);
        try {
            while ((stopReason = checker.shouldStop()) == null) {
                Callable<R> action = actionSupplier.get();
                if (action == null) { //no more action available
                    log("Exhausted action supplier.");
                    stopReason = StopReason.ACTION_SUPPLIER_EXHAUSTED;
                    break;
                }
                Future<Object> actionFuture = executor.submit(() -> {
                    long start = System.nanoTime();
                    try {
                        R result = action.call();
                        if (checker.incrementAndGetExecutionCount() > warmCount) {
                            long durationInNanoSec = (System.nanoTime() - start);
                            stats.addValue(durationInNanoSec  / 1000_000.0);
                            if (executionListeners.length > 0) {
                                BenchmarksMain.OperationKey key = null;
                                if (action instanceof CallableWithType) {
                                    key = ((CallableWithType<R>) action).getType();
                                }
                                for (ExecutionListener<BenchmarksMain.OperationKey, R> executionListener : executionListeners) {
                                    executionListener.onExecutionComplete(key, result, durationInNanoSec);
                                }
                            }
                        }
                    } catch (Exception e) {
                        warn("Failed to execute action. Message: " + e.getMessage());
                        throw e;
                    }
                    return null;
                });
                actionFutures.add(actionFuture);
                //monitor this action future in the background
                monitoringExecutorService.submit(() -> {
                    try {
                        return actionFuture.get();
                    } catch (ExecutionException e) {
                        checker.flagException(e);
                        return null;
                    }
                });

                checker.incrementAndGetSubmissionCount();
            }

            if (stopReason == StopReason.DURATION || stopReason == StopReason.EXCEPTION) {
                if (stopReason == StopReason.EXCEPTION) {
                    warn("Interrupting all actions in executor of task " + taskName + " due to exception");
                } else {
                    log("Interrupting all actions in executor of task " + taskName + " as it reaches the duration " + duration);
                }
                actionFutures.forEach(f -> f.cancel(true));
            }
        } finally {
            checker.close();
            monitoringExecutorService.shutdown();
            executor.shutdown();

            log("Now waiting for all executing/submitted actions to finish execution.");
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        }

        ExecutionException exception = checker.getException();
        if (exception != null) {
            throw exception; //re-throw the exception so the caller can handle it
        }
        long currTime = System.currentTimeMillis();
        long rpm = (currTime - startTime) > 0 ? checker.getExecutionCount() * 1000 * 60 / (currTime - startTime) : checker.getExecutionCount() * 1000 * 60;
        System.out.println("Time elapsed : " + (currTime - startTime)  + " total execution count : "+ checker.getExecutionCount() + " rpm : " + rpm + " benchmarked executions: "+stats.getN()) ;
    }

    /**
     * 1. Periodically prints status of the submitted/executed actions
     * 2. Keeps status of the actions and determines whether we should stop the whole controller
     * 3. Pauses action submission if action rate is too high or the execution cannot catch up (maintain rpm and back pressure)
     */
    private class StatusChecker implements Closeable {
        private final AtomicLong submissionCount = new AtomicLong();
        private final AtomicLong executionCount = new AtomicLong();
        private final Timer progressTimer = new Timer();
        AtomicReference<ExecutionException> exceptionRef = new AtomicReference<>(null);
        private final Integer rpm;
        private final Long endTime;
        private final long startTime;
        private final int maxPendingActions;

        private StatusChecker(Integer rpm, Long endTime, int maxPendingActions) {
            this.rpm = rpm;
            this.endTime = endTime;
            this.startTime = System.currentTimeMillis();
            this.maxPendingActions = maxPendingActions;
            progressTimer.schedule( new TimerTask() {
                public void run() {
                    long currentSubmissionCount = submissionCount.get();
                    long currentExecutionCount = executionCount.get();
                    StringBuilder logMessage = new StringBuilder("Action Submitted: " + currentSubmissionCount + ", Executed: " + currentExecutionCount);
                    long timeElapsed = System.currentTimeMillis() - startTime;
                    if (timeElapsed > 0) {
                        long currentRpmExecuted = currentExecutionCount * 1000 * 60 / timeElapsed;
                        long currentRpmSubmitted = currentSubmissionCount * 1000 * 60 / timeElapsed;
                        if (rpm == null) {
                            logMessage.append(", RPM(executed/submitted): " + currentRpmExecuted + "/" + currentRpmSubmitted);
                        } else {
                            logMessage.append(", RPM(executed/submitted/target): " + currentRpmExecuted + "/" + currentRpmSubmitted + "/" + rpm);
                        }
                    }
                    log(logMessage.toString());
                }
            }, 0, 10*1000);
        }

        /**
         *
         * @return  whether we should stop the current executor. This might block to handle RPM and back-pressure
         * @throws InterruptedException
         */
        private synchronized StopReason shouldStop() throws InterruptedException {
            if (exceptionRef.get() != null) {
                log("Encountered execution exception " + exceptionRef.get().getCause() + "... Stopping the executor");
                return StopReason.EXCEPTION;
            }
            if (maxExecution != null && submissionCount.get() >= maxExecution) {
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
            waitIfRequired();
            return null;
        }

        private void waitIfRequired() throws InterruptedException {
            //check if we are too fast according to rpm
            if (rpm != null) {
                long currentTime = System.currentTimeMillis();
                //what the current time supposed to be if we go with the target rate
                //use submission count here as long-running action might not be "executed" for quite a while
                //we don't want to base wait estimation on actual execution
                long targetTime = startTime + submissionCount.get() * 1000 * 60 / rpm;

                if (targetTime > currentTime) { //then we are too fast, sleep for a while
                    TimeUnit.MILLISECONDS.sleep(targetTime - currentTime);
                }
            }

            //check for back pressure, if we have too many pending actions, then do not submit more
            while ((submissionCount.get() - executionCount.get()) >= maxPendingActions) {
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }

        private void flagException(ExecutionException exception) {
            exceptionRef.set(exception);
        }

        public ExecutionException getException() {
            return exceptionRef.get();
        }

        private long incrementAndGetSubmissionCount() {
            return submissionCount.incrementAndGet();
        }
        private long incrementAndGetExecutionCount() {
            return executionCount.incrementAndGet();
        }
        private long getExecutionCount() {
            return executionCount.get();
        }

        @Override
        public void close() {
            progressTimer.cancel();
        }
    }
    enum StopReason {
        DURATION, EXCEPTION, MAX_EXECUTION, ACTION_SUPPLIER_EXHAUSTED
    }

    private void log(String message) {
        log.info("(" + taskName + ") " +  message);
    }

    private void warn(String message) {
        log.warn("(" + taskName + ") " +  message);
    }
}
