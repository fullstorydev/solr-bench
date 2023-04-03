package org.apache.solr.benchmarks.beans;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// nocommit: javadocs
public class SolrBenchQueryResponse {
	
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public String queryString;
	
	public String responseString = null;
	
	public NamedList<Object> rawResponse;
	
	public boolean isSuccessfulResponse = false;
	
	public SolrBenchQueryResponse (String queryString, NamedList<Object> response) {
		this.rawResponse = response;
		this.queryString = queryString;
		
		InputStream responseStream = (InputStream) response.get("stream");
		try {
			responseString = getResponseStreamAsString(responseStream); // should only call this once, as this reads the stream!
		} catch (IOException e) {
			log.warn("Failed to read the response stream for " + queryString);
		}

		isSuccessfulResponse = isSuccessfulRsp(response.get("closeableResponse"));
	}
	
	private String getResponseStreamAsString(InputStream responseStream) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IOUtils.copy(responseStream, baos);

		return new String(baos.toByteArray());
	}
	
	private boolean isSuccessfulRsp(Object responseObj) {
		if (responseObj instanceof CloseableHttpResponse) {
			int statusCode = ((CloseableHttpResponse) responseObj).getStatusLine().getStatusCode();
			if (statusCode == 200) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}


}
