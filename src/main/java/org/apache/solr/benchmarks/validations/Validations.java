package org.apache.solr.benchmarks.validations;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
public abstract class Validations {
	
	@JsonProperty("type")
	public String type;
	
	public void generateValidationsData() throws Exception {
		throw new IllegalAccessException("A sub-class of Validations should've overridden this method.");				
	}

	public void loadValidations() throws Exception {
		throw new IllegalAccessException("A sub-class of Validations should've overridden this method.");		
	}
	
	public Map<String, Number> doValidate() throws Exception {
		throw new IllegalAccessException("A sub-class of Validations should've overridden this method.");
	}
}
