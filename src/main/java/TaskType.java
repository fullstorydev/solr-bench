import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.benchmarks.beans.QueryBenchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.solr.benchmarks.beans.SetConfigProperties;

public class TaskType {
	@JsonProperty("command")
	String command;

	@JsonProperty("index-benchmark")
	IndexBenchmark indexBenchmark;

	@JsonProperty("query-benchmark")
	QueryBenchmark queryBenchmark;

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
		
	    @JsonProperty("configset")
	    public String configset;

		@JsonProperty("exclude-nodes")
		List<Integer> excludeNodes = new ArrayList<>();

	    @JsonProperty("collection-creation-params")
	    public Map<String, String> collectionCreationParams;

	}

	@JsonProperty("restart-solr-node")
	String restartSolrNode;

	@JsonProperty("restart-all-nodes")
	boolean restartAllNodes = false;

	// Typically used with restart:
	@JsonProperty("await-recoveries")
	boolean awaitRecoveries = false;

	@JsonProperty("pause-solr-node")
	String pauseSolrNode;

	@JsonProperty("pause-seconds")
	int pauseSeconds;

	@JsonProperty("set-config-properties")
	SetConfigProperties setConfigProperties;
	
	@JsonProperty("defaults")
	Map<String, String> defaults;
}
