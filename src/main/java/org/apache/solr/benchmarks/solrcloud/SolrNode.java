package org.apache.solr.benchmarks.solrcloud;

public interface SolrNode {

  void provision() throws Exception;

  void init() throws Exception;

  int start() throws Exception;

  int stop() throws Exception;
  
  int pause(int seconds) throws Exception;

  String getBaseUrl();
  
  String getNodeName();
  
  void cleanup() throws Exception;

}