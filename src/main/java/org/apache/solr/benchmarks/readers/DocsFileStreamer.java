package org.apache.solr.benchmarks.readers;

import java.io.File;
import java.io.IOException;

import org.apache.solr.common.SolrInputDocument;

public interface DocsFileStreamer {
    void stream(File datasetFile, Sink<SolrInputDocument> sink) throws IOException;
}
