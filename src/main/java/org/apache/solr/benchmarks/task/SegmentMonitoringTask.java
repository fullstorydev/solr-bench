package org.apache.solr.benchmarks.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.solr.benchmarks.BenchmarkContext;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.beans.TaskByClass;
import org.apache.solr.benchmarks.prometheus.PrometheusExportManager;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;

/**
 * A task to report segment info of a given collection via prometheus listener/exporter
 */
public class SegmentMonitoringTask extends AbstractTask<List<SegmentMonitoringTask.SegmentInfo>> {
  private final String collection;
  private final HttpSolrClient solrClient;
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final ControlledExecutor.ExecutionListener<BenchmarksMain.OperationKey, List<SegmentInfo>>[] executionListeners;

  public SegmentMonitoringTask(TaskByClass taskSpec, SolrCloud solrCloud) {
    super(taskSpec);
    String collection = (String)taskSpec.params.get("collection");
    if (collection == null) {
      throw new IllegalArgumentException("Should define collection for " + SegmentMonitoringTask.class.getSimpleName());
    }
    this.collection = collection;
    solrClient = new HttpSolrClient.Builder(solrCloud.nodes.get(0).getBaseUrl()).build();

    if (PrometheusExportManager.isEnabled()) {
      log.info("Adding Prometheus listener for Segment Monitoring Task");
      executionListeners = new ControlledExecutor.ExecutionListener[] { new SegmentInfoPrometheusListener(collection)};
    } else {
      executionListeners = new ControlledExecutor.ExecutionListener[0];
    }
  }

  @Override
  public List<SegmentInfo> runAction() throws Exception {
    List<String> cores;
    try {
      cores = getCores();
    } catch (IOException e) {
      log.info("Collection/Cores " + collection + " might not be available yet...");
      return Collections.emptyList();
    }

    List<SegmentInfo> allSegments = new ArrayList<>();
    for (String core : cores) {
      allSegments.addAll(getSegmentInfos(core));
    }

    return allSegments;
  }

  private Collection<SegmentInfo> getSegmentInfos(String core) throws IOException {
    String segmentUrl = solrClient.getBaseURL() + "/" + core + "/admin/segments";
    String response = solrClient.getHttpClient().execute(new HttpGet(segmentUrl), new BasicResponseHandler());

    // Define SegmentsHolder class
    ObjectMapper objectMapper = new ObjectMapper();
    SegmentsHolder segmentsHolder = objectMapper.readValue(response, SegmentsHolder.class);
    return segmentsHolder.segments.values();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SegmentsHolder {
    // Segments as map because of the dynamic nature of the keys
    public Map<String, SegmentInfo> segments;

    public SegmentsHolder() {}
  }
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SegmentInfo {
    public String name;
    public int size;
    public int delCount;
    public long sizeInBytes;
  }

  private List<String> getCores() throws IOException {
    List<String> cores = new ArrayList<>(); //find cores of this collection
    String coresUrl = solrClient.getBaseURL() + "/admin/cores"; //no call to get single collection only...
    String response = solrClient.getHttpClient().execute(new HttpGet(coresUrl), new BasicResponseHandler());

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(response);
    JsonNode statusNode = root.path("status");
    // loop through each core in "status", if collection matches, get the core
    statusNode.fields().forEachRemaining(entry -> {
      JsonNode cloudNode = entry.getValue().get("cloud");
      if(cloudNode != null && collection.equals(cloudNode.get("collection").textValue())) {
        cores.add(entry.getKey());
      }
    });
    return cores;
  }

  @Override
  public ControlledExecutor.ExecutionListener<BenchmarksMain.OperationKey, List<SegmentInfo>>[] getExecutionListeners() {
    return executionListeners;
  }

  @Override
  public Map<String, Object> getAdditionalResult() {
    return null;
  }

  @Override
  public void postTask() throws IOException {
    if (solrClient != null) {
      solrClient.close();
    }
  }

  private static class SegmentInfoPrometheusListener implements ControlledExecutor.ExecutionListener<Object, List<SegmentInfo>> {
    private final Gauge segmentCountGauge;
    private final Gauge segmentDocCountMedianGauge;
    private final Gauge segmentDocCountMaxGauge;
    private final String collection;
    private static final String zkHost = BenchmarkContext.getContext().getZkHost();
    private static final String testSuite = BenchmarkContext.getContext().getTestSuite();

    public SegmentInfoPrometheusListener(String collection) {
      this.segmentCountGauge = PrometheusExportManager.registerGauge("solr_bench_segment_count", "Total segment count per collection", "collection", "zk_host", "test_suite");
      this.segmentDocCountMedianGauge = PrometheusExportManager.registerGauge("solr_bench_segment_doc_count_median", "Medium of segment doc count per collection", "collection", "zk_host", "test_suite");
      this.segmentDocCountMaxGauge = PrometheusExportManager.registerGauge("solr_bench_segment_doc_count_max", "Max of segment doc count per collection", "collection", "zk_host", "test_suite");
      this.collection = collection;
    }


    @Override
    public void onExecutionComplete(Object typeKey, List<SegmentInfo> result, long duration) {
      if (!result.isEmpty()) {
        segmentCountGauge.labels(collection, zkHost, testSuite).set(result.size()); //keep it simple for now
        result.sort(Comparator.comparingInt(o -> o.size));
        segmentDocCountMaxGauge.labels(collection, zkHost, testSuite).set(result.get(result.size() - 1).size);
        segmentDocCountMedianGauge.labels(collection, zkHost, testSuite).set(result.get(result.size() / 2).size);
      }
    }
  }
}
