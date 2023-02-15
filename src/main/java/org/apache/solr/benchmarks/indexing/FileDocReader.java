package org.apache.solr.benchmarks.indexing;

import org.apache.solr.benchmarks.readers.JsonlFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

/**
 * A DocReader that reads from a File.
 */
public class FileDocReader implements DocReader {
  private final BufferedReader reader;
  private final long offset;
  private long docsRead; //docs read so far from all the readDocs invocations
  private Long maxDocs; //max number of totals to be read, if docsRead >= maxDocs, it should stop reading any new docs
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public FileDocReader(File datasetFile, Long maxDocs, long offset) throws IOException {
    this.maxDocs = maxDocs;
    this.reader = JsonlFileType.getBufferedReader(datasetFile);
    this.offset = offset;
  }

  /**
   * Read `count` number of docs if available; otherwise return whatever remains.
   *
   * Stop reading when total number of docs read so far exceeds the maxDocs
   * @param count
   * @return
   * @throws IOException
   */
  @Override
  synchronized public List<String> readDocs(int count) throws IOException {
    if (docsRead >= maxDocs || count <= 0) {
      return null;
    }

    List<String> docs = new ArrayList<>();
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

        if (docsRead < offset) continue;

        // _version_ must be removed or adding doc will fail
        line = line.replaceAll("\"_version_\":\\d*,*", "");
        docs.add(line);
        docsRead++;

        if (maxDocs != null && docsRead >= maxDocs) {
          return docs;
        }
        if (docs.size() >= count) {
          return docs;
        }
      }
      return docs.isEmpty() ? null : docs;
    } catch (java.io.EOFException e) {
      log.info("Likely the Unexpected end of ZLIB input. Likely similar to https://stackoverflow.com/q/55608979. Ignoring for now. Actual exception message: " + e.getMessage());
      return docs;
    }
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
