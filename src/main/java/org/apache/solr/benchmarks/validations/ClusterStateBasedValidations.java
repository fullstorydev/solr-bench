package org.apache.solr.benchmarks.validations;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClusterStateBasedValidations extends Validations {
	
	private String baseUrl;
	
	public void init(String baseUrl) throws Exception {
		this.baseUrl = baseUrl;
	}
	
	@JsonProperty("clusterstate-conditions")
	List<String> conditions;
}
