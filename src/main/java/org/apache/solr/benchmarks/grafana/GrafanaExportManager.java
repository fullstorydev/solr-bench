package org.apache.solr.benchmarks.grafana;

import org.apache.solr.benchmarks.beans.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

public class GrafanaExportManager {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static volatile GrafanaExportServer SERVER = null; //singleton for now

  public synchronized static void startServer(Workflow workflow) throws IOException {
    if (SERVER == null) {
      SERVER = new GrafanaExportServer(workflow);
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

  public static void markQueryDuration(String query, long durationInNanosecond) {
    checkEnabled();
//    SERVER.querySummary.labels(query).observe(durationInNanosecond);
    SERVER.querySummary.observe(durationInNanosecond); //less not mark query for now, it could be very high cardinalityß
  }




}
