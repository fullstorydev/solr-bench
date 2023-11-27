package org.apache.solr.benchmarks.beans;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskType {
	@JsonProperty("command")
	public String command;

	@JsonProperty("index-benchmark")
	public IndexBenchmark indexBenchmark;

	@JsonProperty("query-benchmark")
	public QueryBenchmark queryBenchmark;

	@JsonProperty("cluster-state-benchmark")
	public ClusterStateBenchmark clusterStateBenchmark;

	@JsonProperty("move-replica")
	public MoveReplica moveReplica;

	public static class MoveReplica {
		@JsonProperty("collection")
		public String collection;
	}
	
	public static class ClusterStateBenchmark {
		@JsonProperty("filename")
		public String filename; // File containing the /solr/admin/collections?action=CLUSTERSTATUS output
		
		@JsonProperty("simulation-concurrency-fraction")
		public double simulationConcurrencyFraction = 0.5; // Concurrency will be: (simulConcurrencyFraction * availableCPUs + 1)
		
		/**
		 * The number of collections to index from the cluster state. By default -1 (i.e. index all collections.
		 */
		@JsonProperty("collections-limit")
		public int collectionsLimit = -1;
		
		@JsonProperty("configset")
		public String configset;

		@JsonProperty("exclude-nodes")
		public List<Integer> excludeNodes = new ArrayList<>();

		@JsonProperty("collection-creation-params")
		public Map<String, String> collectionCreationParams;

	}

	@JsonProperty("restart-solr-node")
	public String restartSolrNode;

	@JsonProperty("restart-all-nodes")
	public boolean restartAllNodes = false;

	// Typically used with restart:
	@JsonProperty("await-recoveries")
	public boolean awaitRecoveries = false;

	@JsonProperty("pause-solr-node")
	public String pauseSolrNode;

	@JsonProperty("pause-seconds")
	public int pauseSeconds;
	
	@JsonProperty("defaults")
	public Map<String, String> defaults;
}
