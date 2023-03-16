package org.apache.solr.benchmarks;

import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.readers.TarGzFileReader;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class QueryGenerator {
    final QueryBenchmark queryBenchmark;
    List<String> queries = new ArrayList<>();
    Random random;
    AtomicLong counter = new AtomicLong();
    final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public QueryGenerator(QueryBenchmark queryBenchmark) throws IOException, ParseException {
        this.queryBenchmark = queryBenchmark;
        File file = Util.resolveSuitePath(queryBenchmark.queryFile);
        if (queryBenchmark.queryFile.endsWith(".tar.gz")) {
            queries = new ArrayList<>();
            TarGzFileReader.readFilesFromZip(
                    file,
                    s -> s.endsWith(".json"),
                    q -> queries.add(q)
            );
        } else {
            queries = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        }
        if (Boolean.TRUE.equals(queryBenchmark.shuffle)) {
            random = new Random();
        }

        if (queryBenchmark.endDate != null) {
            this.queryBenchmark.params.put("NOW", String.valueOf(DATE_FORMAT.parse(queryBenchmark.endDate).getTime()));
        }

        System.out.println("Total queries: " + queries.size());

    }


    public QueryRequest nextRequest() {
    	while (counter.get() < queryBenchmark.offset) {
            long idx = random == null ? counter.get() : random.nextInt(queries.size());
            String q = queries.get((int) (idx % queries.size()));
            long c = counter.incrementAndGet();
            System.err.println("Skipping query "+c+": "+q);
    	}
        
    	long idx = random == null ? counter.get() : random.nextInt(queries.size());
        String q = queries.get((int) (idx % queries.size()));
        counter.incrementAndGet();
        
        QueryRequest request;
        if (queryBenchmark.templateValues != null && !queryBenchmark.templateValues.isEmpty()) {
            PropertiesUtil.substituteProperty(q, queryBenchmark.templateValues);
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
                    return new RequestWriter.StringPayloadContentWriter(q, CommonParams.JSON_MIME);
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
                    return q;
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
            request = new QueryRequest(Util.parseQueryString(q)) {
                @Override
                public String getCollection() {
                    return queryBenchmark.collection;
                }
            };
        }

        return request;
    }
}
