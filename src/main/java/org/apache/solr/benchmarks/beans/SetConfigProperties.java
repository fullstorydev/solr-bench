package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SetConfigProperties {
  @JsonProperty("collection")
  public String collection;

  @JsonProperty("properties")
  public Map<String, Object> properties;
}
