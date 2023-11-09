package org.apache.solr.benchmarks.beans;

import java.util.Collections;
import java.util.List;
import java.util.Map;


import com.fasterxml.jackson.annotation.JsonProperty;

public class Workflow {
	// Cluster definition
	@JsonProperty("cluster")
	public Cluster cluster;

	@JsonProperty("repositories")
	public List<Repository> repos;

	@JsonProperty("solr-package")
	public String solrPackage;

	@JsonProperty("pre-download")
	public List<String> preDownloadResources;
	
	// Workflow definition
	@JsonProperty("task-types")
	public Map<String, TaskType> taskTypes;

	@JsonProperty("global-variables")
	public Map<String, Integer> globalVariables;

	@JsonProperty("global-constants")
	public Map<String, String> globalConstants;

	@JsonProperty("threadpools")
	public List<ThreadpoolInfo> threadpools = Collections.emptyList();

	@JsonProperty("execution-plan")
	public Map<String, TaskInstance> executionPlan;
	
	// Validations definition
	@JsonProperty("validations")
	public Map<String, Validation> validations;
	
	@JsonProperty("metrics")
	public List<String> metrics;

	// seconds between metrics collection
	@JsonProperty("metrics-collection-interval")
	public int metricsCollectionInterval = 2;

	@JsonProperty("prometheus-metrics")
	public List<String> prometheusMetrics;
	// port of the prometheus exporter endpoint to scrape, ex: 9100
	@JsonProperty("prometheus-metrics-port")
	public String prometheusMetricsPort;

	@JsonProperty("zk-metrics")
	public List<String> zkMetrics = Collections.emptyList();

	/**
	 * If defined, the benchmarking will export/expose tests metrics to prometheus by running the plain Java HttpServer at
	 * the port defined
	 */
	@JsonProperty("prometheus-export-port")
	public Integer prometheusExportPort;

	public static class ThreadpoolInfo {
		public String name;
		public int size;
	}
	
	public static class Validation {
		@JsonProperty("num-inactive-replicas")
		public Integer numInactiveReplicas;
		
		@JsonProperty("match-docs")
		public MatchDocs matchDocs;

		public static class MatchDocs {
			@JsonProperty("indexing-tasks")
			public List<String> indexingTasks;
			
		}
	}
}
