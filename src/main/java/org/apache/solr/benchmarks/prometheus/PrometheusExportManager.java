package org.apache.solr.benchmarks.prometheus;

import io.prometheus.client.*;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.beans.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A manager to control benchmark result exporting via Prometheus /metrics endpoint.
 *
 * <p>It keeps and manages a singleton of a {@link PrometheusExportServer}
 *
 * <p>Code logic should interact with this class instead of the {@link PrometheusExportServer} directly
 */
public class PrometheusExportManager {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static volatile PrometheusExportServer SERVER = null; //singleton for now
  private static final ConcurrentMap<String, Histogram> registeredHistograms = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Gauge> registeredGauges = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Counter> registeredCounters = new ConcurrentHashMap<>();
  public static String globalTypeLabel;

  /**
   * Starts this manager. This should be invoked once before any other operations on the manager except
   * <code>isEnabled</code>
   *
   * @param workflow
   * @throws IOException
   */
  public synchronized static void startServer(Workflow workflow) throws IOException {
    if (SERVER == null) {
      SERVER = new PrometheusExportServer(workflow);
      globalTypeLabel = workflow.prometheusExport.typeLabel;
    }
  }

  public static Histogram registerHistogram(String name, String help, String...labels) {
    return registeredHistograms.computeIfAbsent(name, n -> Histogram.build(name, help).labelNames(labels).exponentialBuckets(1, 2, 30).register());
  }

  public static Gauge registerGauge(String name, String help, String...labels) {
    return registeredGauges.computeIfAbsent(name, n -> Gauge.build(name, help).labelNames(labels).register());
  }

  public static Counter registerCounter(String name, String help, String...labels) {
    return registeredCounters.computeIfAbsent(name, n -> Counter.build(name, help).labelNames(labels).register());
  }

  /**
   * Indicates whether this manager has been started
   * @return
   */
  public synchronized static boolean isEnabled() {
    return SERVER != null;
  }
}
