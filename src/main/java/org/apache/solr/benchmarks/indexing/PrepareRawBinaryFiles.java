package org.apache.solr.benchmarks.indexing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.util.JavaBinCodec;
import org.apache.solr.common.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * This will be executed during the init phase of the indexing tasks.
 * Primary use for this is to read all the accumulated documents (batches) and
 * prepare binary files, as per the {@link IndexBenchmark#indexingFormat}, to be used for
 * the actual indexing task later.
 */
class PrepareRawBinaryFiles implements Callable {
	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	final List<String> docs;
	final String leaderUrl;
	final private IndexBenchmark benchmark;
	final String batchFilename;

	PrepareRawBinaryFiles(IndexBenchmark benchmark, String batchFilename, List<String> docs, String leaderUrl) {
		this.docs = docs;
		this.leaderUrl = leaderUrl;
		this.benchmark = benchmark;
		this.batchFilename = batchFilename;
		log.info("Batch file: "+batchFilename);
	}

	@Override
	public Object call() throws IOException {
		log.info("INIT PHASE of INDEXING! Shard: "+leaderUrl + ", batch: "+batchFilename);		
		List<Map> parsedDocs = new ArrayList<>();
		for (String doc: docs) parsedDocs.add(new ObjectMapper().readValue(doc, Map.class));
		byte jsonDocs[] = new ObjectMapper().writeValueAsBytes(parsedDocs);
		byte cborDocs[] = createCborReq(jsonDocs);
		byte javabinDocs[] = createJavabinReq(jsonDocs);
		
		byte binary[];
		switch(benchmark.indexingFormat) {
			case "javabin": binary = javabinDocs; break;
			case "cbor"   : binary = cborDocs; break;
			case "json"   : binary = jsonDocs; break;
			default:        binary = jsonDocs; break;
		}
		FileUtils.writeByteArrayToFile(new File(batchFilename), binary);
		log.info("Json size: " + jsonDocs.length + ", cbor size: " + cborDocs.length + ", javabin size: " + javabinDocs.length);
		log.info("Writing filename: " + batchFilename);
		return null;
	}

	private byte[] createJavabinReq(byte[] b) throws IOException {
		List l = (List) Utils.fromJSON(b);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		new JavaBinCodec().marshal(l.iterator(), baos);

		return baos.toByteArray();
	}

	private byte[] createCborReq(byte[] is) throws IOException {
		ByteArrayOutputStream baos;
		ObjectMapper jsonMapper = new ObjectMapper(new JsonFactory());

		// Read JSON file as a JsonNode
		JsonNode jsonNode = jsonMapper.readTree(is);
		// Create a CBOR ObjectMapper
		CBORFactory jf = new CBORFactory();
		ObjectMapper cborMapper = new ObjectMapper(jf);
		baos = new ByteArrayOutputStream();
		JsonGenerator jsonGenerator = cborMapper.createGenerator(baos);

		jsonGenerator.writeTree(jsonNode);
		jsonGenerator.close();
		byte[] bytes = baos.toByteArray();
		return bytes;
	}
}
