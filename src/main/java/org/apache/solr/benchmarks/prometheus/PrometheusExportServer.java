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

/**
 * The wrapper of the underlying Prometheus HTTP server that exposes /metrics endpoint
 *
 * <p>This also registers a pre-determined set of histogram to the Prometheus default registry
 */
class PrometheusExportServer {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  Histogram histogram;

  PrometheusExportServer(Workflow workflow) throws IOException {
    log.info("Starting Prometheus exporter and metrics will be available at 127.0.0.1:{}/metrics", workflow.prometheusExport.port);
    new HTTPServer(workflow.prometheusExport.port, true);
  }
}
