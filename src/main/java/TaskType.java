import java.util.Map;

import org.apache.solr.benchmarks.beans.IndexBenchmark;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskType {
	@JsonProperty("command")
	String command;

	@JsonProperty("index-benchmark")
	IndexBenchmark indexBenchmark;

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
