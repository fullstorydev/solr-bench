package org.apache.solr.benchmarks;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.beans.SolrBenchQuery;
import org.apache.solr.benchmarks.readers.TarGzFileReader;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public class QueryGenerator {
    final QueryBenchmark queryBenchmark;
    // = new ArrayList<>();
    List<SolrBenchQuery> sbQueries;
    Random random;
    AtomicLong counter = new AtomicLong();
    final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    List<String> queryStrings = new ArrayList<>();

    public QueryGenerator(QueryBenchmark queryBenchmark) throws IOException, ParseException {
        this.queryBenchmark = queryBenchmark;
        File file = Util.resolveSuitePath(queryBenchmark.queryFile);

        if (queryBenchmark.queryFile.endsWith(".tar.gz")) {
            TarGzFileReader.readFilesFromZip(
                    file,
                    s -> s.endsWith(".json"),
                    q -> queryStrings.add(q)
            );
        } else {
            queryStrings = FileUtils.readLines(file, "UTF-8");
        }
        sbQueries = new ArrayList<SolrBenchQuery>();
        for(String queryString: queryStrings) {
        	sbQueries.add(new SolrBenchQuery(queryString));
        }

        if (Boolean.TRUE.equals(queryBenchmark.shuffle)) {
            random = new Random();
        }

        if (queryBenchmark.endDate != null) {
            this.queryBenchmark.params.put("NOW", String.valueOf(DATE_FORMAT.parse(queryBenchmark.endDate).getTime()));
        }
        System.out.println("Total queries: " + sbQueries.size());
    }


    public SolrBenchQuery nextRequest() {
    	while (counter.get() < queryBenchmark.offset) {
            long idx = random == null ? counter.get() : random.nextInt(sbQueries.size());
            String q = (sbQueries.get((int) (idx % sbQueries.size()))).queryString;
            long c = counter.incrementAndGet();
            System.err.println("Skipping query "+c+": "+q);
    	}
        
    	long idx = random == null ? counter.get() : random.nextInt(sbQueries.size());
        //String q = queries.get((int) (idx % queries.size()));
    	SolrBenchQuery query = sbQueries.get((int) (idx % sbQueries.size()));
        counter.incrementAndGet();
        QueryRequest request;
        if (queryBenchmark.templateValues != null && !queryBenchmark.templateValues.isEmpty()) {
        	query.queryString = PropertiesUtil.substituteProperty(query.queryString, queryBenchmark.templateValues);
        }

        //TODO apply templates if any
        if (Boolean.TRUE.equals(queryBenchmark.isJsonQuery)) {
            request = new QueryRequest() {
                @Override
                public METHOD getMethod() {
                    return METHOD.POST;
                }

                @Override
                public RequestWriter.ContentWriter getContentWriter(String expectedType) {
                    return new RequestWriter.StringPayloadContentWriter(query.queryString, CommonParams.JSON_MIME);
                }

                @Override
                public String getCollection() {
                    return queryBenchmark.collection;
                }

                @Override
                public ResponseParser getResponseParser() {
                    return new InputStreamResponseParser("json");
                }

                @Override
                public String toString() {
                    return query.queryString;
                }

                @Override
                public SolrParams getParams() {
                    return new MapSolrParams(queryBenchmark.params);
                }

                @Override
                public Map<String, String> getHeaders() {
                    return queryBenchmark.headers;
                }
            };

        } else {
            request = new QueryRequest(Util.parseQueryString(query.queryString)) {
                @Override
                public String getCollection() {
                    return queryBenchmark.collection;
                }
            };
        }
        query.request = request;
        return query;
    }
}
