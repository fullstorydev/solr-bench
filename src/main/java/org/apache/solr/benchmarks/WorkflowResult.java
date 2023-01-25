package org.apache.solr.benchmarks;

import java.util.List;
import java.util.Map;
import java.util.Vector;

public class WorkflowResult {
  private final Map<String, List<Map>> benchmarkResults;
  private final Map<String, Map<String, Vector<Number>>> metrics;

  public WorkflowResult(Map<String, List<Map>> benchmarkResults, Map<String, Map<String, Vector<Number>>> metrics) {
    this.benchmarkResults = benchmarkResults;
    this.metrics = metrics;
  }

  public Map<String, List<Map>> getBenchmarkResults() {
    return benchmarkResults;
  }

  public Map<String, Map<String, Vector<Number>>> getMetrics() {
    return metrics;
  }
}
