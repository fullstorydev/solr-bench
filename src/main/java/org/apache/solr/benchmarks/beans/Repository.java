package org.apache.solr.benchmarks.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Repository {
  @JsonProperty("name")
  public String name;

  @JsonProperty("url")
  public String url;

  @JsonProperty("submodules")
  public boolean submodules;

  @JsonProperty("build-command")
  public String buildCommand;

  @JsonProperty("package-subdir")
  public String packageSubdir;

  @JsonProperty
  public String user;

  @JsonProperty("commit-id")
  public String commitId;

  @JsonProperty("description")
  public String description;
}
