package org.apache.solr.benchmarks.exporter;

import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import org.apache.solr.benchmarks.beans.TaskType;
import org.apache.solr.benchmarks.beans.Workflow;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class GrafanaExportManager {
  private static boolean isEnabled = false;
  private static Summary querySummary;

  public static void startServer(Workflow workflow) throws IOException {
    if (initMetrics(workflow)) {
      new HTTPServer(workflow.prometheusExportPort, true);
      isEnabled = true;
    }
  }

  /**
   * True if any metrics are created, hence server should be started
   * @param workflow
   * @return
   */
  private static boolean initMetrics(Workflow workflow) {
    if (workflow.prometheusExportPort == null) {
      return false;
    }

    boolean shouldStart = false;
    Set<TaskType> taskTypes = workflow.executionPlan.values().stream().map(instance -> workflow.taskTypes.get(instance.type)).collect(Collectors.toSet());

    //only export grafana metrics on query and index benchmarks
    if (taskTypes.stream().anyMatch(t -> t.queryBenchmark != null)) {
      querySummary = registerSummary("query_duration", "duration taken to execute a Solr query", "query");
      shouldStart = true;
    }
    if (taskTypes.stream().anyMatch(t -> t.indexBenchmark != null)) {
      shouldStart = true;
    }
    return shouldStart;
  }

  public static boolean isIsEnabled() {
    return isEnabled;
  }

  public static void markQueryDuration(String query, long durationInNanosecond) {
    querySummary.labels(query).observe(durationInNanosecond);
  }

  private static Summary registerSummary(String name, String help, String... labelNames) {

    return Summary.build(name, help).labelNames(labelNames).register();
  }

}
