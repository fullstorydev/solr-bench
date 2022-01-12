package org.apache.solr.benchmarks.beans;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Cluster {
  @JsonProperty("num-solr-nodes")
  public int numSolrNodes;

  @JsonProperty("zk-instance-config")
  public InstanceConfig zkInstanceConfig;

  @JsonProperty("solr-instance-config")
  public InstanceConfig solrInstanceConfig;

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

  public static class InstanceConfig {
    @JsonProperty("image-project")
    public String imageProject;

    @JsonProperty("image-name")
    public String imageName;

    @JsonProperty("network")
    public String network;

    @JsonProperty("region")
    public String region;

    @JsonProperty("instance-type")
    public String instanceType;
  }

  public static InstanceConfig DEFAULT_INSTANCE_CONFIG = new InstanceConfig();

  static {
    DEFAULT_INSTANCE_CONFIG.imageProject = "centos-cloud";
    DEFAULT_INSTANCE_CONFIG.imageName = "centos-8-v20191210";
    DEFAULT_INSTANCE_CONFIG.network = "default";
    DEFAULT_INSTANCE_CONFIG.region = "us-central1-a";
    DEFAULT_INSTANCE_CONFIG.instanceType = "n1-standard-1";
  }
}
