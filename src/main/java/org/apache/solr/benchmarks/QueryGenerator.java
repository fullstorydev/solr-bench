package org.apache.solr.benchmarks;

import org.apache.commons.io.FileUtils;
import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.readers.TarGzFileReader;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.CommonParams;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
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


    public static void main(String[] args) throws Exception {
        QueryBenchmark qb = new QueryBenchmark();
        qb.shuffle = Boolean.TRUE;
        qb.collection = "test";
        qb.queryFile = "small-data/allqueries.tar.gz";
        qb.isJsonQuery = true;

        QueryGenerator qg = new QueryGenerator(qb);
        Random random = new Random();

        ControlledExecutor ce = new ControlledExecutor(16, 20, 80, 5l, -1, () -> () -> {
            QueryRequest req = qg.nextRequest();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                req.getContentWriter(CommonParams.JSON_MIME).write(bos);
                System.out.println(new Date().toString() + " : " + new String(bos.toByteArray(), StandardCharsets.UTF_8));
                Thread.sleep(random.nextInt(50) + 10);
            } catch (IOException e) {
                /*does not matter*/
            } catch (InterruptedException e) {

            }
        });

        ce.run();


    }

}
