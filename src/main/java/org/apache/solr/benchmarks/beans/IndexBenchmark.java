package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class IndexBenchmark extends BaseBenchmark {
  @JsonProperty("replication-type")
  public String replicationType;

  @JsonProperty("dataset-file")
  public String datasetFile;

  @JsonProperty("file-format")
  public String fileFormat;

  @JsonProperty("setups")
  public List<Setup> setups;

  @JsonProperty("offset")
  public Integer offset = 0;

  @JsonProperty("max-docs")
  public Integer maxDocs = Integer.MAX_VALUE;

  @JsonProperty("batch-size")
  public Integer batchSize = 1000;
  
  @JsonProperty("id-field")
  public String idField = "id";


  static public class Setup {
    @JsonProperty("setup-name")
    public String name;

    @JsonProperty("collection")
    public String collection;
    
    @JsonProperty("create-collection")
    public boolean createCollection = true;    

    @JsonProperty("configset")
    public String configset;
    
    @JsonProperty("share-configset")
    public boolean shareConfigset = false;

    @JsonProperty("replication-factor")
    public Integer replicationFactor;

    @JsonProperty("nrt-replicas")
    public Integer nrtReplicas;

    @JsonProperty("tlog-replicas")
    public Integer tlogReplicas;
    
    @JsonProperty("pull-replicas")
    public Integer pullReplicas;

    @JsonProperty("collection-creation-params")
    public Map<String, String> collectionCreationParams;

    @JsonProperty("shards")
    public int shards;

    // Reuse client or create a new client instance per batch of indexing?
    @JsonProperty ("single-client")
    public boolean singleClient = false;

    /**
     * Whether to delete the collection (if exists) on start. Mostly a safeguard for "external" provisioning method
     *
     * In the code, this will be default to false for "external" mode; otherwise true
     *
     * @see  Cluster#provisioningMethod
     */
    @JsonProperty("delete-collection-on-start")
    public Boolean deleteCollectionOnStart;

    @JsonProperty("thread-step")
    public int threadStep;
  }
}

