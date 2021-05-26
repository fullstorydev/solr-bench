import java.util.Map;

import org.apache.solr.benchmarks.beans.IndexBenchmark;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskType {
	@JsonProperty("command")
	String command;

	@JsonProperty("index-benchmark")
	IndexBenchmark indexBenchmark;

	@JsonProperty("cluster-state-benchmark")
	ClusterStateBenchmark clusterStateBenchmark;
	
	static class ClusterStateBenchmark {
		@JsonProperty("filename")
		String filename; // File containing the /solr/admin/collections?action=CLUSTERSTATUS output
		
		@JsonProperty("simulation-concurrency-fraction")
		double simulationConcurrencyFraction = 0.5; // Concurrency will be: (simulConcurrencyFraction * availableCPUs + 1)
		
		/**
		 * The number of collections to index from the cluster state. By default -1 (i.e. index all collections.
		 */
		@JsonProperty("collections-limit")
		int collectionsLimit = -1;
		
	    @JsonProperty("collection-creation-params")
	    public Map<String, String> collectionCreationParams;

	}

	@JsonProperty("restart-solr-node")
	String restartSolrNode;

	// Typically used with restart:
	@JsonProperty("await-recoveries")
	boolean awaitRecoveries = false;

	@JsonProperty("pause-solr-node")
	String pauseSolrNode;

	@JsonProperty("pause-seconds")
	int pauseSeconds;
	
	@JsonProperty("defaults")
	Map<String, String> defaults;
}
