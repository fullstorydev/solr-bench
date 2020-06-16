package org.apache.solr.benchmarks.beans;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration {
  @JsonProperty("index-benchmarks")
  public List<IndexBenchmark> indexBenchmarks;

  @JsonProperty("query-benchmarks")
  public List<QueryBenchmark> queryBenchmarks;
  
  @JsonProperty("cluster")
  public Cluster cluster;

  @JsonProperty("repository")
  public Repository repo;

  @JsonProperty("metrics")
  public List<String> metrics;

  @JsonProperty("solr-package")
  public String solrPackage;

  @JsonProperty("pre-download")
  public List<String> preDownloadResources;
  
  @JsonProperty("results-upload-location")
  public String resultsUploadLocation;
}
