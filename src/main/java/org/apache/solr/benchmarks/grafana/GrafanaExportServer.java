package org.apache.solr.benchmarks.grafana;

import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import org.apache.solr.benchmarks.beans.TaskType;
import org.apache.solr.benchmarks.beans.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.stream.Collectors;

public class GrafanaExportServer {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  Summary querySummary;

  GrafanaExportServer(Workflow workflow) throws IOException {
    if (initMetrics(workflow)) {
      log.info("Starting grafana exporter and metrics will be available at 127.0.0.1:{}/metrics", workflow.prometheusExportPort);
      new HTTPServer(workflow.prometheusExportPort, true);
    }
  }
  /**
   * True if any metrics are created, hence server should be started
   * @param workflow
   * @return
   */
  final boolean initMetrics(Workflow workflow) {
    if (workflow.prometheusExportPort == null) {
      return false;
    }

    boolean shouldStart = false;
    Set<TaskType> taskTypes = workflow.executionPlan.values().stream().map(instance -> workflow.taskTypes.get(instance.type)).collect(Collectors.toSet());

    //only export grafana metrics on query and index benchmarks
    if (taskTypes.stream().anyMatch(t -> t.queryBenchmark != null)) {
      querySummary = registerSummary("solr_bench_query_duration", "duration taken to execute a Solr query", "query");
      shouldStart = true;
    }
    if (taskTypes.stream().anyMatch(t -> t.indexBenchmark != null)) {
      shouldStart = true;
    }
    return shouldStart;
  }

  private static Summary registerSummary(String name, String help, String... labelNames) {
    return Summary.build(name, help).labelNames(labelNames).register();
  }
}
