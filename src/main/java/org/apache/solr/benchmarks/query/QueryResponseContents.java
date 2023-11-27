package org.apache.solr.benchmarks.query;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;

public class QueryResponseContents {
  private final boolean isSuccessful;
  private final InputStream responseStream;
  private volatile String responseStreamString;

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public QueryResponseContents(NamedList<Object> queryRsp) {
    this.isSuccessful = isSuccessfulRsp(queryRsp.get("closeableResponse"));
    this.responseStream = (InputStream) queryRsp.get("stream");
  }

  public synchronized String getResponseStreamAsString() {
    if (responseStreamString == null) {
      responseStreamString = readResponseStreamAsString(responseStream);
    }
    return responseStreamString;
  }

  public boolean isSuccessful() {
    return isSuccessful;
  }

  private static boolean isSuccessfulRsp(Object responseObj) {
    if (responseObj instanceof CloseableHttpResponse) {
      int statusCode = ((CloseableHttpResponse) responseObj).getStatusLine().getStatusCode();
      if (statusCode == 200) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  private static String readResponseStreamAsString(InputStream responseStream) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      IOUtils.copy(responseStream, baos);
      return new String(baos.toByteArray());
    } catch (IOException e) {
      logger.warn("Failed to parse response stream: " + e.getMessage());
      return null;
    }


  }
}
