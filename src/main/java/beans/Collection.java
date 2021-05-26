/* Copyright 2020 freecodeformat.com */
package beans;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Collection {

    @JsonProperty("pullReplicas")
    private String pullreplicas;
    @JsonProperty("replicationFactor")
    private String replicationfactor;
    private Map<String, Shard> shards;
    private Router router;
    @JsonProperty("maxShardsPerNode")
    private String maxshardspernode;
    @JsonProperty("autoAddReplicas")
    private String autoaddreplicas;
    @JsonProperty("nrtReplicas")
    private String nrtreplicas;
    @JsonProperty("tlogReplicas")
    private String tlogreplicas;
    @JsonProperty("znodeVersion")
    private int znodeversion;
    @JsonProperty("configName")
    private String configname;
    public void setPullreplicas(String pullreplicas) {
         this.pullreplicas = pullreplicas;
     }
     public String getPullreplicas() {
         return pullreplicas;
     }

    public void setReplicationfactor(String replicationfactor) {
         this.replicationfactor = replicationfactor;
     }
     public String getReplicationfactor() {
         return replicationfactor;
     }

     public Map<String, Shard> getShards() {
         return shards;
     }

    public void setRouter(Router router) {
         this.router = router;
     }
     public Router getRouter() {
         return router;
     }

    public void setMaxshardspernode(String maxshardspernode) {
         this.maxshardspernode = maxshardspernode;
     }
     public String getMaxshardspernode() {
         return maxshardspernode;
     }

    public void setAutoaddreplicas(String autoaddreplicas) {
         this.autoaddreplicas = autoaddreplicas;
     }
     public String getAutoaddreplicas() {
         return autoaddreplicas;
     }

    public void setNrtreplicas(String nrtreplicas) {
         this.nrtreplicas = nrtreplicas;
     }
     public String getNrtreplicas() {
         return nrtreplicas;
     }

    public void setTlogreplicas(String tlogreplicas) {
         this.tlogreplicas = tlogreplicas;
     }
     public String getTlogreplicas() {
         return tlogreplicas;
     }

    public void setZnodeversion(int znodeversion) {
         this.znodeversion = znodeversion;
     }
     public int getZnodeversion() {
         return znodeversion;
     }

    public void setConfigname(String configname) {
         this.configname = configname;
     }
     public String getConfigname() {
         return configname;
     }

}