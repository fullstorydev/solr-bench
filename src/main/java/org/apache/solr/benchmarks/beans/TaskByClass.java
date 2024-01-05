package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class TaskByClass extends BaseBenchmark {
  @JsonProperty("task-class")
  public String taskClass;

  @JsonProperty("params")
  public Map<String, Object> params;
}
