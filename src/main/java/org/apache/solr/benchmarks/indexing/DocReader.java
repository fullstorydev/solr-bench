package org.apache.solr.benchmarks.indexing;

import java.io.IOException;
import java.util.List;

public interface DocReader extends AutoCloseable {
  /**
   * Stateful reader that returns a list of docs starting from last pointer with returned size according to the count provided.
   *
   * For bounded reader, this might return less than the count if the input docs exhaust. Subsequent calls should
   * start returning null to indicate no more docs should be read
   * @param count
   * @return
   * @throws IOException
   */
  List<String> readDocs(int count) throws IOException;
}
