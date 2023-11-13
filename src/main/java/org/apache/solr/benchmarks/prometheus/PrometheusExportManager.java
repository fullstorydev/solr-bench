package org.apache.solr.benchmarks.prometheus;

import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.beans.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

public class PrometheusExportManager {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static volatile PrometheusExportServer SERVER = null; //singleton for now
  private static String globalTypeLabel;
  public synchronized static void startServer(Workflow workflow) throws IOException {
    if (SERVER == null) {
      SERVER = new PrometheusExportServer(workflow);
      globalTypeLabel = workflow.prometheusExport.typeLabel;
    }
  }

  public static void checkEnabled() {
    if (!isEnabled()) {
      throw new IllegalStateException("Server is not yet started. Call startServer first");
    }
  }

  public synchronized static boolean isEnabled() {
    return SERVER != null;
  }

  public static void markDuration(BenchmarksMain.OperationKey key, String typeLabel, long durationInMillisecond) {
    checkEnabled();
    if (typeLabel == null) {
      typeLabel = globalTypeLabel;
    }
    String[] labels = new String[] { key.getHttpMethod(), key.getPath(), typeLabel };

    SERVER.histogram.labels(labels).observe(durationInMillisecond);
  }




}
