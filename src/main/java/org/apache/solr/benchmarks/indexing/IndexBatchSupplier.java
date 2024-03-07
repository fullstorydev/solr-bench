package org.apache.solr.benchmarks.indexing;

import org.apache.http.client.HttpClient;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.util.JsonRecordReader;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class IndexBatchSupplier implements Supplier<Callable<IndexResult>>, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final Future<?> workerFuture;
  private volatile boolean exit;
  private IndexBenchmark benchmark;
  private DocCollection docCollection;
  private HttpClient httpClient;
  private final LeaderUrlProvider leaderUrlProvider;
  private BlockingQueue<Callable<IndexResult>> pendingBatches = new LinkedBlockingQueue<>(10); //at most 10 pending batches
  private final boolean init;
  private AtomicLong batchesIndexed = new AtomicLong();

  public IndexBatchSupplier(boolean init, DocReader docReader, IndexBenchmark benchmark, DocCollection docCollection, HttpClient httpClient, LeaderUrlProvider leaderUrlProvider) {
    this.benchmark = benchmark;
    this.docCollection = docCollection;
    this.init = init;
    this.httpClient = httpClient;
    this.leaderUrlProvider = leaderUrlProvider;


    this.workerFuture = startWorker(docReader);
  }

  class IdParser implements JsonRecordReader.Handler {
    String idParsed;

    @Override
    public void handle(Map<String, Object> record, String path) {
      idParsed = record.get(benchmark.idField) instanceof String ?
              (String) record.get(benchmark.idField) :
              record.get(benchmark.idField).toString();
    }
  }

  private Future<?> startWorker(DocReader docReader) {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    //a continuously running job until either all input is exhausted or exit is flagged
    Future<?> workerFuture = executorService.submit(() -> {
      List<String> inputDocs;
      JsonRecordReader rdr = JsonRecordReader.getInst("/", Collections.singletonList(benchmark.idField + ":/" + benchmark.idField));
      IdParser idParser = new IdParser();
      Map<String, List<String>> shardVsDocs = new HashMap<>();
      Map<String, AtomicInteger> batchCounters = new ConcurrentHashMap<>();
      try {
        while (!exit && (inputDocs = docReader.readDocs(benchmark.batchSize)) != null) { //can read more than batch size, just use batch size as a sensible value
          for (String inputDoc : inputDocs) {
            rdr.streamRecords(new StringReader(inputDoc), idParser);
            Slice targetSlice = docCollection.getRouter().getTargetSlice(idParser.idParsed, null, null, null, docCollection);
            List<String> shardDocs = shardVsDocs.computeIfAbsent(targetSlice.getName(), key -> new ArrayList<>(benchmark.batchSize));
            shardDocs.add(inputDoc);
            if (shardDocs.size() >= benchmark.batchSize) {
              shardVsDocs.remove(targetSlice.getName());
              String batchFilename = computeBatchFilename(benchmark, batchCounters, targetSlice.getName());
              //a shard has accumulated enough docs to be executed
              Callable<IndexResult> docsBatchCallable = init ? new PrepareRawBinaryFiles(benchmark, batchFilename, shardDocs, leaderUrlProvider.findLeaderUrl(targetSlice.getName())) :
                      new UploadDocs(benchmark, batchFilename, shardDocs, httpClient, targetSlice.getName(), leaderUrlProvider, batchesIndexed);
              while (!exit && !pendingBatches.offer(docsBatchCallable, 1, TimeUnit.SECONDS)) {
                //try again
              }
            }
          }
        }
        shardVsDocs.forEach((shard, docs) -> { //flush the remaining ones
          try {
            String batchFilename = computeBatchFilename(benchmark, batchCounters, shard);
            Callable<IndexResult> docsBatchCallable = init ? new PrepareRawBinaryFiles(benchmark, batchFilename, docs, leaderUrlProvider.findLeaderUrl(shard)) :
                    new UploadDocs(benchmark, batchFilename, docs, httpClient, shard, leaderUrlProvider, batchesIndexed);
            while (!exit && !pendingBatches.offer(docsBatchCallable, 1, TimeUnit.SECONDS)) {
              //try again
            }
          } catch (InterruptedException e) {
            log.warn(e.getMessage(), e);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      } catch (IOException e) {
        log.warn("IO Exception while reading input docs " + e.getMessage(), e);
      } finally {
        exit = true;
      }
      return null;
    });

    executorService.shutdown();
    return workerFuture;
  }

  private String computeBatchFilename(IndexBenchmark benchmark, Map<String, AtomicInteger> batchCounters, String shard) {
    if (benchmark.prepareBinaryFormat == null) return null;
    String batchFilename = null;
    String tmpDir = "tmp/" + benchmark.name;
    try {
      FileUtils.mkdirs(new File(tmpDir), true);
    } catch (IOException e) {
      log.error("Unable to create directory: " + tmpDir);
      throw new RuntimeException("Unable to create directory " + tmpDir, e);
    }
    AtomicInteger batchCounter = batchCounters.get(shard);
    if (batchCounter == null) batchCounter = new AtomicInteger(0);
    batchCounter.incrementAndGet();
    batchCounters.put(shard, batchCounter);

    batchFilename = docCollection.getName() + "_" + shard.replace(':', '_').replace('/', '_') + "_batch" + batchCounter.get() + "." + benchmark.prepareBinaryFormat;
    return tmpDir + "/" + batchFilename;
  }

  @Override
  public Callable<IndexResult> get() {
    try {
      Callable<IndexResult> batch = null;
      while ((batch = pendingBatches.poll(1, TimeUnit.SECONDS)) == null && !exit) {
      }
      if (batch == null) { //rare race condition can fill the queue even if above loop exits, just try it once last time...
        batch = pendingBatches.poll();
      }

      if (batch != null) {
        Callable<IndexResult> finalBatch = batch;
        return new ControlledExecutor.CallableWithType<>() { //wrap it so listener can report metrics on it with extra info
          @Override
          public IndexResult call() throws Exception {
            return finalBatch.call();
          }

          @Override
          public BenchmarksMain.OperationKey getType() {
            return new BenchmarksMain.OperationKey("POST", "/update", Collections.EMPTY_MAP); //tricky to extract values from batch, let's just hard-code this for now and it should be correct
          }
        };
      } else {
        return null;
      }
    } catch (InterruptedException e) {
      log.warn("Cannot get the pending batches " + e.getMessage(), e);
      return null;
    }
  }

  @Override
  public void close() throws Exception {
    exit = true;
    workerFuture.get(); //this could throw exception if there are any unhandled exceptions in UploadDocs execution
  }

  public long getBatchesIndexed() {
    return batchesIndexed.get();
  }
}

