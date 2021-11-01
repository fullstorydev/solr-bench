package org.apache.solr.benchmarks.solrcloud;

public interface Zookeeper {

  public int start() throws Exception;

  public int stop() throws Exception;

  public void cleanup() throws Exception;
  
  public String getHost();

  public String getPort();

  public String getAdminPort();

}