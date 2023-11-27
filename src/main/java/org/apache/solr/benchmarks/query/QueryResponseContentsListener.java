package org.apache.solr.benchmarks.query;

import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;

public interface QueryResponseContentsListener extends ControlledExecutor.ExecutionListener<BenchmarksMain.OperationKey, QueryResponseContents> {
}
