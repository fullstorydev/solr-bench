package org.apache.solr.benchmarks.readers;

public interface Sink<T> {

    boolean item(T doc);
}

