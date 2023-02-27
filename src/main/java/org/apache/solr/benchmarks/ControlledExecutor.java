package org.apache.solr.benchmarks;


import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ControlledExecutor<T> {
    private final int threads;
    private final Supplier<Callable<T>> callableSupplier;
    private final LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor;
    private final Integer duration;
    private final Long totalCount;
    private final int warmCount;
    private Long endTime;
    final SynchronizedDescriptiveStatistics stats;
    private RateLimiter rateLimiter;
    AtomicLong count = new AtomicLong(0);

    public ControlledExecutor(int threads, Integer duration, Integer rpm, Long totalCount, int warmCount, Supplier<Callable<T>> callableSupplier) {
        this.threads = threads;
        this.duration = duration;
        this.totalCount = totalCount;
        this.warmCount = warmCount;
        this.stats = new SynchronizedDescriptiveStatistics();
        this.callableSupplier = callableSupplier;
        executor = new ThreadPoolExecutor(threads, threads,
                0L, TimeUnit.MILLISECONDS,
                workQueue);
        if (rpm != null) rateLimiter = new RateLimiter(rpm);
    }

    public List<T> run() throws InterruptedException {
        long initTime = System.currentTimeMillis();

        if (duration != null) {
            endTime = initTime + (1000 * duration);
        }

        List<T> results = Collections.synchronizedList(new ArrayList<>());
        int maxItemsWaiting = 10 * threads;
        try {
            for (; ; ) {
                if (isEnd(initTime)) break;
                for (; ; ) {
                    if (workQueue.size() < maxItemsWaiting)
                        break;// keep a max '10* threads' no:of tasks in queue
                    Thread.sleep(5);//There are a lot of tasks waiting.  let's wait before pumping in more tasks (avoid OOM)
                }
                Callable<T> c;
                try {
                	c = callableSupplier.get();
                } catch (Exception ex) {
                	ex.printStackTrace();
                	continue;
                }
                if (c == null) {
                    break;
                }

                executor.submit(() -> {
                	if (rateLimiter != null) {
                        if (isEnd(initTime)) return null;
                        rateLimiter.waitIfRequired();
                    }
                	
                    if (isEnd(initTime)) return null;

                    long start = System.nanoTime();
                    T result = c.call();
                    results.add(result); //slightly easier than having to collect all results at the end...
                    if (count.get() >= warmCount) {
                    	stats.addValue((System.nanoTime() - start) / 1000_000.0);
                    }

                    
                	  long currentCount = count.incrementAndGet();
                    if (totalCount != null) {
                        printCountProgress(currentCount, totalCount, initTime);
                    }
                    return result;
                });
            }
            return results;
        } finally {
            long currTime = System.currentTimeMillis();
            long rpm = count.get() * 1000 * 60 / (currTime - initTime);
            System.out.println("exiting,time over at " + currTime + " time elapsed : " + (currTime - initTime)  + " total execution count : "+ count + " rpm : " + rpm + " benchmarked queries: "+stats.getN()) ;
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        }

    }

    private void printCountProgress(long currentCount, long totalCount, long startTime) {
        long chunkSize = totalCount / 100;
        if (currentCount % chunkSize == 0) {
            long percentage = currentCount / chunkSize;
            if (percentage % 10 == 0) {
                System.out.print(percentage + "%"); //using println as some logs (k8s for example) only updates on new line
                printRpm(currentCount, startTime);
                System.out.println();
            } else {
                System.out.print(".");
            }
        } else if (currentCount == totalCount) {
            System.out.println("100%");
            System.out.println();
        }
    }


    private void printRpm(long currentCount, long startTime) {
        long timeElapsed = System.currentTimeMillis() - startTime;
        long currentRpm = currentCount * 1000 * 60 / timeElapsed;
        System.out.print(" current rpm: " + currentRpm);
        if (rateLimiter != null) {
           System.out.print(" target rpm: " + rateLimiter.rpm);
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
