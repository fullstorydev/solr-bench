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

	@JsonProperty("repository")
	public Repository repo;

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

	@JsonProperty("execution-plan")
	Map<String, TaskInstance> executionPlan;
	
	@JsonProperty("metrics")
	public List<String> metrics;

	@JsonProperty("zk-metrics")
	public List<String> zkMetrics = Collections.emptyList();

}
