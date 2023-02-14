package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class BaseBenchmark {
  @JsonProperty("name")
  public String name;

  @JsonProperty("description")
  public String description;

  @JsonProperty("duration-secs")
  public Integer durationSecs;

  /**
   * Number of total executions
   */
  @JsonProperty("total-count")
  public Long totalCount;

  @JsonProperty ("rpm")
  public Integer rpm;

  @JsonProperty("min-threads")
  public int minThreads;

  @JsonProperty("max-threads")
  public int maxThreads;
}
