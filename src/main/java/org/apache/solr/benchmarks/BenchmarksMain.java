package org.apache.solr.benchmarks;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.solr.benchmarks.beans.Cluster;
import org.apache.solr.benchmarks.beans.Configuration;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.beans.Repository;
import org.apache.solr.benchmarks.readers.JsonlFileType;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpClusterStateProvider;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.DocRouter;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.util.JsonRecordReader;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BenchmarksMain {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static String getSolrPackagePath(Repository repo, String solrPackageUrl) {
    	if (solrPackageUrl != null) {
    		String filename = solrPackageUrl.split("/")[solrPackageUrl.split("/").length-1];
    		if (new File(filename).exists() == false) {
    			throw new RuntimeException("File not found: "+filename+", was expecting the "+solrPackageUrl+" would've been downloaded.");
    		}
    		return filename; // this file is in current working dir
    	}
    	if (repo!=null) {
    		String filename = Util.DOWNLOAD_DIR + "solr-"+repo.commitId+".tgz";
    		if (new File(filename).exists() == false) {
    			throw new RuntimeException("File not found: "+filename+", was expecting the package with commit="+repo.commitId+" would've been built.");
    		}
    		return filename;
    	}
    	throw new RuntimeException("Solr package not found. Either specify 'repository' or 'solr-package' section in configuration");
    }
    
    public static void main(String[] args) throws Exception {

        Configurator.setRootLevel(Level.INFO);

        String configFile = args[0];

        Configuration config = new ObjectMapper().readValue(FileUtils.readFileToString(new File(configFile), "UTF-8"), Configuration.class);
        Cluster cluster = config.cluster;

        String solrPackagePath = getSolrPackagePath(config.repo, config.solrPackage);
        SolrCloud solrCloud = new SolrCloud(cluster, solrPackagePath);

        MetricsCollector metricsCollector = null;
        Thread metricsThread = null;
        
        try {
            solrCloud.init();
            log.info("SolrCloud initialized...");

            Map<String, Map> results = new LinkedHashMap<String, Map>();
            results.put("indexing-benchmarks", new LinkedHashMap<Map, List<Map>>());
            results.put("query-benchmarks", new LinkedHashMap<Map, List<Map>>());

            // Start metrics collection
            if (config.metrics != null) {
            	metricsCollector = new MetricsCollector(solrCloud.nodes, config.metrics, 2);
            	metricsThread = new Thread(metricsCollector);
            	metricsThread.start();
            	results.put("solr-metrics", metricsCollector.metrics);
            }

            // Indexing benchmarks
            log.info("Starting indexing benchmarks...");

            for (IndexBenchmark benchmark : config.indexBenchmarks) {
            	results.get("indexing-benchmarks").put(benchmark.name, new LinkedHashMap());
            	
                for (IndexBenchmark.Setup setup : benchmark.setups) {
                	List setupMetrics = new ArrayList();
                	((Map)(results.get("indexing-benchmarks").get(benchmark.name))).put(setup.name, setupMetrics);

                    for (int i = setup.minThreads; i <= setup.maxThreads; i += setup.threadStep) {
                        log.info("Creating collection: " + setup.collection);
                        try {
                            solrCloud.deleteCollection(setup.collection);
                        } catch (Exception ex) {
                            log.warn("Error trying to delete collection: " + ex);
                        }
                        solrCloud.uploadConfigSet(setup.configset);
                        solrCloud.createCollection(setup.collection, setup.configset, setup.shards, setup.replicationFactor);
                        long start = System.nanoTime();
                        index(solrCloud.nodes.get(0).getBaseUrl(), setup.collection, i, benchmark);
                        long end = System.nanoTime();

                        if (i != setup.maxThreads || config.queryBenchmarks.isEmpty()) {
                            solrCloud.deleteCollection(setup.collection);
                        }
                        
                        setupMetrics.add(Util.map("threads", i, "total-time", String.valueOf((end - start) / 1_000_000_000.0)));
                    }
                }
            }

            // Query benchmarks
            if (config.queryBenchmarks != null && config.queryBenchmarks.size() > 0)
                log.info("Starting querying benchmarks...");
            for (QueryBenchmark benchmark : config.queryBenchmarks) {
            	results.get("query-benchmarks").put(benchmark.name, new ArrayList());


                for (int threads = benchmark.minThreads; threads <= benchmark.maxThreads; threads++) {
                    QueryGenerator queryGenerator = new QueryGenerator(benchmark);

                    HttpSolrClient client = new HttpSolrClient.Builder(solrCloud.nodes.get(0).getBaseUrl()).build();
                    ControlledExecutor controlledExecutor = new ControlledExecutor(threads,
                            benchmark.duration,
                            benchmark.rpm,
                            benchmark.totalCount,
                            benchmark.warmCount,
                            getQuerySupplier(queryGenerator, client, benchmark.collection));
                    long start = System.currentTimeMillis();
                    try {
                        controlledExecutor.run();
                    } finally {
                        client.close();
                    }

                    long time = System.currentTimeMillis() - start;
                    System.out.println("Took time: " + time);
                    if (time > 0) {
                        System.out.println("Thread: " + threads + ", Median latency: " + controlledExecutor.stats.getPercentile(50) +
                                ", 95th latency: " + controlledExecutor.stats.getPercentile(95));
                        ((List)results.get("query-benchmarks").get(benchmark.name)).add(
                        		Util.map("threads", threads, "50th", controlledExecutor.stats.getPercentile(50), "90th", controlledExecutor.stats.getPercentile(90), 
                        				"95th", controlledExecutor.stats.getPercentile(95), "mean", controlledExecutor.stats.getMean(), "total-queries", controlledExecutor.stats.getN()));
                    }
                }
            }
            // Stop metrics collection
            if (config.metrics != null) {
            	metricsCollector.stop();
            }
            // Write results to a file
            results.put("configuration", Util.map("configuration", config));
            System.out.println("Final results: "+results);
            new ObjectMapper().writeValue(new File("results.json"), results);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            solrCloud.shutdown(true);
        }
    }

    private static Supplier<Runnable> getQuerySupplier(QueryGenerator queryGenerator, HttpSolrClient client, String collection) {
        return () -> {
            QueryRequest qr = queryGenerator.nextRequest();
            if (qr == null) return null;
            return () -> {
                try {
                    NamedList<Object> rsp = client.request(qr, collection);
                    printErrOutput(qr, rsp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
        };
    }

    private static void printErrOutput(QueryRequest qr, NamedList<Object> rsp) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (rsp.get("stream") == null) {
        	return;
        }
        IOUtils.copy((InputStream) rsp.get("stream"), baos);
        String errorout = new String(baos.toByteArray());
        if (!errorout.trim().startsWith("{")) {
            // it's not a JSON output, something must be wrong
            System.out.println("########### A query failed ");
            System.out.println("failed query " + qr.toString());
            System.out.println("Error response " + errorout);
        }
    }

    static void index(String baseUrl, String collection, int threads, IndexBenchmark benchmark) throws Exception {
    	if (benchmark.fileFormat.equalsIgnoreCase("json")) {
    		indexJson(baseUrl, collection, threads, benchmark);
    	} else if (benchmark.fileFormat.equalsIgnoreCase("tsv")) {
    		indexTSV(baseUrl, collection, threads, benchmark);
    	}
    }

    static void indexTSV(String baseUrl, String collection, int threads, IndexBenchmark benchmark) throws Exception {
        long start = System.currentTimeMillis();
        ConcurrentUpdateSolrClient client = new ConcurrentUpdateSolrClient.Builder(baseUrl).withThreadCount(threads).build();
        
        BufferedReader br;
        if (benchmark.datasetFile.endsWith("gz")) {
        	GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(benchmark.datasetFile));
        	br = new BufferedReader(new InputStreamReader(gzis));
        } else {
        	br = new BufferedReader(new FileReader(benchmark.datasetFile));
        }
        String line = br.readLine();
        List<String> headers = new ArrayList<String>(Arrays.asList(line.split("\\t")));
        for (int i=0; i<headers.size(); i++) if (headers.get(i).endsWith("#")) headers.remove(i--);
        System.out.println(headers);

        while ((line = br.readLine()) != null) {
        	if (line.trim().equals("")) continue; // ignore empty lines
        	String fields[] = line.split("\\t");
        	if (fields.length != headers.size()) throw new RuntimeException("Mismatch in field lengths against TSV header: " + line);
        	SolrInputDocument doc = new SolrInputDocument();
        	for (int i=0; i<fields.length; i++) {
        		doc.addField(headers.get(i), fields[i]);
        	}
        	if (!doc.containsKey(benchmark.idField)) doc.addField(benchmark.idField, UUID.randomUUID().toString());
        	client.add(collection, doc);
        }
        br.close();
        client.blockUntilFinished();
        client.commit(collection);
        client.close();        
    }
    
    static void indexJson(String baseUrl, String collection, int threads, IndexBenchmark benchmark) throws Exception {

        long start = System.currentTimeMillis();
        CloseableHttpClient httpClient = HttpClientUtil.createClient(null);

        final ExecutorService executor = Executors.newFixedThreadPool(threads);

        long count;
        AtomicInteger tasks = new AtomicInteger();

        try {
            HttpClusterStateProvider stateProvider = new HttpClusterStateProvider(Collections.singletonList(baseUrl), httpClient);
            DocCollection coll = stateProvider.getCollection(collection);

            DocRouter docRouter = coll.getRouter();
            Map<String, String> shardVsLeader = new HashMap<>();

            for (Slice slice : coll.getSlices()) {
                Replica leader = slice.getLeader();
                shardVsLeader.put(slice.getName(), leader.getBaseUrl() + "/" + leader.getCoreName() + "/update/json/docs");
            }
            JsonRecordReader rdr = JsonRecordReader.getInst("/", Collections.singletonList(benchmark.idField+":/"+benchmark.idField));
            Map<String, List<String>> shardVsDocs = new HashMap<>();
            File datasetFile = new File(benchmark.datasetFile);
            BufferedReader br = JsonlFileType.getBufferedReader(datasetFile);
            count = 0;


            String line;
            String[] id = new String[1];
            JsonRecordReader.Handler handler = (map, s) -> id[0] = (String) map.get(benchmark.idField);
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                rdr.streamRecords(new StringReader(line), handler);
                Slice targetSlice = docRouter.getTargetSlice(id[0], null, null, null, coll);
                List<String> docs = shardVsDocs.get(targetSlice.getName());
                if (docs == null) shardVsDocs.put(targetSlice.getName(), docs = new ArrayList<>(benchmark.batchSize));
                count++;
                if (count > benchmark.maxDocs) break;
                docs.add(line);
                if (docs.size() >= benchmark.batchSize) {
                    shardVsDocs.remove(targetSlice.getName());
                    executor.submit(new UploadDocs(docs, httpClient,
                            shardVsLeader.get(targetSlice.getName()),
                            tasks,
                            benchmark.reqTrace
                    ));
                }
            }
            br.close();
            shardVsDocs.forEach((shard, docs) -> executor.submit(new UploadDocs(docs,
                    httpClient,
                    shardVsLeader.get(shard), tasks, benchmark.reqTrace)));
        } finally {
            for (; ; ) {
                if (tasks.get() <= 0) break;
                Thread.sleep(10);
            }
            executor.shutdown();
            httpClient.close();
        }
	HttpSolrClient client = new HttpSolrClient.Builder(baseUrl).build();
	client.commit(collection);
	client.close();

        log.info("Indexed " + count + " docs." + "time taken : " + ((System.currentTimeMillis() - start) / 1000));
    }


    static class UploadDocs implements Runnable {

        final List<String> docs;
        final HttpClient client;
        final String leaderUrl;
        final AtomicInteger counter;
        private final boolean addReqId;
        private static final ReqIdInjector<HttpPost> ID_INJECTOR = (ReqIdInjector<HttpPost>) (carrier, key, value) -> {
            carrier.setHeader(key, value);
        };

        UploadDocs(List<String> docs, HttpClient client, String leaderUrl, AtomicInteger counter, boolean addReqId) {
            this.docs = docs;
            this.client = client;
            this.leaderUrl = leaderUrl;
            this.counter = counter;
            this.addReqId = addReqId;
            counter.incrementAndGet();
        }

        @Override
        public void run() {
            HttpPost httpPost = new HttpPost(leaderUrl);
            httpPost.setHeader(new BasicHeader("Content-Type", "application/json; charset=UTF-8"));
            if (addReqId) {
                ReqIdUtil.injectReqId(httpPost, ID_INJECTOR);
            }

            httpPost.setEntity(new BasicHttpEntity() {
                @Override
                public boolean isStreaming() {
                    return true;
                }

                @Override
                public void writeTo(OutputStream outstream) throws IOException {
                    OutputStreamWriter writer = new OutputStreamWriter(outstream);
                    for (String doc : docs) {
                        writer.append(doc).append('\n');
                    }
                    writer.flush();
                }
            });

            try {
                HttpResponse rsp = client.execute(httpPost);
                int statusCode = rsp.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    log.error("Failed a request: " +
                            rsp.getStatusLine() + " " + EntityUtils.toString(rsp.getEntity(), StandardCharsets.UTF_8));
                }

            } catch (IOException e) {
                log.error("Error in request to url : " + leaderUrl, e);
            } finally {
                counter.decrementAndGet();
            }

        }
    }
}
