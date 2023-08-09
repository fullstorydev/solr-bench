package org.apache.solr.benchmarks;

import org.apache.commons.io.IOUtils;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.benchmarks.solrcloud.SolrNode;
import org.apache.solr.benchmarks.WorkflowResult;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MetricsCollector implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	// Key: Node name, Value: Map of metrics (key: metric name, value: list of metrics at various intervals of time) 
	public Map<String, Map<String, Vector<Number>>> metrics = Collections.synchronizedMap(new LinkedHashMap<String, Map<String, Vector<Number>>>());
	public Map<String, Vector<Number>> zkMetrics = new ConcurrentHashMap<String, Vector<Number>>();
	private final List<String> zkMetricsPaths;
	private final List<String> metricsPaths;
	private final List<String> prometheusMetrics;
	private final String prometheusMetricsPort;
	private final SolrCloud cloud;
	private final int collectionDurationSeconds;
	Set<String> groups = new HashSet<String>();

	public MetricsCollector(SolrCloud cloud, List<String> zkMetricsPaths, List<String> solrMetricsPaths, List<String> prometheusMetrics, String prometheusMetricsPort, int collectionIntervalSeconds) {
		// default to empty lists, since either of these could be null
		solrMetricsPaths = solrMetricsPaths != null ? solrMetricsPaths : new ArrayList<>();
		prometheusMetrics = prometheusMetrics != null ? prometheusMetrics : new ArrayList<>();

		for (SolrNode node: cloud.nodes) {
			metrics.put(node.getNodeName(), new ConcurrentHashMap<>());
			for (String metricPath: solrMetricsPaths) {
				metrics.get(node.getNodeName()).put(metricPath, new Vector<>());
			}
			for (String prometheusMetric : prometheusMetrics) {
				metrics.get(node.getNodeName()).put(prometheusMetric, new Vector<>());
			}
		}
		for (String zkMetricPath: zkMetricsPaths) {
			zkMetrics.put(zkMetricPath, new Vector<>());
		}

		this.zkMetricsPaths = zkMetricsPaths;
		this.metricsPaths = solrMetricsPaths;
		this.cloud = cloud;
		this.collectionDurationSeconds = collectionIntervalSeconds;
		this.prometheusMetrics = prometheusMetrics;
		this.prometheusMetricsPort = prometheusMetricsPort;

		for (String path: metricsPaths) {
			groups.add(path.split("/")[0]);
		}
	}

	@Override
	public void run() {
		while (running.get()) {
			try {
				fetchZKMetrics();
				fetchSolrMetrics();
				fetchPrometheusMetrics();
				Thread.sleep(collectionDurationSeconds*1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void fetchZKMetrics() {
		try {
			URL url = new URL(cloud.getZookeeperAdminUrl() + "/commands/monitor");
			JSONObject output = new JSONObject(IOUtils.toString(url, Charset.forName("UTF-8")));
			for (String path: zkMetricsPaths) {
				if (path.startsWith("jmx/")) {
					String parts[] = path.split("/");
					double metric = Util.getJmxCPULoad(cloud.getZookeeper().getHost(), 4048, parts[1], parts[2]);
					zkMetrics.get(path).add(metric);
				} else {
					Long metric = output.getLong(path);
					zkMetrics.get(path).add(metric);
				}
			}
		} catch (Exception e1) {
			log.debug("Couldn't get metrics from " + cloud.getZookeeperAdminUrl(), e1);
		}
	}

	private void fetchSolrMetrics() {
		for (SolrNode node: cloud.nodes) {
			Map<String, JSONObject> resp = new HashMap<>();
			for (String group: groups) {
				try {
					URL url = new URL(node.getBaseUrl()+"admin/metrics?group="+group);
					resp.put(group, new JSONObject(IOUtils.toString(url, Charset.forName("UTF-8"))));
				} catch (JSONException | IOException e1) {
					log.debug("Couldn't get metrics from "+node.getBaseUrl(), e1);
				}
			}
			for (String path: metricsPaths) {
				String[] elements = path.split("(?<!\\\\)/");
				String group = elements[0];
				JSONObject json = resp.get(group);

				if (json != null) {
					try {
						json = json.getJSONObject("metrics");
						int n = elements.length;
						for (int i = 1; i < n - 1; ++i) {
							json = json.getJSONObject(elements[i].replace("\\/", "/"));
						}
						Double metric = json.getDouble(elements[n - 1].replace("\\/", "/"));
						metrics.get(node.getNodeName()).get(path).add(metric);
					} catch (JSONException e) {
						// some key, e.g., solr.core.fsloadtest.shard1.replica_n1 may not be available immediately
						log.debug("Skipped metrics path {}: json: " + json, path, e);
						metrics.get(node.getNodeName()).get(path).add(-1.0);
					}
				} else { // else this response wasn't fetched
					metrics.get(node.getNodeName()).get(path).add(-1.0);
				}
			}
		}
	}

	private void fetchPrometheusMetrics() {
		for (SolrNode node: cloud.nodes) {
			Map<String, Double> metricMap = getPrometheusMetrics(node);

			for (String metricName : this.prometheusMetrics) {
				if (metricMap.containsKey(metricName)) {
					metrics.get(node.getNodeName()).get(metricName).add(metricMap.get(metricName));
				} else {
					// metric not found
					metrics.get(node.getNodeName()).get(metricName).add(-1.0);
				}
			}
		}
	}

	private Map<String, Double> getPrometheusMetrics(SolrNode node) {
		String resp = "";
		try {
			// split out the port from the url
			URL url = new URL(node.getHostUrl()+":"+this.prometheusMetricsPort+"/metrics");
			resp = IOUtils.toString(url, Charset.forName("UTF-8"));
			System.out.println(resp);
		} catch (IOException e1) {
			log.debug("Couldn't get prometheus metrics from"+node.getNodeName(), e1);
		}

		Map<String, Double> metricMap = new HashMap<>();
		try {
			BufferedReader reader = new BufferedReader(new StringReader(resp));
			String line = reader.readLine();

			while (line != null) {
				// only parse when not a comment line
				if (!line.contains("#")) {
					String[] tokens = line.split(" ");
					if (tokens.length >= 2) {
						try {
							Double lastToken = Double.parseDouble(tokens[tokens.length - 1]);
							String jointTokens = String.join(" ", Arrays.copyOf(tokens, tokens.length-1));
							metricMap.put(jointTokens, lastToken);
						} catch (NumberFormatException e) {
							log.debug("The last token is not a valid double: " + tokens[tokens.length - 1]);
						}
					}
				}
				line = reader.readLine();
			}
		} catch (Exception e) {
			log.debug("An error occurred while parsing prometheus metrics for node " + node.getBaseUrl(), e);
		}
		return metricMap;
	}

	AtomicBoolean running = new AtomicBoolean(true);
	public void stop() {
		running.getAndSet(false);
	}
}
