package org.apache.solr.benchmarks.query;

import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.apache.solr.benchmarks.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

public class DetailedStats {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final DetailedQueryStatsListener.StatsMetricType metricType;
  private final Object statsObj;
  private final String queryType;
  private final Map<String, Object> extraProperties = new HashMap<>();

  DetailedStats(DetailedQueryStatsListener.StatsMetricType metricType, String queryType, Object stats) {
    this.metricType = metricType;
    this.queryType = queryType;
    this.statsObj = stats;
  }

  public String getStatsName() {
    return "(" + metricType + ") " + queryType;
  }

  public String getQueryType() {
    return queryType;
  }

  public DetailedQueryStatsListener.StatsMetricType getMetricType() {
    return metricType;
  }

  public void setExtraProperty(String key, Object value) {
    extraProperties.put(key, value);
  }

  public Map values() {
    Map resultMap;
    if (statsObj instanceof SynchronizedDescriptiveStatistics) {
      SynchronizedDescriptiveStatistics stats = (SynchronizedDescriptiveStatistics) statsObj;
      resultMap = Util.map("5th", stats.getPercentile(5), "10th", stats.getPercentile(10), "50th", stats.getPercentile(50), "90th", stats.getPercentile(90),
              "95th", stats.getPercentile(95), "99th", stats.getPercentile(99), "mean", stats.getMean(), "min", stats.getMin(), "max", stats.getMax(), "total-queries", stats.getN());
    } else if (statsObj instanceof Number) {
      resultMap = Util.map("count", ((Number) statsObj).doubleValue());
    } else {
      logger.warn("Unexpected stats type " + statsObj.getClass());
      return null;
    }
    resultMap.putAll(extraProperties);
    return resultMap;
  }
}
