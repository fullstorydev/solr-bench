package org.apache.solr.benchmarks.indexing;

public class IndexResult {
    private long bytesWritten;
    private long docsUploaded;

    IndexResult(long bytesWritten, long docsUploaded) {
        this.bytesWritten = bytesWritten;
        this.docsUploaded = docsUploaded;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public long getDocsUploaded() {
        return docsUploaded;
    }
}
