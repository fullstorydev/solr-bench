package org.apache.solr.benchmarks.prometheus;

import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.beans.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

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
  private static String globalTypeLabel;

  /**
   * Starts this manager. This should be invoked once before any other operations on the manager except
   * <code>isEnabled</code>
   * @param workflow
   * @throws IOException
   */
  public synchronized static void startServer(Workflow workflow) throws IOException {
    if (SERVER == null) {
      SERVER = new PrometheusExportServer(workflow);
      globalTypeLabel = workflow.prometheusExport.typeLabel;
    }
  }

  private static void checkEnabled() {
    if (!isEnabled()) {
      throw new IllegalStateException("Server is not yet started. Call startServer first");
    }
  }

  /**
   * Indicates whether this manager has been started
   * @return
   */
  public synchronized static boolean isEnabled() {
    return SERVER != null;
  }

  /**
   * Records a duration metrics on the operation defined by the key and typeLabel
   * @param key       the key of the operation, for example a GET operation on path /select
   * @param typeLabel custom value to define the label `type`
   * @param durationInMillisecond duration of the operation measured in millisecond
   */
  public static void markDuration(BenchmarksMain.OperationKey key, String typeLabel, long durationInMillisecond) {
    checkEnabled();
    if (typeLabel == null) {
      typeLabel = globalTypeLabel;
    }
    String[] labels = new String[] { key.getHttpMethod(), key.getPath(), typeLabel };

    SERVER.histogram.labels(labels).observe(durationInMillisecond);
  }
}
