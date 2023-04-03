package org.apache.solr.benchmarks.validations;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.beans.SolrBenchQueryResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FileBasedQueryValidations extends Validations {
	@JsonProperty("query-validations-file")
	public String queryValidationsFile;

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	
	public List<SolrBenchQueryResponse> generatedQueries;
	public QueryBenchmark benchmark;
	public String baseUrl, collection;
	
	public void init(List<SolrBenchQueryResponse> generatedQueries, QueryBenchmark benchmark, String collection, String baseUrl) {
		this.generatedQueries = generatedQueries;
		this.benchmark = benchmark;
		this.baseUrl = baseUrl;
		this.collection = collection;
	}
	
	@Override
	public Map<String, Number> doValidate() throws Exception {
		if (validations == null) throw new RuntimeException("Validations have not been loaded yet.");

		int success = 0, failure = 0;
		for (SolrBenchQueryResponse sbq: generatedQueries) {
			int numFound = getNumFoundFromSolrQueryResponse(sbq.responseString);
			Map<String, Object> facets = getFacetsFromSolrQueryResponse(sbq.responseString);
			String key = sbq.queryString.replace('\n', ' ').replace('\r', ' ');
			int expectedNumFound = validations.get(key).first();
			String facetsString = new ObjectMapper().writeValueAsString(facets).replace('\n', ' ');
			String expectedFacetsString = validations.get(key).second();
			if (numFound == expectedNumFound && facetsString.trim().equals(expectedFacetsString.trim())) {
				success++;
			} else {
				log.error("Validation failed: numFound=" + numFound+", expected=" + expectedNumFound + ", facets=" + facetsString +", expected="+expectedFacetsString);
				failure++;
			}
		}

		log.info("Successes: "+success+", failures: "+failure);
		return Map.of("success", success, "failure", failure);
	}

	private static Map<String, Object> getFacetsFromSolrQueryResponse(String response)
			throws JsonProcessingException, JsonMappingException {
		Map<String, Object> facets = null;
		Map<String, Object> jsonResponse = new ObjectMapper().readValue(response, Map.class);
		if (jsonResponse.containsKey("facets")) {
			facets = (((Map<String, Object>) jsonResponse.get("facets")));
		}
		return facets;
	}


	private static int getNumFoundFromSolrQueryResponse(String response)
			throws JsonProcessingException, JsonMappingException {
		int numFound = -1;
		Map<String, Object> jsonResponse = new ObjectMapper().readValue(response, Map.class);
		if (jsonResponse.containsKey("response")) {
			numFound = ((int) ((Map<String, Object>) jsonResponse.get("response")).get("numFound"));
		} else {
			log.error("Didn't find a \"response\" key in the returned response: " + response);
		}
		return numFound;
	}

	private Map<String, Pair<Integer, String>> validations = new HashMap<>();

	@Override
	public void loadValidations() throws Exception {
    	if (queryValidationsFile == null) return;
    	
    	if (new File("suites/" + queryValidationsFile).exists() == false) {
    		throw new FileNotFoundException("Query validations file not found: " + queryValidationsFile);
    	}

		for (String line: FileUtils.readLines(new File("suites/" + queryValidationsFile))) {
			String parts[] = line.split("\t");
			validations.put(parts[0], new Pair(Integer.valueOf(parts[1]), parts[2]));
		}
		log.info("Loaded " + validations.size() + " validations from " + queryValidationsFile);
	}
	
	private static int getTotalDocsIndexed(String baseUrl, String collection)
			throws IOException, JsonProcessingException, JsonMappingException {
		int totalDocsIndexed = -1;
		try(HttpSolrClient solrClient = new HttpSolrClient.Builder(baseUrl + "/" + collection).build()) {
			SolrQuery query = new SolrQuery();
			query.set("q", "*:*");
			QueryResponse response = solrClient.query(query);
			
		    totalDocsIndexed = ((Long)response.getResults().getNumFound()).intValue();
		} catch (SolrServerException e) {
			e.printStackTrace();
		}
		return totalDocsIndexed;
	}

	@Override
	public void generateValidationsData() throws Exception {
    	int totalDocsIndexed = getTotalDocsIndexed(baseUrl, collection);
		
    	FileWriter outFile = new FileWriter("suites/validations-"+benchmark.queryFile+"-docs-"+totalDocsIndexed+"-queries-"+benchmark.totalCount+".tsv");
        for (SolrBenchQueryResponse sbq: generatedQueries) {
        	int numFound = getNumFoundFromSolrQueryResponse(sbq.responseString);
        	Map<String, Object> facets = getFacetsFromSolrQueryResponse(sbq.responseString);
        	outFile.write(sbq.queryString.replace('\n', ' ').replace('\r', ' ') +
        			"\t" + numFound + "\t" + new ObjectMapper().writeValueAsString(facets).replace('\n', ' ') + "\n");
        }
        outFile.close();
	}
}
