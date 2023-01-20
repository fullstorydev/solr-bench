package org.apache.solr.benchmarks.exporter;

import org.apache.solr.benchmarks.WorkflowResult;

import java.io.IOException;

public interface ResultExporter {
  void export(WorkflowResult result) throws IOException;
}


