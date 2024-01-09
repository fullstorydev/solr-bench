package org.apache.solr.benchmarks;

public class BenchmarkContext {
  private static BenchmarkContext SINGLETON;
  private final String testSuite;
  private final String zkHost;

  public BenchmarkContext(String testSuite, String zkHost) {
    this.testSuite = testSuite;
    this.zkHost = zkHost;
  }

  public static void setContext(BenchmarkContext context) {
    SINGLETON = context;
  }

  public static BenchmarkContext getContext() {
    return SINGLETON;
  }

  public String getTestSuite() {
    return testSuite;
  }

  public String getZkHost() {
    return zkHost;
  }
}
