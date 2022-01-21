package org.apache.solr.benchmarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.io.IOUtils;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.benchmarks.solrcloud.SolrNode;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GrafanaMetricsCollector implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public Map<String, List<TimedMetrics>> solrMetrics = new ConcurrentHashMap<>();
	public Map<String, Vector<Number>> zkMetrics = new ConcurrentHashMap<String, Vector<Number>>();
	private final List<String> zkMetricsPaths;


	private final List<String> metricsPaths;
	private final SolrCloud cloud;
	private final int collectionDurationSeconds;
	Set<String> groups = new HashSet<String>();
	private static final String TOTAL_SIZE_IN_BYTES_KEY = "totalSizeInBytes";

	private static class TimedMetrics {
		@JsonProperty("timestamp")
		private final long timestamp;

		@JsonProperty("metrics")
		private final Map<String, Number> metricsByPath = new HashMap<>();

		public TimedMetrics(long timestamp) {
			this.timestamp = timestamp;
		}

		private void putMetric(String metricPath, Number value) {
			metricsByPath.put(metricPath, value);
		}
	}

	public GrafanaMetricsCollector(SolrCloud cloud, List<String> zkMetricsPaths, List<String> solrMetricsPaths, int collectionIntervalSeconds) {
		for (SolrNode node: cloud.nodes) {
			List<TimedMetrics> metricsByNode = new ArrayList<TimedMetrics>();
			solrMetrics.put(node.getNodeName(), metricsByNode);
		}
		for (String zkMetricPath: zkMetricsPaths) {
			zkMetrics.put(zkMetricPath, new Vector<Number>());
		}

		this.zkMetricsPaths = zkMetricsPaths;
		this.metricsPaths = solrMetricsPaths;
		this.cloud = cloud;
		this.collectionDurationSeconds = collectionIntervalSeconds;
		
		for (String path: metricsPaths) {
			groups.add(path.split("/")[0]);
		}
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		while (running.get()) {
			try {
				// Fetch zk metrics
				{
					try {
						URL url = new URL(cloud.getZookeeperAdminUrl() + "/commands/monitor");
						JSONObject output = new JSONObject(IOUtils.toString(url, Charset.forName("UTF-8")));
						for (String path: zkMetricsPaths) {
							Long metric = output.getLong(path);
							zkMetrics.get(path).add(metric);
						}
					} catch (JSONException | IOException e1) {
						log.error("Couldn't get metrics from " + cloud.getZookeeperAdminUrl(), e1);
					}
				}
				// Fetch Solr metrics
				for (SolrNode node: cloud.nodes) {
					Map<String, JSONObject> resp = new HashMap<String, JSONObject>();

					TimedMetrics timedMetrics = new TimedMetrics(System.currentTimeMillis() - startTime);
					for (String group: groups) {
						try {
							URL url = new URL(node.getBaseUrl()+"admin/metrics?group="+group);
							resp.put(group, new JSONObject(IOUtils.toString(url, Charset.forName("UTF-8"))));
						} catch (JSONException | IOException e1) {
							log.debug("Couldn't get metrics from "+node.getBaseUrl(), e1);
						}
					}

					for (String path: metricsPaths) {
						String[] elements = path.split("/");
						String group = elements[0];
						JSONObject json = resp.get(group);

						String grafanaPath = path.replace('.', '_').replace('/', '_');
						if (json != null) {
							try {
								json = json.getJSONObject("metrics");
								int n = elements.length;
								for (int i = 1; i < n - 1; ++i) {
									json = json.getJSONObject(elements[i]);
								}
								Double metric = json.getDouble(elements[n - 1]);
								timedMetrics.putMetric(grafanaPath, metric);
							} catch (JSONException e) {
								// some key, e.g., solr.core.fsloadtest.shard1.replica_n1 may not be available immediately
								log.debug("skipped metrics path {}:", path, e);
								timedMetrics.putMetric(grafanaPath, -1.0);
							}
						} else { // else this response wasn't fetched
							timedMetrics.putMetric(grafanaPath, -1.0);
						}
					}
					//Always collect size total
					try {
						URL url = new URL(node.getBaseUrl()+"admin/metrics?prefix=INDEX.sizeInBytes");
						JSONObject response = new JSONObject(IOUtils.toString(url, Charset.forName("UTF-8")));
						JSONObject metricsJson = response.getJSONObject("metrics");
						long totalSizeInBytes = 0;
						for (String replica : metricsJson.keySet()) {
							totalSizeInBytes += metricsJson.getJSONObject(replica).getLong("INDEX.sizeInBytes");
						}
						timedMetrics.putMetric(TOTAL_SIZE_IN_BYTES_KEY, totalSizeInBytes);
					} catch (JSONException | IOException e1) {
						log.debug("Couldn't get metrics from "+node.getBaseUrl(), e1);
						timedMetrics.putMetric(TOTAL_SIZE_IN_BYTES_KEY, -1.0);
					}
					solrMetrics.get(node.getNodeName()).add(timedMetrics);

				}
				Thread.sleep(collectionDurationSeconds*1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	AtomicBoolean running = new AtomicBoolean(true);
	public void stop() {
		running.getAndSet(false);
	}
}
