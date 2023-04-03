package org.apache.solr.benchmarks;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.benchmarks.BenchmarksMain.QueryCallable;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.beans.SolrBenchQueryResponse;
import org.apache.solr.benchmarks.indexing.DocReader;
import org.apache.solr.benchmarks.indexing.FileDocReader;
import org.apache.solr.benchmarks.indexing.IndexBatchSupplier;
import org.apache.solr.benchmarks.readers.TarGzFileReader;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.benchmarks.solrcloud.SolrNode;
import org.apache.solr.benchmarks.validations.FileBasedQueryValidations;
import org.apache.solr.benchmarks.validations.Validations;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpClusterStateProvider;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            throws IOException, InterruptedException, ParseException, ExecutionException {
		if (queryBenchmarks != null && queryBenchmarks.size() > 0)
		    log.info("Starting querying benchmarks...");

		for (QueryBenchmark benchmark : queryBenchmarks) {
      log.info("Query Benchmark name: " + benchmark.name);
			results.get("query-benchmarks").put(benchmark.name, new ArrayList());
      List<SolrNode> queryNodes = solrCloud.queryNodes.isEmpty() ? solrCloud.nodes : solrCloud.queryNodes;
      String baseUrl = queryNodes.get(benchmark.queryNode-1).getBaseUrl();
      log.info("Query base URL " + baseUrl);
			for (int threads = benchmark.minThreads; threads <= benchmark.maxThreads; threads++) {
				ControlledExecutor.ExecutionListener<String, SolrBenchQueryResponse> listener = benchmark.detailedStats ? new DetailedQueryStatsListener() : new ValidationListener();
		        QueryGenerator queryGenerator = new QueryGenerator(benchmark);
		        HttpSolrClient client = new HttpSolrClient.Builder(baseUrl).build();
    			String collection = collectionNameOverride==null? benchmark.collection: collectionNameOverride;
    			ControlledExecutor<String, Long> controlledExecutor = new ControlledExecutor(
						benchmark.name,
						threads,
		                benchmark.durationSecs,
		                benchmark.rpm,
		                benchmark.totalCount,
		                benchmark.warmCount,
		                getQuerySupplier(queryGenerator, client, collectionNameOverride==null? benchmark.collection: collectionNameOverride),
						listener);
		        long start = System.currentTimeMillis();
		        try {
		            controlledExecutor.run();
		        } finally {
		            client.close();
		        }

		        Map<String, Number> validationResults = null;
		        if (listener instanceof ValidationListener) {
		        	try {
		        		validationResults = runQueryValidations(benchmark.validations, ((ValidationListener) listener).queryResponses, benchmark, collection, baseUrl);
		        	} catch (Exception e) {
		        		log.error("Problem during validations", e);
		        	}
		        }

		        long time = System.currentTimeMillis() - start;
		        System.out.println("Took time: " + time);
		        if (time > 0) {
    				Map<String, Number> taskResults = new LinkedHashMap<>();
                    taskResults.put("threads", threads);
                    taskResults.put("50th", controlledExecutor.stats.getPercentile(50));
                    taskResults.put("90th", controlledExecutor.stats.getPercentile(90));
                    taskResults.put("95th", controlledExecutor.stats.getPercentile(95));
                    taskResults.put("mean", controlledExecutor.stats.getMean());
                    taskResults.put("total-queries", controlledExecutor.stats.getN());
                    taskResults.put("total-time", time);
                    
					if (StressMain.validate && validationResults != null) taskResults.putAll(validationResults);

		            ((List)results.get("query-benchmarks").get(benchmark.name)).add(taskResults);
    				System.out.println("Query task results: " + taskResults);

					if (listener instanceof DetailedQueryStatsListener) {
						Map detailedStats = (Map) results.get("query-benchmarks").computeIfAbsent("detailed-stats", key -> new LinkedHashMap<>());
						//add the detailed stats (per query in the input query file) collected by the listener
						for (DetailedStats stats : ((DetailedQueryStatsListener) listener).getStats()) {
							String statsName = stats.getStatsName();
							List<Map> outputStats = (List<Map>)(detailedStats.computeIfAbsent(statsName, key -> new ArrayList<>()));
							stats.setExtraProperty("threads", threads);
							stats.setExtraProperty("total-time", time);
							outputStats.add(Util.map(stats.metricType.dataCategory, stats)); //forced by the design that this has to be a map, otherwise we shouldn't need to do this one entry map
						}
					}
		        }
		    }
		}
	}
	
	private static Map<String, Number> runQueryValidations(Validations validation, List<SolrBenchQueryResponse> actualQueryResponses,
			QueryBenchmark benchmark, String collection, String baseUrl) throws Exception {
		if (validation instanceof FileBasedQueryValidations) {
			((FileBasedQueryValidations) validation).init(actualQueryResponses, benchmark, collection, baseUrl);
		} else {
			throw new IllegalArgumentException("Validations type " + validation.type + " not supported for a query benchmarking task.");
		}

        if (StressMain.generateValidations) {
        	validation.generateValidationsData();
	        return null;
        } else if (StressMain.validate) {
        	validation.loadValidations();
        	return validation.doValidate();
        }
        return null;
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
		            	log.info("Creating collection: " + collectionName);
						boolean deleteCollectionOnStart;
						if (setup.deleteCollectionOnStart == null) {
							//to play safe for external mode, only delete the collection if it has been explicitly stated to do so
							deleteCollectionOnStart = solrCloud.getProvisioningMethod().equalsIgnoreCase("external") ? false : true;
						} else {
							deleteCollectionOnStart = setup.deleteCollectionOnStart;
						}
						if (deleteCollectionOnStart) {
							log.info("Attempt to delete existing collection: " + collectionName);
							try {
								solrCloud.deleteCollection(collectionName);
								log.info("Existing collection deleted");
							} catch (Exception ex) {
								if (ex instanceof SolrException && ((SolrException) ex).code() == ErrorCode.NOT_FOUND.code) {
									log.info("No existing collection to delete");
								} else {
									//log.warn("Error trying to delete collection: " + ex);
								}
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

	public static class QueryCallable implements ControlledExecutor.CallableWithType<String, SolrBenchQueryResponse> {
		private final QueryRequest queryRequest;
		private final String queryString;
		private final HttpSolrClient client;
		private final String collection;
		
		public QueryCallable(QueryRequest queryRequest, String queryString, HttpSolrClient client, String collection) {
			this.queryRequest = queryRequest;
			this.client = client;
			this.collection = collection;
			this.queryString = queryString;
		}
		@Override
		public String getType() {
			return queryRequest.toString();
		}

		public QueryRequest getQueryRequest() {
			return queryRequest;
		}

		public String getQueryString() {
			return queryString;
		}

		@Override
		public SolrBenchQueryResponse call() throws Exception {
			NamedList<Object> rsp = client.request(queryRequest, collection);
			//let's not do printErrOutput here as this reads the input stream and once read it cannot be read anymore
			//Probably better to let the caller handle the return values instead
			//printErrOutput(queryRequest, rsp);
			return new SolrBenchQueryResponse(queryString, rsp);
		}
	}
	

    public static Supplier<ControlledExecutor.CallableWithType<String, SolrBenchQueryResponse>> getQuerySupplier(QueryGenerator queryGenerator, HttpSolrClient client, String collection) {
        return () -> {
            Pair<String, QueryRequest> req = queryGenerator.nextRequest();
        	QueryRequest qr = req.second();
        	String queryString = req.first();
            if (qr == null) return null;
            return new QueryCallable(qr, queryString, client, collection);
        };
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

	/**
	 * A Query specific stats listener that accumulates stats for:
	 * <ol>
	 *     <li>Duration of query execution (percentile)</li>
	 *     <li>Document hit count of the query (percentile)</li>
	 *     <li>Error count on the query (long)</li>
	 * </ol>
	 */
	private static class DetailedQueryStatsListener implements ControlledExecutor.ExecutionListener<String, SolrBenchQueryResponse> {
		private final ConcurrentMap<String, SynchronizedDescriptiveStatistics> durationStatsByType = new ConcurrentHashMap<>();
		private final ConcurrentMap<String, SynchronizedDescriptiveStatistics> docHitCountStatsByType = new ConcurrentHashMap<>();
		private final ConcurrentMap<String, AtomicLong> errorCountStatsByType= new ConcurrentHashMap<>();
		private final AtomicBoolean loggedQueryRspError = new AtomicBoolean(false);
		private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
		@Override
		public void onExecutionComplete(String typeKey, SolrBenchQueryResponse sbq, long duration) {
			if (typeKey != null) {
				SynchronizedDescriptiveStatistics durationStats = durationStatsByType.computeIfAbsent(typeKey, (key) -> new SynchronizedDescriptiveStatistics());
				durationStats.addValue(duration / 1_000_000.0);
				if (sbq.responseString != null) {

			return results;
		}
		private enum StatsMetricType {
			DURATION("timings"), DOC_HIT_COUNT("percentile"), ERROR_COUNT("error_count");
			private final String dataCategory;

					if (sbq.isSuccessfulResponse) {
						SynchronizedDescriptiveStatistics hitCountStats = docHitCountStatsByType.computeIfAbsent(typeKey, (key) -> new SynchronizedDescriptiveStatistics());
						int hitCount = getHitCount(sbq.responseString);
						if (hitCount != -1) {
							hitCountStats.addValue(hitCount);
						}
					} else {
						AtomicLong errorCount = errorCountStatsByType.computeIfAbsent(typeKey, (key) -> new AtomicLong(0));
						errorCount.incrementAndGet();
						//this could be noisy
						if (!loggedQueryRspError.getAndSet(true) || logger.isDebugEnabled()) {
							logger.warn("Non successful response. The response stream is " + sbq.responseString + " And the full rsp list " + sbq.rawResponse);
						}
					}
				}
			}
		}

		private int getHitCount(String response)  {
			Map<String, Object> jsonResponse;
			try {
				jsonResponse = new ObjectMapper().readValue(response, Map.class);
			} catch (JsonProcessingException e) {
				logger.warn("Failed to json parse the response stream " + response);
				return -1;
			}

			if (jsonResponse.containsKey("response")) {
				return (int)((Map<String, Object>) jsonResponse.get("response")).get("numFound");
			} else {
				logger.warn("The json response stream does not have key `response`. The json response stream : " + jsonResponse);
				return -1;
			}
		}

		public List<DetailedStats> getStats() {
			List<DetailedStats> results  = new ArrayList<>();
			durationStatsByType.forEach( (key, stats) -> results.add(new DetailedStats(StatsMetricType.DURATION, key, stats)));
			docHitCountStatsByType.forEach( (key, stats) -> results.add(new DetailedStats(StatsMetricType.DOC_HIT_COUNT, key, stats)));
			errorCountStatsByType.forEach( (key, stats) -> results.add(new DetailedStats(StatsMetricType.ERROR_COUNT, key, stats)));

			return results;
		}
		private enum StatsMetricType {
			DURATION("timings"), DOC_HIT_COUNT("percentile"), ERROR_COUNT("error_count");
			private final String dataCategory;

			StatsMetricType(String dataCategory) {
				this.dataCategory = dataCategory;
			}
			
		}
	}

	public static class DetailedStats {
		private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
		private final DetailedQueryStatsListener.StatsMetricType metricType;
		private final Object statsObj;
		private final String queryType;
		private final Map<String, Object> extraProperties = new HashMap<>();

		private DetailedStats(DetailedQueryStatsListener.StatsMetricType metricType, String queryType, Object stats) {
			this.metricType = metricType;
			this.queryType = queryType;
			this.statsObj = stats;
		}

		private String getStatsName(){
			return "(" + metricType + ") " + queryType;
		}

		public String getQueryType() {
			return queryType;
		}

		public DetailedQueryStatsListener.StatsMetricType getMetricType() {
			return metricType;
		}

		private void setExtraProperty(String key, Object value) {
			extraProperties.put(key, value);
		}

		public Map values() {
			Map resultMap;
			if (statsObj instanceof SynchronizedDescriptiveStatistics) {
				SynchronizedDescriptiveStatistics stats = (SynchronizedDescriptiveStatistics) statsObj;
				resultMap = Util.map("50th", stats.getPercentile(50), "90th", stats.getPercentile(90),
						"95th", stats.getPercentile(95), "mean", stats.getMean(), "total-queries", stats.getN());
			} else if (statsObj instanceof Number) {
				resultMap = Util.map("count", ((Number)statsObj).doubleValue());
			} else {
				logger.warn("Unexpected stats type " + statsObj.getClass());
				return null;
			}
			resultMap.putAll(extraProperties);
			return resultMap;
		}
	}

	private static class ValidationListener implements ControlledExecutor.ExecutionListener<String, SolrBenchQueryResponse> {

		public List<SolrBenchQueryResponse> queryResponses = new Vector<>();
		
		@Override
		public void onExecutionComplete(String typeKey, SolrBenchQueryResponse result, long duration) {
			queryResponses.add(result);
			try {
				printErrOutput(typeKey, result);
			} catch (IOException e) {
				log.warn("Failed to invoke printErrOutput");
			}
		}
		
	    private static void printErrOutput(String qr, SolrBenchQueryResponse sbq) throws IOException {
	        if (!sbq.isSuccessfulResponse || !sbq.responseString.trim().startsWith("{")) {
	            // it's not a JSON output, something must be wrong
	            System.out.println("########### A query failed ");
	            System.out.println("failed query " + qr.toString());
	            System.out.println("Error response " + sbq.responseString);
	        }
	    }
	}
	
}
