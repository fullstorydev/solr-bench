package org.apache.solr.benchmarks.indexing;

import io.prometheus.client.Counter;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.benchmarks.prometheus.PrometheusExportManager;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An upload task to a single shard with a list of docs
 */
class UploadDocs implements Callable {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Counter dataWriteCounter = PrometheusExportManager.registerCounter("solr_bench_data_write", "solr data written in bytes", "collection", "zk_host", "test_suite");
  final List<String> docs;
  final HttpClient client;
  private final boolean interruptOnFailure;
  private AtomicLong totalUploadedDocs;
  final String batchFilename;

  final String updateEndpoint;
  final String contentType;
  final byte[] payload;

  UploadDocs(IndexBenchmark benchmark, String batchFilename, List<String> docs, HttpClient client, String leaderUrl, AtomicLong totalUploadedDocs) {
    this.docs = docs;
    this.client = client;
    this.totalUploadedDocs = totalUploadedDocs;
    this.batchFilename = batchFilename;
    this.interruptOnFailure = benchmark.interruptOnFailure;

    log.debug("Batch file: " + batchFilename);

    if (batchFilename == null) { // plain JSON docs
      this.updateEndpoint = leaderUrl + "/update/json/docs";
      this.contentType = "application/json; charset=UTF-8";
      payload = null; // docs will be indexed one by one
    } else {
      try {
        payload = FileUtils.readFileToByteArray(new File(batchFilename));
      } catch (IOException e) {
        throw new RuntimeException("Cannot open pre-initialized batch file: " + batchFilename, e);
      }
      if (batchFilename.endsWith(".json")) {
        this.updateEndpoint = leaderUrl + "/update/json/docs";
        this.contentType = "application/json; charset=UTF-8";
      } else if (batchFilename.endsWith(".javabin")) {
        this.updateEndpoint = leaderUrl + "/update";
        this.contentType = "application/javabin";
      } else if (batchFilename.endsWith(".cbor")) {
        this.updateEndpoint = leaderUrl + "/update/cbor";
        this.contentType = "application/cbor";
      } else {
        throw new RuntimeException("Cannot determine update endpoint or content type for pre-initialized batch file: " + batchFilename);
      }
    }
  }

  @Override
  public Object call() throws IOException {
    log.debug("Posting to " + updateEndpoint + ", type: " + contentType + ", size: " + (payload == null ? 0 : payload.length));
    HttpPost httpPost = new HttpPost(updateEndpoint);
    httpPost.setHeader(new BasicHeader("Content-Type", contentType));
    httpPost.getParams().setParameter("overwrite", "false");

    httpPost.setEntity(new BasicHttpEntity() {
      @Override
      public boolean isStreaming() {
        return true;
      }

      @Override
      public void writeTo(OutputStream outstream) throws IOException {
        if (payload == null) {
          OutputStreamWriter writer = new OutputStreamWriter(outstream);
          int docSize = 0;
          for (String doc : docs) {
            writer.append(doc).append('\n');
            docSize += doc.getBytes().length;
          }
          dataWriteCounter.inc(docSize);
          writer.flush();
        } else {
          dataWriteCounter.inc(payload.length);
          outstream.write(payload);
          outstream.flush();
        }
      }
    });

    HttpResponse rsp = client.execute(httpPost);
    httpPost.getEntity().getContentLength();
    int statusCode = rsp.getStatusLine().getStatusCode();
    if (!HttpStatus.isSuccess(statusCode)) {
      log.error("Failed a request: " +
              rsp.getStatusLine() + " " + EntityUtils.toString(rsp.getEntity(), StandardCharsets.UTF_8));
      if (interruptOnFailure) {
        throw new IOException("Failed to execute index call on " + updateEndpoint + ", status code: " + statusCode + " reason: " + rsp.getStatusLine().getReasonPhrase());
      }
    } else {
      rsp.getEntity().getContent().close();
      totalUploadedDocs.addAndGet(docs.size());
    }

    return null;
  }
}
