package org.apache.solr.benchmarks.indexing;

import java.io.IOException;

public interface LeaderUrlProvider {
  String findLeaderUrl(String shard) throws IOException;
}
