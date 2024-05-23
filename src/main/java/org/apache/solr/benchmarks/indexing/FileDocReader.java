package org.apache.solr.benchmarks.indexing;

import org.apache.solr.benchmarks.readers.JsonlFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

  private static String generateRandomString(int targetStringLength) {
    char leftLimit = 'a'; // numeral '0'
    char rightLimit = 'z'; // letter 'Z'
    Random random = new Random();
    StringBuilder buffer = new StringBuilder(targetStringLength);
    for (int i = 0; i < targetStringLength; i++) {
      int randomLimitedInt = random.nextInt(rightLimit - leftLimit + 1) + leftLimit;
//      System.out.println(randomLimitedInt + "=>" + (char)randomLimitedInt);
      buffer.append((char) randomLimitedInt);
    }
    return buffer.toString();
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
    if ((maxDocs != null && docsRead >= maxDocs) || count <= 0) {
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
        //"_version_":1753748992113508354,
        //"Id":"11zsy1wa13sd2!11zsy1wa13sd2_1chsbyn053ntx",
        line = line.replaceAll("\"_version_\":\\d*,*", "");
        line = line.replaceAll("\"Id\":\"[a-z0-9!]*\"", "\"Id\":\"" + generateRandomString(16) + "\"");
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
      return docs.isEmpty() ? null : docs;
    }
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
