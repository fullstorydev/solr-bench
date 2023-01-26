package org.apache.solr.benchmarks;


import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ControlledExecutor {
    private final int threads;
    private final Supplier<Runnable> runnerSupplier;
    private final LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor;
    private final Integer duration;
    private final Long totalCount;
    private final int warmCount;
    private Long endTime;
    final SynchronizedDescriptiveStatistics stats;
    private RateLimiter rateLimiter;
    AtomicLong count = new AtomicLong(0);

    public ControlledExecutor(int threads, Integer duration, Integer rpm, Long totalCount, int warmCount, Supplier<Runnable> runnerSupplier) {
        this.threads = threads;
        this.duration = duration;
        this.totalCount = totalCount;
        this.warmCount = warmCount;
        this.stats = new SynchronizedDescriptiveStatistics();
        this.runnerSupplier = runnerSupplier;
        executor = new ThreadPoolExecutor(threads, threads,
                0L, TimeUnit.MILLISECONDS,
                workQueue);
        if (rpm != null) rateLimiter = new RateLimiter(rpm);
    }

    public void run() throws InterruptedException {
        long initTime = System.currentTimeMillis();

        if (duration != null) {
            endTime = initTime + (1000 * duration);
        }

        int maxItemsWaiting = 10 * threads;
        try {
            for (; ; ) {
                if (isEnd(initTime)) break;
                for (; ; ) {
                    if (workQueue.size() < maxItemsWaiting)
                        break;// keep a max '10* threads' no:of tasks in queue
                    Thread.sleep(5);//There are a lot of tasks waiting.  let's wait before pumping in more tasks (avoid OOM)
                }
                Runnable r;
                try {
                	r = runnerSupplier.get();
                } catch (Exception ex) {
                	ex.printStackTrace();
                	continue;
                }
                if (r == null) {
                    break;
                }

                executor.submit(() -> {
                	if (rateLimiter != null) {
                        if (isEnd(initTime)) return;
                        rateLimiter.waitIfRequired();
                    }
                	
                    if (isEnd(initTime)) return;

                    long start = System.nanoTime();
                    r.run();
                    if (count.get() >= warmCount) {
                    	stats.addValue((System.nanoTime() - start) / 1000_000.0);
                    }

                    
                	  long currentCount = count.incrementAndGet();
                    if (totalCount != null) {
                        printProgress(currentCount, totalCount);
                    }
                });
            }
        } finally {
            long currTime = System.currentTimeMillis();
            System.out.println("exiting,time over at " + currTime + " time elapsed : " + (currTime - initTime)  + "total tasks : "+ count +", benchmarked queries: "+stats.getN()) ;
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        }

    }

    private void printProgress(long currentCount, long totalCount) {
        long chunkSize = totalCount / 100;
        if (currentCount % chunkSize == 0) {
            long percentage = currentCount / chunkSize;
            if (percentage % 10 == 0) {
                System.out.println(percentage + "%"); //using println as some logs (k8s for example) only updates on new line
            } else {
                System.out.print(".");
            }
        } else if (currentCount == totalCount) {
            System.out.print("100%");
        }
    }


    private synchronized boolean isEnd(long initTime) {
    	if (totalCount != null && totalCount > 0 && count != null && count.get() >= totalCount) {
   			return true;
    	}
        if (endTime != null) {
            long l = System.currentTimeMillis();
            if (l > endTime) {
                if(rateLimiter != null) rateLimiter.isEnd = true;
                return true;
            }
        }
        return false;
    }
}
