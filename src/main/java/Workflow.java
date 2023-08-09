import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.solr.benchmarks.beans.Cluster;
import org.apache.solr.benchmarks.beans.Repository;

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
	Map<String, TaskType> taskTypes;

	@JsonProperty("global-variables")
	Map<String, Integer> globalVariables;

	@JsonProperty("global-constants")
	Map<String, String> globalConstants;

	@JsonProperty("threadpools")
	List<ThreadpoolInfo> threadpools = Collections.emptyList();

	@JsonProperty("execution-plan")
	Map<String, TaskInstance> executionPlan;
	
	// Validations definition
	@JsonProperty("validations")
	Map<String, Validation> validations;
	
	@JsonProperty("metrics")
	public List<String> metrics;

	@JsonProperty("prometheus-metrics")
	public List<String> prometheusMetrics;
	// port of the prometheus exporter endpoint to scrape, ex: 9100
	@JsonProperty("prometheus-metrics-port")
	public String prometheusMetricsPort;

	@JsonProperty("zk-metrics")
	public List<String> zkMetrics = Collections.emptyList();

	static class ThreadpoolInfo {
		public String name;
		public int size;
	}
	
	static class Validation {
		@JsonProperty("num-inactive-replicas")
		Integer numInactiveReplicas;
		
		@JsonProperty("match-docs")
		MatchDocs matchDocs;
		
		static class MatchDocs {
			@JsonProperty("indexing-tasks")
			List<String> indexingTasks;
			
		}
	}
}
