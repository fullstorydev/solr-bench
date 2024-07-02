package org.apache.solr.benchmarks.task;

import io.prometheus.client.Histogram;
import org.apache.solr.benchmarks.BenchmarkContext;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.beans.TaskByClass;
import org.apache.solr.benchmarks.prometheus.PrometheusExportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A task similar to the simple TaskType.command but with more control (concurrent/retries/rate/duration etc)
 */
public class UrlCommandTask extends AbstractTask<UrlCommandTask.ExecutionResult> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final List<String> urls;
  private final int maxRetry; //max retry on non zero exit code, no retry on exception
  private final int RETRY_DELAY = 10000; //10 secs delay
  private final String taskName;
  private final UrlResponseListener listener;
  private final AtomicInteger runCounter = new AtomicInteger();

  public static UrlCommandTask buildTask(TaskByClass taskSpec, List<String> urls) {
    if (taskSpec.durationSecs == null && taskSpec.rpm == null) { //then we assume only run per urls once only
      return new UrlCommandTask(taskSpec, urls, (long)urls.size());
    } else {
      return new UrlCommandTask(taskSpec, urls, null);
    }
  }

  private UrlCommandTask(TaskByClass taskSpec, List<String> urls, Long maxExecution) {
    super(taskSpec, maxExecution);
    this.urls = urls;
    this.taskName = taskSpec.name;
    maxRetry = (int) taskSpec.params.getOrDefault("max-retry", 0);
    listener = new UrlResponseListener();
  }

  @Override
  public ExecutionResult runAction() throws Exception {
    boolean keepRunning = true;
    int retryCount = 0;
    int responseCode = -1;
    Exception exception;
    String url = urls.get(runCounter.getAndIncrement() % urls.size());
    while (keepRunning) { //retry in case of failures
      try {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        responseCode = connection.getResponseCode();
        exception = null;
      } catch (Exception ex) {
        log.warn("Failed task [" + taskName + "] with exception " + ex.getMessage(), ex);
        exception = ex;
      }

      if (!(responseCode >= 200 && responseCode < 300)) {
        log.warn("Error http status code {}", responseCode);
        if (++ retryCount <= maxRetry) {
          log.info("Retry #{} after {} millisecond delay", retryCount, RETRY_DELAY);
          Thread.sleep(RETRY_DELAY);
        } else {
          if (maxRetry > 0) {
            log.warn("Exhausted all {} retries. Stopping the url command task GET {} with http status code {}", maxRetry, url, responseCode);
          } else {
            log.warn("No max-retry configured. Stopping the url command task GET {} with http status code {}", url, responseCode);
          }
          if (exception != null) { //re-throw the exception as the caller should handle it
            throw exception;
          }
          keepRunning = false;
        }
      } else {
        keepRunning = false; //complete successfully
      }
    }
    return new ExecutionResult(url, responseCode);
  }

  @Override
  public ControlledExecutor.ExecutionListener<BenchmarksMain.OperationKey, ExecutionResult>[] getExecutionListeners() {
    return new ControlledExecutor.ExecutionListener[] {
            listener
    };
  }

  /**
   * Supports both prometheus and old style export to xml file
   */
  private static class UrlResponseListener implements ControlledExecutor.ExecutionListener<Object, ExecutionResult> {
    private final Histogram durationHistogram;

    private static final String zkHost = BenchmarkContext.getContext().getZkHost();
    private static final String testSuite = BenchmarkContext.getContext().getTestSuite();
    private final Map<Integer, Long> countByResponseCode = new HashMap<>();
    private final Map<Integer, Long> totalDurationByResponseCode = new HashMap<>();

    public UrlResponseListener() {
      this.durationHistogram = PrometheusExportManager.registerHistogram("solr_bench_url_command_duration", "Duration to execute the http command url", "zk_host", "test_suite", "command_url", "http_status_code");
    }

    @Override
    public void onExecutionComplete(Object typeKey, ExecutionResult result, long durationInNanoSec) {
      long durationInMilliSec = TimeUnit.MILLISECONDS.convert(durationInNanoSec, TimeUnit.NANOSECONDS);
      durationHistogram.labels(zkHost, testSuite, result.url, String.valueOf(result.responseCode)).observe(durationInMilliSec);

      //keep it simple for old style export. only provide granularity on response code (not by url, otherwise we might need map of map)
      long count = countByResponseCode.getOrDefault(result.responseCode, 0L);
      countByResponseCode.put(result.responseCode, count + 1);
      long totalDuration = totalDurationByResponseCode.getOrDefault(result.responseCode, 0L);
      totalDurationByResponseCode.put(result.responseCode, totalDuration + durationInMilliSec);
    }
  }

  @Override
  public Map<String, Object> getAdditionalResult() { //for exporting to old style xml results
    Map<String, Object> averageDurationByStatusCode = new HashMap<>();
    for (Map.Entry<Integer, Long> entry: listener.countByResponseCode.entrySet()) {
      int statusCode = entry.getKey();
      averageDurationByStatusCode.put(String.valueOf(statusCode), listener.totalDurationByResponseCode.get(statusCode)/entry.getValue());
    }
    return Collections.singletonMap("mean-duration-by-status-code", averageDurationByStatusCode);
  }

  @Override
  public void postTask() throws IOException {

  }


  public static class ExecutionResult {
    private final String url;
    private final int responseCode;
    public ExecutionResult(String url, int responseCode) {
      this.url = url;
      this.responseCode = responseCode;
    }
  }

}

