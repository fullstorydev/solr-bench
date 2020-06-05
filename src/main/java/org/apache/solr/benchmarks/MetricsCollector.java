package org.apache.solr.benchmarks;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.apache.solr.benchmarks.solrcloud.SolrNode;
import org.json.JSONException;
import org.json.JSONObject;

public class MetricsCollector implements Runnable {

	// Key: Node name, Value: Map of metrics (key: metric name, value: list of metrics at various intervals of time) 
	Map<String, Map<String, Vector<Double>>> metrics = new ConcurrentHashMap<String, Map<String, Vector<Double>>>();
	private final List<String> metricsPaths;
	private final List<SolrNode> nodes;
	private final int collectionDurationSeconds;
	Set<String> groups = new HashSet<String>();

	public MetricsCollector(List<SolrNode> nodes, List<String> metricsPaths, int collectionIntervalSeconds) {
		for (SolrNode node: nodes) {
			metrics.put(node.getNodeName(), new ConcurrentHashMap<String, Vector<Double>>());
			for (String metricPath: metricsPaths) {
				metrics.get(node.getNodeName()).put(metricPath, new Vector<Double>());
			}
		}
		this.metricsPaths = metricsPaths;
		this.nodes = nodes;
		this.collectionDurationSeconds = collectionIntervalSeconds;
		
		for (String path: metricsPaths) {
			groups.add(path.split("/")[0]);
		}
	}

	@Override
	public void run() {
		for (;;) {
			if (running.get()) {

				try {
					for (SolrNode node: nodes) {
						Map<String, JSONObject> resp = new HashMap<String, JSONObject>();
						for (String group: groups) {
							URL url = new URL(node.getBaseUrl()+"admin/metrics?group="+group);
							resp.put(group, new JSONObject(IOUtils.toString(url, Charset.forName("UTF-8"))));
						}
						for (String path: metricsPaths) {
							String group = path.split("/")[0];
							JSONObject json = resp.get(group);

							String key1 = path.split("/")[1];
							String key2 = path.split("/")[2];
							Double metric = json.getJSONObject("metrics").getJSONObject(key1).getDouble(key2);
							metrics.get(node.getNodeName()).get(path).add(metric);
						}
					}
					Thread.sleep(collectionDurationSeconds*1000);
				} catch (JSONException | IOException e1) {
					e1.printStackTrace();
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}

	AtomicBoolean running = new AtomicBoolean(true);
	public void stop() {
		running.getAndSet(false);
	}
	
}
