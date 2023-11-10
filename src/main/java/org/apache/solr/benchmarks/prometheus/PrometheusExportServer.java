package org.apache.solr.benchmarks.prometheus;

import io.prometheus.client.Histogram;
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

public class PrometheusExportServer {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  Histogram queryHistogram;

  PrometheusExportServer(Workflow workflow) throws IOException {
    if (initMetrics(workflow)) {
      log.info("Starting grafana exporter and metrics will be available at 127.0.0.1:{}/metrics", workflow.prometheusExport.port);
      new HTTPServer(workflow.prometheusExport.port, true);
    }
  }
  /**
   * True if any metrics are created, hence server should be started
   * @param workflow
   * @return
   */
  final boolean initMetrics(Workflow workflow) {
    if (workflow.prometheusExport == null) {
      return false;
    }

    boolean shouldStart = false;
    Set<TaskType> taskTypes = workflow.executionPlan.values().stream().map(instance -> workflow.taskTypes.get(instance.type)).collect(Collectors.toSet());

    //only export grafana metrics on query and index benchmarks
    if (taskTypes.stream().anyMatch(t -> t.queryBenchmark != null)) {
      queryHistogram = registerHistogram("solr_bench_query_duration", "duration taken to execute a Solr query");
      shouldStart = true;
    }
    if (taskTypes.stream().anyMatch(t -> t.indexBenchmark != null)) {
      shouldStart = true;
    }
    return shouldStart;
  }

  private static Summary registerSummary(String name, String help) {
    return Summary.build(name, help).labelNames("type").register(); //only support a single "type" label for now
  }

  private static Histogram registerHistogram(String name, String help) {
    return Histogram.build(name, help).labelNames("type").exponentialBuckets(1, 1.5, 30).register(); //only support a single "type" label for now
  }
}
