package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class QueryBenchmark extends BaseBenchmark {
  @JsonProperty("collection")
  public String collection;

  @JsonProperty("query-file")
  public String queryFile;

  @JsonProperty("json-query")
  public Boolean isJsonQuery;

  @JsonProperty("uri")
  public String uri = "/select";

  @JsonProperty("shuffle")//"random" , "sequential"
  public Boolean shuffle = Boolean.TRUE;

  @JsonProperty("client-type")
  public String clientType;

  @JsonProperty("offset")
  public Integer offset = 0;

  /**
   * Number of total executions
   */
  @JsonProperty("total-count")
  public Long totalCount;

  @JsonProperty("warm-count")
  public int warmCount = -1;

  @JsonProperty("template-values")
  public Map<String,String>  templateValues;

  @JsonProperty("params")
  public Map<String,String> params = new HashMap<>();

  @JsonProperty("headers")
  public Map<String,String> headers;
  
  @JsonProperty("query-node")
  public Integer queryNode = 1;

  @JsonProperty("end-date")
  // This is a date in the format "YYYY-MM-DD" to be used as NOW for Solr queries
  public String endDate;

  /**
   * Keeps track of detailed stats. On top of the stats for all queries, this would keep track and report duration
   * percentiles and doc hit for each query listed in the query file.
   */
  @JsonProperty("detailed-stats")
  public boolean detailedStats = false;
}
