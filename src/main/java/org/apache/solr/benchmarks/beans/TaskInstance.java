package org.apache.solr.benchmarks.beans;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskInstance {
	@JsonProperty("type")
	public String type;
	
	@JsonProperty("mode")
	public String mode;
	
	@JsonProperty("description")
	public String description;
	
	@JsonProperty("instances")
	public int instances = 1;
	
	@JsonProperty("concurrency")
	public int concurrency = 1; // single threaded by default

	@JsonProperty("threadpool")
	public String threadpool;
	
	@JsonProperty("validations")
	public List<String> validations;
	
	@JsonProperty("parameters")
	public Map<String, String> parameters;
	
	@JsonProperty("pre-task-evals")
	public List<String> preTaskEvals;
	
	@JsonProperty("wait-for")
	public String waitFor;

	/**
	 * Delay the execution of this task instance by n milli-sec
	 */
	@JsonProperty("start-delay")
	public int startDelay;
}
