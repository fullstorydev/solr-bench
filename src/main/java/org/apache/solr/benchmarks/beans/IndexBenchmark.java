package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class IndexBenchmark extends BaseBenchmark {
  @JsonProperty("replication-type")
  public String replicationType;

  @JsonProperty("dataset-file")
  public String datasetFile;

  @JsonProperty("file-format")
  public String fileFormat;

  @JsonProperty("prepare-binary-format")
  public String prepareBinaryFormat = null;

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

  @JsonProperty("interrupt-on-failure")
  public boolean interruptOnFailure;

  /**
   * An explicit update path to use for indexing. If not set, the update path will be computed
   */
  @JsonProperty("update-path")
  public String updatePath;

  /**
   *  Retries up to this amount of time if indexing op failed (non successful http response code or IOException), has no effect if interruptOnFailure is true
   */
  @JsonProperty("max-retry")
  public int maxRetry;

  /**
   * whether to append commit=true on update calls
   */
  @JsonProperty("commit")
  public boolean commit;

  /**
   * whether to use live collection state for indexing. This could be slow. Only set to false if the cluster/collection state could change during the test
   */
  @JsonProperty("live-state")
  public boolean liveState;
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

