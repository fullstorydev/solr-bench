package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class IndexBenchmark {
  @JsonProperty("name")
  public String name;

  @JsonProperty("description")
  public String description;

  @JsonProperty("replication-type")
  public String replicationType;

  @JsonProperty("dataset-file")
  public String datasetFile;

  @JsonProperty("file-format")
  public String fileFormat;

  @JsonProperty("setups")
  public List<Setup> setups;

  @JsonProperty("duration-secs")
  public Integer durationSecs;

  @JsonProperty("run-count")
  public Integer runCount;

  @JsonProperty("max-docs")
  public Integer maxDocs = Integer.MAX_VALUE;

  @JsonProperty ("rpm")
  public Integer rpm;

  @JsonProperty("batch-size")
  public Integer batchSize = 1000;
  
  @JsonProperty("id-field")
  public String idField = "id";

  @JsonProperty("req-trace")//whether inject _req_id trace header or not
  public Boolean reqTrace = Boolean.FALSE;

  static public class Setup {
    @JsonProperty("setup-name")
    public String name;

    @JsonProperty("collection")
    public String collection;

    @JsonProperty("configset")
    public String configset;

    @JsonProperty("replication-factor")
    public int replicationFactor;

    @JsonProperty("shards")
    public int shards;

    @JsonProperty("min-threads")
    public int minThreads;

    @JsonProperty("max-threads")
    public int maxThreads;
    
    @JsonProperty("thread-step")
    public int threadStep;



  }
}

