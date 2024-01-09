package org.apache.solr.benchmarks;

import io.prometheus.client.Histogram;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.benchmarks.beans.QueryBenchmark;
import org.apache.solr.benchmarks.prometheus.PrometheusExportManager;
import org.apache.solr.benchmarks.indexing.DocReader;
import org.apache.solr.benchmarks.indexing.FileDocReader;
import org.apache.solr.benchmarks.indexing.IndexBatchSupplier;
import org.apache.solr.benchmarks.query.DetailedQueryStatsListener;
import org.apache.solr.benchmarks.query.DetailedStats;
import org.apache.solr.benchmarks.query.QueryResponseContents;
import org.apache.solr.benchmarks.query.QueryResponseContentsListener;
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
import java.util.concurrent.ExecutionException;
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
            throws IOException, InterruptedException, ParseException, ExecutionException {
		if (queryBenchmarks != null && queryBenchmarks.size() > 0)
		    log.info("Starting querying benchmarks...");


		for (QueryBenchmark benchmark : queryBenchmarks) {
			String collection = collectionNameOverride == null ? benchmark.collection: collectionNameOverride;
      log.info("Query Benchmark name: " + benchmark.name);
			results.get("query-benchmarks").put(benchmark.name, new ArrayList());
      List<? extends SolrNode> queryNodes = solrCloud.getNodesByRole(SolrCloud.NodeRole.COORDINATOR);
      String baseUrl = queryNodes.get(benchmark.queryNode-1).getBaseUrl();
      log.info("Query base URL " + baseUrl);

			List<ControlledExecutor.ExecutionListener<BenchmarksMain.OperationKey, QueryResponseContents>> listeners = new ArrayList<>();
			DetailedQueryStatsListener detailedQueryStatsListener = null;

			if (benchmark.detailedStats) {  //add either DetailedQueryStatsListener or ErrorListener
				detailedQueryStatsListener = new DetailedQueryStatsListener();
				listeners.add(detailedQueryStatsListener);
			} else {
				listeners.add(new ErrorListener());
			}
			if (PrometheusExportManager.isEnabled()) {
				log.info("Adding Prometheus listener for query benchmark [" + benchmark.name + "]");
				listeners.add(new PrometheusHttpRequestDurationListener<>(benchmark.prometheusTypeLabel, collection));
			}

			for (int threads = benchmark.minThreads; threads <= benchmark.maxThreads; threads++) {
				QueryGenerator queryGenerator = new QueryGenerator(benchmark);
				HttpSolrClient client = new HttpSolrClient.Builder(baseUrl).build();
				ControlledExecutor<QueryResponseContents> controlledExecutor = new ControlledExecutor(
					benchmark.name,
					threads,
					benchmark.durationSecs,
					benchmark.rpm,
					benchmark.totalCount,
					benchmark.warmCount,
					getQuerySupplier(queryGenerator, client, collection),
					listeners.toArray(new ControlledExecutor.ExecutionListener[listeners.size()]));
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
					if (detailedQueryStatsListener != null) {
						Map detailedStats = (Map) results.get("query-benchmarks").computeIfAbsent("detailed-stats", key -> new LinkedHashMap<>());
						//add the detailed stats (per query in the input query file) collected by the listener
						for (DetailedStats stats : detailedQueryStatsListener.getStats()) {
							String statsName = stats.getStatsName();
							List<Map> outputStats = (List<Map>)(detailedStats.computeIfAbsent(statsName, key -> new ArrayList<>()));
							stats.setExtraProperty("threads", threads);
							stats.setExtraProperty("total-time", time);
							outputStats.add(Util.map(stats.getMetricType().getDataCategory(), stats)); //forced by the design that this has to be a map, otherwise we shouldn't need to do this one entry map
						}
					}
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

					indexInit(solrCloud.nodes.get(0).getBaseUrl(), collectionName, i, setup, benchmark);
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

  private static Supplier<ControlledExecutor.CallableWithType<QueryResponseContents>> getQuerySupplier(QueryGenerator queryGenerator, HttpSolrClient client, String collection) {
    class QueryCallable implements ControlledExecutor.CallableWithType<QueryResponseContents> {
      private final QueryRequest queryRequest;

      private QueryCallable(QueryRequest queryRequest) {
        this.queryRequest = queryRequest;
      }
      @Override
      public OperationKey getType() {
        return new OperationKey(queryRequest.getMethod().name(), queryRequest.getPath(), Map.of("query", queryRequest.toString()));
      }

      @Override
      public QueryResponseContents call() throws Exception {
        NamedList<Object> rsp = client.request(queryRequest, collection);
        //let's not do printErrOutput here as this reads the input stream and once read it cannot be read anymore
        //Probably better to let the caller handle the return values instead
        //printErrOutput(queryRequest, rsp);
        return new QueryResponseContents(rsp);
      }
    }
    return () -> {
      QueryRequest qr = queryGenerator.nextRequest();
      if (qr == null) return null;
      return new QueryCallable(qr);
    };
  }

    private static void printErrOutput(String qr, String responseStreamString) throws IOException {
        if (!responseStreamString.trim().startsWith("{")) {
            // it's not a JSON output, something must be wrong
            System.out.println("########### A query failed ");
            System.out.println("failed query " + qr.toString());
            System.out.println("Error response " + responseStreamString);
        }
    }

    public static void indexInit(String baseUrl, String collection, int threads, IndexBenchmark.Setup setup, IndexBenchmark benchmark) throws Exception {
    	index(true, baseUrl, collection, threads, setup, benchmark);
    }
    public static void index(String baseUrl, String collection, int threads, IndexBenchmark.Setup setup, IndexBenchmark benchmark) throws Exception {
    	index(false, baseUrl, collection, threads, setup, benchmark);
    }
    private static void index(boolean init, String baseUrl, String collection, int threads, IndexBenchmark.Setup setup, IndexBenchmark benchmark) throws Exception {
    	if (benchmark.fileFormat.equalsIgnoreCase("json")) {
    		indexJsonComplex(init, baseUrl, collection, threads, setup, benchmark);
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

    static boolean isInitPhaseNeeded(IndexBenchmark benchmark) {
    	if (benchmark.prepareBinaryFormat != null) {
    		// We need an init phase to prepare the raw binary batch files to index in the final stage
    		return true;
    	} else return false;
    }
    static void indexJsonComplex(boolean init, String baseUrl, String collection, int threads, IndexBenchmark.Setup setup, IndexBenchmark benchmark) throws Exception {
      if (init && !isInitPhaseNeeded(benchmark)) return; // no-op

        long start = System.currentTimeMillis();
        CloseableHttpClient httpClient = HttpClientUtil.createClient(null);

        try {
          HttpClusterStateProvider stateProvider = new HttpClusterStateProvider(Collections.singletonList(baseUrl), httpClient);
          DocCollection coll = stateProvider.getCollection(collection);

          Map<String, String> shardVsLeader = new HashMap<>();

          for (Slice slice : coll.getSlices()) {
              Replica leader = slice.getLeader();
              shardVsLeader.put(slice.getName(), leader.getBaseUrl() + "/" + leader.getCoreName());
          }
          File datasetFile = Util.resolveSuitePath(benchmark.datasetFile);
          try (DocReader docReader = new FileDocReader(datasetFile, benchmark.maxDocs != null ? benchmark.maxDocs.longValue() : null, benchmark.offset)) {
            try (IndexBatchSupplier indexBatchSupplier = new IndexBatchSupplier(init, docReader, benchmark, coll, httpClient, shardVsLeader)) {
              ControlledExecutor.ExecutionListener[] listeners;
              if (PrometheusExportManager.isEnabled()) {
								log.info("Adding Prometheus listener for index benchmark [" + benchmark.name + "]");
                listeners = new ControlledExecutor.ExecutionListener[]{ new PrometheusHttpRequestDurationListener(null, collection) }; //no type label override for indexing
              } else {
                listeners = new ControlledExecutor.ExecutionListener[0];
              }

              ControlledExecutor controlledExecutor = new ControlledExecutor(
                benchmark.name,
                threads,
                benchmark.durationSecs,
                benchmark.rpm,
                null, //total is controlled by docReader's maxDocs
                0,
                indexBatchSupplier,
                listeners);
              controlledExecutor.run();
              HttpSolrClient client = new HttpSolrClient.Builder(baseUrl).build();
              client.commit(collection);
              client.close();

              log.info("Indexed " + indexBatchSupplier.getBatchesIndexed() + " docs." + "time taken : " + ((System.currentTimeMillis() - start) / 1000));
            }
          }
        } finally {
            httpClient.close();
        }
    }

	private static class ErrorListener implements QueryResponseContentsListener {

		@Override
		public void onExecutionComplete(OperationKey key, QueryResponseContents result, long duration) {
			try {
				printErrOutput((String) key.attributes.get("query"), result.getResponseStreamAsString());
			} catch (IOException e) {
				log.warn("Failed to invoke printErrOutput");
			}
		}
	}

	private static class PrometheusHttpRequestDurationListener<R> implements ControlledExecutor.ExecutionListener<BenchmarksMain.OperationKey, R> {
		private final String typeLabel;
		private final Histogram histogram;
		private static final String zkHost = BenchmarkContext.getContext().getZkHost();
		private static final String testSuite = BenchmarkContext.getContext().getTestSuite();
		private final String collection;

		PrometheusHttpRequestDurationListener(String typeLabelOverride, String collection) {
			this.histogram = PrometheusExportManager.registerHistogram("solr_bench_duration", "duration taken to execute a Solr indexing/query", "method", "path", "type", "collection", "zk_host", "test_suite");
			this.typeLabel = typeLabelOverride != null ? typeLabelOverride : PrometheusExportManager.globalTypeLabel;
			this.collection = collection;
		}

		@Override
		public void onExecutionComplete(OperationKey key, R result, long durationInNanosecond) {
			String[] labels = new String[] { key.getHttpMethod(), key.getPath(), typeLabel, collection, zkHost, testSuite };
			histogram.labels(labels).observe(durationInNanosecond / 1_000_000);
		}
	}

	public static class OperationKey {
		private final String httpMethod;
		private final String path;
		private final Map<String, Object> attributes;

		public OperationKey(String httpMethod, String path, Map<String, Object> attributes) {
			this.httpMethod = httpMethod;
			this.path = path;
			this.attributes = attributes;
		}

		public String getHttpMethod() {
			return httpMethod;
		}

		public String getPath() {
			return path;
		}

		public Map<String, Object> getAttributes() {
			return attributes;
		}
	}
}
