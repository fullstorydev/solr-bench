package org.apache.solr.benchmarks;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The rpm requests per minute. It is enforced uniformly
 * <p>
 * for instance if rpm is 120. The system tries to limit the request to 2/sec
 * <p>
 * <p>
 * if the rpm is 10 the system will try have keep at least 1 req every 6secs
 */
public class RateLimiter {
	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    final int targetRpm;
    private volatile long startTime = -1;
    private long executionCount = 0;

    public RateLimiter(int rpm) {
        this.targetRpm = rpm;
    }

    public synchronized void waitIfRequired() throws InterruptedException {
        executionCount ++;
        if (startTime == -1) { //first execution
            startTime = System.currentTimeMillis();
            return;
        }

        long currentTime = System.currentTimeMillis();
        long targetTime = startTime + executionCount * 1000 * 60 / targetRpm ; //what the current time supposed to be if we go with the target rate

        if (targetTime > currentTime) { //then we are too fast
            TimeUnit.MILLISECONDS.sleep(targetTime - currentTime);
        }
    }
}
