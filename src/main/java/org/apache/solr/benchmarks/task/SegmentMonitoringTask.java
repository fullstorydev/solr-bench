package org.apache.solr.benchmarks.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.beans.TaskByClass;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

import java.io.IOException;
import java.util.*;

public class SegmentMonitoringTask extends AbstractTask<Object> {
  private final String collection;
  private final HttpSolrClient solrClient;

  public SegmentMonitoringTask(TaskByClass taskSpec, SolrCloud solrCloud) {
    super(taskSpec);
    String collection = (String)taskSpec.params.get("collection");
    if (collection == null) {
      throw new IllegalArgumentException("Should define collection for " + SegmentMonitoringTask.class.getSimpleName());
    }
    this.collection = collection;
    solrClient = new HttpSolrClient.Builder(solrCloud.nodes.get(0).getBaseUrl()).build();
  }

  @Override
  public Object runAction() throws Exception {
    List<String> cores = getCores();

    for (String core : cores) {
      String segmentUrl = solrClient.getBaseURL() + "/" + core + "/admin/segments";
      String response = solrClient.getHttpClient().execute(new HttpGet(segmentUrl), new BasicResponseHandler());
      System.out.println(response);
    }

    return null;
  }

  private List<String> getCores() throws IOException {
    List<String> cores = new ArrayList<>(); //find cores of this collection
    String coresUrl = solrClient.getBaseURL() + "/admin/cores"; //no call to get single collection only...
    String response = solrClient.getHttpClient().execute(new HttpGet(coresUrl), new BasicResponseHandler());

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(response);

// get "status" node
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
  public ControlledExecutor.ExecutionListener<BenchmarksMain.OperationKey, Object>[] getExecutionListeners() {
    return new ControlledExecutor.ExecutionListener[0];
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
}
