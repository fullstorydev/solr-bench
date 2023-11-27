package org.apache.solr.benchmarks.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Query specific stats listener that accumulates stats for:
 * <ol>
 *     <li>Duration of query execution (percentile)</li>
 *     <li>Document hit count of the query (percentile)</li>
 *     <li>Error count on the query (long)</li>
 * </ol>
 */
public class DetailedQueryStatsListener implements QueryResponseContentsListener {
  private final ConcurrentMap<String, SynchronizedDescriptiveStatistics> durationStatsByType = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, SynchronizedDescriptiveStatistics> docHitCountStatsByType = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicLong> errorCountStatsByType = new ConcurrentHashMap<>();
  private final AtomicBoolean loggedQueryRspError = new AtomicBoolean(false);
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public void onExecutionComplete(BenchmarksMain.OperationKey key, QueryResponseContents response, long duration) {
    if (key != null) {
      SynchronizedDescriptiveStatistics durationStats = durationStatsByType.computeIfAbsent((String) key.getAttributes().get("query"), (typeKey) -> new SynchronizedDescriptiveStatistics());
      durationStats.addValue(duration / 1_000_000.0);
      if (response != null) {

        if (response.isSuccessful()) {
          SynchronizedDescriptiveStatistics hitCountStats = docHitCountStatsByType.computeIfAbsent((String) key.getAttributes().get("query"), (typeKey) -> new SynchronizedDescriptiveStatistics());
          int hitCount = getHitCount(response.getResponseStreamAsString());
          if (hitCount != -1) {
            hitCountStats.addValue(hitCount);
          }
        } else {
          AtomicLong errorCount = errorCountStatsByType.computeIfAbsent((String) key.getAttributes().get("query"), (typeKey) -> new AtomicLong(0));
          errorCount.incrementAndGet();
          //this could be noisy
          if (!loggedQueryRspError.getAndSet(true) || logger.isDebugEnabled()) {
            logger.warn("Non successful response. The response stream is " + response.getResponseStreamAsString());
          }
        }
      }
    }
  }


  private int getHitCount(String response) {
    Map<String, Object> jsonResponse;
    try {
      jsonResponse = new ObjectMapper().readValue(response, Map.class);
    } catch (JsonProcessingException e) {
      logger.warn("Failed to json parse the response stream " + response);
      return -1;
    }

    if (jsonResponse.containsKey("response")) {
      return (int) ((Map<String, Object>) jsonResponse.get("response")).get("numFound");
    } else {
      logger.warn("The json response stream does not have key `response`. The json response stream : " + jsonResponse);
      return -1;
    }
  }


  public List<DetailedStats> getStats() {
    List<DetailedStats> results = new ArrayList<>();
    durationStatsByType.forEach((key, stats) -> results.add(new DetailedStats(StatsMetricType.DURATION, key, stats)));
    docHitCountStatsByType.forEach((key, stats) -> results.add(new DetailedStats(StatsMetricType.DOC_HIT_COUNT, key, stats)));
    errorCountStatsByType.forEach((key, stats) -> results.add(new DetailedStats(StatsMetricType.ERROR_COUNT, key, stats)));

    return results;
  }

  public enum StatsMetricType {
    DURATION("timings"), DOC_HIT_COUNT("percentile"), ERROR_COUNT("error_count");
    private final String dataCategory;

    StatsMetricType(String dataCategory) {
      this.dataCategory = dataCategory;
    }

    public String getDataCategory() {
      return dataCategory;
    }
  }
}
