package org.apache.solr.benchmarks;

import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.indexing.DocReader;
import org.apache.solr.benchmarks.indexing.FileDocReader;
import org.apache.solr.benchmarks.indexing.IndexBatchSupplier;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.benchmarks.solrcloud.SolrNode;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpClusterStateProvider;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

public class BenchmarksMain {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // nocommit: this should goto Utils
    public static String getSolrPackagePath(String commit, String solrPackageUrl) {
    	if (solrPackageUrl != null) {
    		String filename = solrPackageUrl.split("/")[solrPackageUrl.split("/").length-1];
    		if (new File(filename).exists() == false) {
    			throw new RuntimeException("File not found: "+filename+", was expecting the "+solrPackageUrl+" would've been downloaded.");
    		}
    		return filename; // this file is in current working dir
    	}
    	if (commit != null) {
    		String filename = Util.DOWNLOAD_DIR + "solr-"+commit+".tgz";
    		if (new File(filename).exists() == false) {
    			throw new RuntimeException("File not found: "+filename+", was expecting the package with commit=" + commit + " would've been built.");
    		}
    		return filename;
    	}
    	throw new RuntimeException("Solr package not found. Either specify 'repository' or 'solr-package' section in configuration");
    }

	public static void runQueryBenchmarks(List<QueryBenchmark> queryBenchmarks, String collectionNameOverride, SolrCloud solrCloud, Map<String, Map> results)
            throws IOException, InterruptedException, ParseException {
		if (queryBenchmarks != null && queryBenchmarks.size() > 0)
		    log.info("Starting querying benchmarks...");

		for (QueryBenchmark benchmark : queryBenchmarks) {
      log.info("Query Benchmark name: " + benchmark.name);
			results.get("query-benchmarks").put(benchmark.name, new ArrayList());
      List<SolrNode> queryNodes = solrCloud.queryNodes.isEmpty() ? solrCloud.nodes : solrCloud.queryNodes;
      String baseUrl = queryNodes.get(benchmark.queryNode-1).getBaseUrl();
      log.info("Query base URL " + baseUrl);

		    for (int threads = benchmark.minThreads; threads <= benchmark.maxThreads; threads++) {
		        QueryGenerator queryGenerator = new QueryGenerator(benchmark);
		        HttpSolrClient client = new HttpSolrClient.Builder(baseUrl).build();
		        ControlledExecutor controlledExecutor = new ControlledExecutor(
						benchmark.name,
						threads,
		                benchmark.durationSecs,
		                benchmark.rpm,
		                benchmark.totalCount,
		                benchmark.warmCount,
		                getQuerySupplier(queryGenerator, client, collectionNameOverride==null? benchmark.collection: collectionNameOverride));
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
		            				"95th", controlledExecutor.stats.getPercentile(95), "mean", controlledExecutor.stats.getMean(), "total-queries", controlledExecutor.stats.getN(), "total-time", time));
		        }
		    }
		}
	}

	public static void runIndexingBenchmarks(List<IndexBenchmark> indexBenchmarks, SolrCloud solrCloud, Map<String, Map> results) throws Exception {
		runIndexingBenchmarks(indexBenchmarks, null, true, solrCloud, results);
	}
	public static void runIndexingBenchmarks(List<IndexBenchmark> indexBenchmarks, String collectionNameOverride, boolean deleteAfter, SolrCloud solrCloud, Map<String, Map> results)
			throws Exception {
		for (IndexBenchmark benchmark : indexBenchmarks) {
			results.get("indexing-benchmarks").put(benchmark.name, new LinkedHashMap());
			
		    for (IndexBenchmark.Setup setup : benchmark.setups) {
		    	List setupMetrics = new ArrayList();
		    	((Map)(results.get("indexing-benchmarks").get(benchmark.name))).put(setup.name, setupMetrics);

		        for (int i = benchmark.minThreads; i <= benchmark.maxThreads; i += setup.threadStep) {
		            String collectionName = collectionNameOverride != null ? collectionNameOverride: setup.collection;
		            String configsetName = setup.configset==null? null: collectionName+".SOLRBENCH";
		            if (setup.shareConfigset) configsetName = setup.configset;

		            if (setup.createCollection) {
		            	log.info("Creating collection1: " + collectionName);
		            	try {
                            solrCloud.deleteCollection(collectionName);
		            	} catch (Exception ex) {
		            		if (ex instanceof SolrException && ((SolrException)ex).code() ==  ErrorCode.NOT_FOUND.code) {
		            			//log.debug("Error trying to delete collection: " + ex);
		            		} else {
		            			//log.warn("Error trying to delete collection: " + ex);
		            		}
		            	}
                        if (solrCloud.shouldUploadConfigSet()) {
                            solrCloud.uploadConfigSet(setup.configset, setup.shareConfigset, configsetName);
                        }
		            	solrCloud.createCollection(setup, collectionName, configsetName);
		            }
		            long start = System.nanoTime();
		            index(solrCloud.nodes.get(0).getBaseUrl(), collectionName, i, setup, benchmark);
		            long end = System.nanoTime();

		            if (i != benchmark.maxThreads && setup.createCollection) {
		            	if (deleteAfter) {
		            		solrCloud.deleteCollection(collectionName);
		            	}
		            }
		            
		            setupMetrics.add(Util.map("threads", i, "total-time", String.valueOf((end - start) / 1_000_000_000.0)));
		        }
		    }
		}
	}

    private static Supplier<Callable> getQuerySupplier(QueryGenerator queryGenerator, HttpSolrClient client, String collection) {
        return () -> {
            QueryRequest qr = queryGenerator.nextRequest();
            if (qr == null) return null;
            return () -> {
                NamedList<Object> rsp = client.request(qr, collection);
                printErrOutput(qr, rsp);
                return null;
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

    static void index(String baseUrl, String collection, int threads, IndexBenchmark.Setup setup, IndexBenchmark benchmark) throws Exception {
    	if (benchmark.fileFormat.equalsIgnoreCase("json")) {
    		indexJsonComplex(baseUrl, collection, threads, setup, benchmark);
    	} else if (benchmark.fileFormat.equalsIgnoreCase("tsv")) {
    		indexTSV(baseUrl, collection, threads, setup, benchmark);
    	}
    }

    static void indexTSV(String baseUrl, String collection, int threads, IndexBenchmark.Setup setup, IndexBenchmark benchmark) throws Exception {
        long start = System.currentTimeMillis();
        ConcurrentUpdateSolrClient client = new ConcurrentUpdateSolrClient.Builder(baseUrl).withThreadCount(threads).build();
        
        BufferedReader br;
        if (benchmark.datasetFile.endsWith("gz")) {
        	GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(Util.resolveSuitePath(benchmark.datasetFile)));
        	br = new BufferedReader(new InputStreamReader(gzis));
        } else {
        	br = new BufferedReader(new FileReader(Util.resolveSuitePath(benchmark.datasetFile)));
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

    static void indexJsonComplex(String baseUrl, String collection, int threads, IndexBenchmark.Setup setup, IndexBenchmark benchmark) throws Exception {

        long start = System.currentTimeMillis();
        CloseableHttpClient httpClient = HttpClientUtil.createClient(null);

        try {
            HttpClusterStateProvider stateProvider = new HttpClusterStateProvider(Collections.singletonList(baseUrl), httpClient);
            DocCollection coll = stateProvider.getCollection(collection);

            Map<String, String> shardVsLeader = new HashMap<>();

            for (Slice slice : coll.getSlices()) {
                Replica leader = slice.getLeader();
                shardVsLeader.put(slice.getName(), leader.getBaseUrl() + "/" + leader.getCoreName() + "/update/json/docs");
            }
            File datasetFile = Util.resolveSuitePath(benchmark.datasetFile);
            try (DocReader docReader = new FileDocReader(datasetFile, benchmark.maxDocs != null ? benchmark.maxDocs.longValue() : null, benchmark.offset)) {
              try (IndexBatchSupplier indexBatchSupplier = new IndexBatchSupplier(docReader, benchmark, coll, httpClient, shardVsLeader)) {
                ControlledExecutor controlledExecutor = new ControlledExecutor(
						benchmark.name,
						threads,
                        benchmark.durationSecs,
                        benchmark.rpm,
                        null, //total is controlled by docReader's maxDocs
                        0,
                        indexBatchSupplier);
                controlledExecutor.run();
                HttpSolrClient client = new HttpSolrClient.Builder(baseUrl).build();
                client.commit(collection);
                client.close();

                log.info("Indexed " + indexBatchSupplier.getDocsIndexed() + " docs." + "time taken : " + ((System.currentTimeMillis() - start) / 1000));
              }
            }
        } finally {
            httpClient.close();
        }
    }
}
