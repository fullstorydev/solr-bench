package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class QueryBenchmark {
  @JsonProperty("name")
  public String name;

  @JsonProperty("description")
  public String description;

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

  @JsonProperty("min-threads")
  public int minThreads;

  @JsonProperty("max-threads")
  public int maxThreads;

  @JsonProperty
  public Integer rpm;

  @JsonProperty("duration-secs")
  public Integer duration;

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
}
