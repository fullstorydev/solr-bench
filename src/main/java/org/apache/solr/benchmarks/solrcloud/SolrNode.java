package org.apache.solr.benchmarks.solrcloud;

public interface SolrNode {

  void provision() throws Exception;

  void init() throws Exception;

  int start() throws Exception;

  int stop() throws Exception;

  String getBaseUrl();
  
  String getNodeName();

  default boolean isQaNode() {
    return false;
  }

  void cleanup() throws Exception;

}