package org.apache.solr.benchmarks.beans;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.solr.client.solrj.request.QueryRequest;

public class SolrBenchQuery {
	public String queryString;
	
	public QueryRequest request;
	
	public String response;
	
	public SolrBenchQuery (String queryString){
		this.queryString = queryString;
	}
}
