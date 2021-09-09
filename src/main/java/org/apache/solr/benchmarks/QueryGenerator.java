package org.apache.solr.benchmarks;

import org.apache.commons.io.FileUtils;
import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.readers.TarGzFileReader;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class QueryGenerator {
    final QueryBenchmark queryBenchmark;
    List<String> queries = new ArrayList<>();
    Random random;
    AtomicLong counter = new AtomicLong();

    public QueryGenerator(QueryBenchmark queryBenchmark) throws IOException {
        this.queryBenchmark = queryBenchmark;
        File file = new File(queryBenchmark.queryFile);
        if (queryBenchmark.queryFile.endsWith(".tar.gz")) {
            queries = new ArrayList<>();
            TarGzFileReader.readFilesFromZip(
                    file,
                    s -> s.endsWith(".json"),
                    q -> queries.add(q)
            );
        } else {
            queries = FileUtils.readLines(file, "UTF-8");
        }
        if (Boolean.TRUE.equals(queryBenchmark.shuffle)) {
            random = new Random();
        }

        System.out.println("Total queries: " + queries.size());

    }


    public QueryRequest nextRequest() {
        long idx = random == null ? counter.get() : random.nextInt(queries.size());
        counter.incrementAndGet();
        String q = queries.get((int) (idx % queries.size()));
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
                    Map<String, String> params;
                    if (queryBenchmark.reqTrace) {
                        params = new HashMap<>(queryBenchmark.params);
                        ReqIdUtil.injectReqId(params, ID_INJECTOR);
                    } else {
                        params = queryBenchmark.params;
                    }
                    return new MapSolrParams(params);
                }
            };

        } else {
            MultiMapSolrParams params = Util.parseQueryString(q);
            if (queryBenchmark.reqTrace) {
                ReqIdUtil.injectReqId(params.getMap(), ID_INJECTOR_2);
            }
            request = new QueryRequest(params) {
                @Override
                public String getCollection() {
                    return queryBenchmark.collection;
                }
            };
        }

        return request;
    }

    private static final ReqIdInjector<Map<String, String>> ID_INJECTOR = (ReqIdInjector<Map<String, String>>) (carrier, key, value) -> {
        carrier.put(key, value);
    };
    private static final ReqIdInjector<Map<String, String[]>> ID_INJECTOR_2 = (ReqIdInjector<Map<String, String[]>>) (carrier, key, value) -> {
        carrier.put(key, new String[] { value });
    };
}
