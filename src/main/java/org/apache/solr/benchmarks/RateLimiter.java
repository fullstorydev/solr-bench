package org.apache.solr.benchmarks;


/**
 * The rpm requests per minute. It is enforced uniformly
 * <p>
 * for instance if rpm is 120. The system tries to limit the request to 2/sec
 * <p>
 * <p>
 * if the rpm is 10 the system will try have keep at least 1 req eveery 6secs
 */
public class RateLimiter {

    final int rpm;
    final int intervalMillis;
    Object lock = new Object();
    volatile boolean isEnd = false;

    private volatile long lastRequestSentAt = 0;

    public RateLimiter(int rpm) {
        this.rpm = rpm;
        intervalMillis = 60 * 1000 / rpm;
    }


    public synchronized void waitIfRequired() {
        if(isEnd) return;
        long currTime = System.currentTimeMillis();
        long timeElapsed = currTime - lastRequestSentAt;
        if (timeElapsed > intervalMillis) {
            lastRequestSentAt = currTime;
            return;
        }

        try {
            long timeoutMillis = intervalMillis - timeElapsed;
            Thread.sleep(timeoutMillis);
        } catch (InterruptedException e) {
            //
        } finally {
            lastRequestSentAt = System.currentTimeMillis();
        }

    }



}
