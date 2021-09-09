package org.apache.solr.benchmarks;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ReqIdUtil {
    public static String REQ_ID_KEY = "rid";

    private ReqIdUtil() {

    }

    private static final AtomicLong counter = new AtomicLong();

    public static String generateTraceIdString() {
        return UUID.randomUUID().toString();
    }

    public static <T> void injectReqId(T carrier, ReqIdInjector<T> injector) {
        injector.inject(carrier, REQ_ID_KEY, generateTraceIdString());
    }
}

interface ReqIdInjector<T> {
    void inject(T carrier, String key, String value);
}
