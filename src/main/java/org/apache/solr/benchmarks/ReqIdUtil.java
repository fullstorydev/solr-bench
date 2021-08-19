package org.apache.solr.benchmarks;

import java.util.concurrent.atomic.AtomicLong;

public class ReqIdUtil {
    public static String REQ_ID_KEY = "_req_id";

    private ReqIdUtil() {

    }

    private static final AtomicLong counter = new AtomicLong();

    private static int ID_LENGTH = 16;
    private static String ZERO_STRING = getZeroString(ID_LENGTH);
    private static final String ID_SEPARATOR = ":";

    private static String getZeroString(int idLength) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < idLength; i ++) {
            builder.append('0');
        }
        return builder.toString();
    }

    public static String generateTraceIdString() {
        final String hex = Long.toHexString(counter.incrementAndGet());
        if (hex.length() >= ID_LENGTH) {
            return hex.substring(0, ID_LENGTH);
        }
        return ZERO_STRING.substring(hex.length()) + hex;
    }

    public static <T> void injectReqId(T carrier, ReqIdInjector<T> injector) {
        injector.inject(carrier, REQ_ID_KEY, generateTraceIdString());
    }
}

interface ReqIdInjector<T> {
    void inject(T carrier, String key, String value);
}
