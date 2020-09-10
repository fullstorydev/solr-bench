package org.apache.solr.benchmarks;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

public class SendQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendQuery.class);

    static String query(String filepath, String collection, boolean cache, String baseUrl) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(filepath));
        String requestJson = new String(bytes, StandardCharsets.UTF_8);
        QueryRequest request = new QueryRequest() {
            @Override
            public METHOD getMethod() {
                return METHOD.POST;
            }

            @Override
            public RequestWriter.ContentWriter getContentWriter(String expectedType) {
                return new RequestWriter.StringPayloadContentWriter(requestJson, CommonParams.JSON_MIME);
            }

            @Override
            public String getCollection() {
                return collection;
            }

            @Override
            public ResponseParser getResponseParser() {
                return new InputStreamResponseParser("json");
            }

            @Override
            public String toString() {
                return requestJson;
            }

            @Override
            public SolrParams getParams() {
                return new MapSolrParams(Collections.singletonMap("cache", cache ? "enabled" : "disabled"));
            }
        };
        try (HttpSolrClient client = new HttpSolrClient.Builder(baseUrl).build()) {
            NamedList<Object> response = client.request(request, collection);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy((InputStream) response.get("stream"), baos);
            String responseJson = baos.toString(StandardCharsets.UTF_8.name());
            try {
                String prettyResponseJson = new JSONObject(responseJson).toString(2);
                // System.out.println(prettyResponseJson);
                String outputPath = Paths.get(filepath).getFileName() + "-" + cache + "-" + ".json";
                Files.write(Paths.get(outputPath), prettyResponseJson.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
                return prettyResponseJson;
            } catch(Exception e) {
                System.out.println(responseJson);
                throw e;
            }
        }
    }

    public static void main2(String[] args) throws Exception {
        Configurator.setRootLevel(Level.INFO);
//        query("/Users/shan/Downloads/DimensionalityQueriesForSolrBenchmark_20200424/fsplaypen_CartToConfirm_5Step_15days_AnySession_Rage_Not.json",
//                "loadtest", true, "http://localhost:8983/solr/");
        boolean cache = true;
        Files.walk(Paths.get("/Users/shan/Downloads/DimensionalityQueriesForSolrBenchmark_20200424")).forEach(
                p -> {
                    try {
                        if (p.toFile().isFile()) {
                            String a = query(p.toString(), "loadtest", cache, "http://localhost:8983/solr/").replaceAll("\"QTime\": \\d+,", ",").replaceAll("\n\\s+\"Lumens\": \\d+,\n", "\n");
                            if (cache) {
                                String previous = p.getFileName() + "-false-.json";
                                String b = new String(Files.readAllBytes(Paths.get(previous)), StandardCharsets.UTF_8).replaceAll("\"QTime\": \\d+,", ",").replaceAll("\n\\s+\"Lumens\": \\d+,\n", "\n");
                                System.out.println("" + p + ":" + a.equals(b));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );
    }

    public static void main(String[] args) throws Exception {
        Configurator.setRootLevel(Level.INFO);
        query("/Users/shan/Downloads/debug-query-1.json", "loadtest", true, "http://localhost:8983/solr/");
    }
}
