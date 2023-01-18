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

  @JsonProperty("external-solr-config")
  public ExternalSolrConfig externalSolrConfig;

  @JsonProperty("provisioning-method")
  public String provisioningMethod;

  public static class ExternalSolrConfig {
    @JsonProperty("zk-host")
    public String zkHost;

    @JsonProperty("zk-admin-port")
    public Integer zkAdminPort;

    @JsonProperty("zk-chroot")
    public String zkChroot;

    @JsonProperty("restart-script")
    public String restartScript;

    @JsonProperty("ssh-username")
    public String sshUserName;
  }
}
