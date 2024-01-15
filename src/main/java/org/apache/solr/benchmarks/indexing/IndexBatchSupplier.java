package org.apache.solr.benchmarks.indexing;

import org.apache.http.client.HttpClient;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.util.JsonRecordReader;
import org.eclipse.jgit.util.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class IndexBatchSupplier implements Supplier<Callable>, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final Future<?> workerFuture;
  private volatile boolean exit;
  private IndexBenchmark benchmark;
  private DocCollection docCollection;
  private HttpClient httpClient;
  private Map<String, String> shardVsLeader;
  private BlockingQueue<Callable> pendingBatches = new LinkedBlockingQueue<>(10); //at most 10 pending batches
  private final boolean init;
  private AtomicLong batchesIndexed = new AtomicLong();

  public IndexBatchSupplier(boolean init, DocReader docReader, IndexBenchmark benchmark, DocCollection docCollection, HttpClient httpClient, Map<String, String> shardVsLeader) {
    this.benchmark = benchmark;
    this.docCollection = docCollection;
    this.init = init;
    this.httpClient = httpClient;
    this.shardVsLeader = shardVsLeader;

    this.workerFuture = startWorker(docReader);
  }

  static class KeyValueParser implements JsonRecordReader.Handler {
    private final Map<String, Object> keyValue = new HashMap<>();
    private final String[] keys;

    KeyValueParser(String... keys) {
      this.keys = keys;
    }


    @Override
    public void handle(Map<String, Object> record, String path) {
      for (String key : keys) {
        Object value = record.get(key);
        if (value != null) {
          keyValue.put(key, value);
        } else {
          keyValue.remove(key);
        }
      }
    }

    public Object value(String key) {
      return keyValue.get(key);
    }
  }

  private Future<?> startWorker(DocReader docReader) {
//    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    //a continuously running job until either all input is exhausted or exit is flagged
    Future<?> workerFuture = executorService.submit(() -> {
      List<String> inputDocs;
      //JsonRecordReader rdr = JsonRecordReader.getInst("/", List.of(benchmark.idField + ":/" + benchmark.idField, "EventStart:/EventStart"));
      //KeyValueParser parser = new KeyValueParser(benchmark.idField, "EventStart");
      JSONParser parser = new JSONParser();
      Map<String, List<String>> shardVsDocs = new HashMap<>();
      Map<String, AtomicInteger> batchCounters = new ConcurrentHashMap<>();
      try {
        while (!exit && (inputDocs = docReader.readDocs(benchmark.batchSize)) != null) { //can read more than batch size, just use batch size as a sensible value
          for (String inputDoc : inputDocs) {
            //rdr.streamRecords(new StringReader(inputDoc), parser);
            JSONObject jsonObj = (JSONObject) parser.parse(inputDoc);
            //String id = (String) parser.value(benchmark.idField);
            String id = (String) jsonObj.get(benchmark.idField);
            Slice targetSlice = docCollection.getRouter().getTargetSlice(id, null, null, null, docCollection);
            List<String> shardDocs = shardVsDocs.computeIfAbsent(targetSlice.getName(), key -> new ArrayList<>(benchmark.batchSize));
            //Object eventStart = parser.value("EventStart");
            if (benchmark.overrideTimestamp != null) {
              Object oldTime = jsonObj.get(benchmark.overrideTimestamp);
              if (oldTime instanceof String) {
                //System.out.println(formatter.format(new Date((Long) eventStart)));
                String newTime = ZonedDateTime.now().format(formatter);
                System.out.println(id + " : " + oldTime + "=>" + newTime);
                jsonObj.put(benchmark.overrideTimestamp, newTime);
                inputDoc = jsonObj.toJSONString();
              }
            }

            shardDocs.add(inputDoc);
            if (shardDocs.size() >= benchmark.batchSize) {
              shardVsDocs.remove(targetSlice.getName());
              String batchFilename = computeBatchFilename(benchmark, batchCounters, targetSlice.getName());
              //a shard has accumulated enough docs to be executed
              Callable docsBatchCallable = init ? new PrepareRawBinaryFiles(benchmark, batchFilename, shardDocs, shardVsLeader.get(targetSlice.getName())) :
                      new UploadDocs(benchmark, batchFilename, shardDocs, httpClient, shardVsLeader.get(targetSlice.getName()), batchesIndexed);
              while (!exit && !pendingBatches.offer(docsBatchCallable, 1, TimeUnit.SECONDS)) {
                //try again
              }
            }
          }
        }
        shardVsDocs.forEach((shard, docs) -> { //flush the remaining ones
          try {
            String batchFilename = computeBatchFilename(benchmark, batchCounters, shard);
            Callable docsBatchCallable = init ? new PrepareRawBinaryFiles(benchmark, batchFilename, docs, shardVsLeader.get(shard)) :
                    new UploadDocs(benchmark, batchFilename, docs, httpClient, shardVsLeader.get(shard), batchesIndexed);
            while (!exit && !pendingBatches.offer(docsBatchCallable, 1, TimeUnit.SECONDS)) {
              //try again
            }
          } catch (InterruptedException e) {
            log.warn(e.getMessage(), e);
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
  public Callable get() {
    try {
      Callable batch = null;
      while ((batch = pendingBatches.poll(1, TimeUnit.SECONDS)) == null && !exit) {
      }
      if (batch == null) { //rare race condition can fill the queue even if above loop exits, just try it once last time...
        batch = pendingBatches.poll();
      }

      if (batch != null) {
        Callable finalBatch = batch;
        return new ControlledExecutor.CallableWithType<>() { //wrap it so listener can report metrics on it with extra info
          @Override
          public Object call() throws Exception {
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
