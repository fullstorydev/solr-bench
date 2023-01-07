package org.apache.solr.benchmarks.beans;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Cluster {
  @JsonProperty("num-solr-nodes")
  public int numSolrNodes;

  @JsonProperty("ssh-flags")
  public String sshFlags;

  @JsonProperty("jdk-url")
  public String jdkUrl;
  
  @JsonProperty("jdk-tarball")
  public String jdkTarball;
  
  @JsonProperty("jdk-directory")
  public String jdkDirectory;

  @JsonProperty("startup-params")
  public String startupParams;

  @JsonProperty("startup-params-overrides")
  public List<String> startupParamsOverrides;
  
  @JsonProperty("terraform-gcp-config")
  public Map<String, Object> terraformGCPConfig;

  @JsonProperty("vagrant-config")
  public Map<String, Object> vagrantConfig;

  @JsonProperty("provisioning-method")
  public String provisioningMethod;

  @JsonProperty("zk-host")
  public String zkHost;

  @JsonProperty("zk-port")
  public Integer zkPort;

  @JsonProperty("zk-admin-port")
  public Integer zkAdminPort;

  @JsonProperty("solr-nodes")
  public List<Node> solrNodes;

  public static class Node {
    @JsonProperty("host")
    public String host;

    @JsonProperty("port")
    public Integer port;

    @JsonProperty("qa")
    public boolean qa = false;

    @Override
    public String toString() {
      return "Node{" +
              "host='" + host + '\'' +
              ", port=" + port +
              ", qa=" + qa +
              '}';
    }
  }

}
