/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.benchmarks;

import au.com.bytecode.opencsv.CSVReader;
import com.google.cloud.compute.deprecated.*;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.log4j.Logger;
import org.apache.lucene.util.TestUtil;
import org.apache.solr.benchmarks.beans.Cluster;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.util.Pair;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import java.io.*;
import java.math.BigInteger;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * This class provides utility methods for the package.
 * @author Vivek Narang
 *
 */
public class Util {

  public final static Logger logger = Logger.getLogger(Util.class);

  public static String WORK_DIRECTORY = getWorkingDir() + File.separator;

  private static String getWorkingDir(){
    try {
      return new File( "." ).getCanonicalPath();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String DNAME = "SolrNightlyBenchmarksWorkDirectory";
  public static String BASE_DIR = WORK_DIRECTORY + DNAME + File.separator;
  public static String RUN_DIR = BASE_DIR + "RunDirectory" + File.separator;
  public static String DOWNLOAD_DIR = BASE_DIR + "Download" + File.separator;
  public static String ZOOKEEPER_DOWNLOAD_URL = "https://archive.apache.org/dist/zookeeper/";
  public static String ZOOKEEPER_RELEASE = "3.4.14";
  public static String ZOOKEEPER_DIR = RUN_DIR;
  public static String SOLR_DIR = RUN_DIR;

  public static List<String> argsList;


  /**
   * A method used for invoking a process with specific parameters.
   * 
   * @param command
   * @param workingDirectoryPath
   * @return
   * @throws Exception 
   */
  public static int execute(String command, String workingDirectoryPath) throws Exception {
    logger.debug("Executing: " + command);
    logger.debug("Working dir: " + workingDirectoryPath);
    File workingDirectory = new File(workingDirectoryPath);

    workingDirectory.setExecutable(true);

    Runtime rt = Runtime.getRuntime();
    Process proc = null;
    ProcessStreamReader processErrorStream = null;
    ProcessStreamReader processOutputStream = null;
    
    List<String> vars = new ArrayList<String>();
    for (Map.Entry<String, String> e: System.getenv().entrySet()) {
    	vars.add(e.toString());
    }
    //vars.add("GIT_SSH_COMMAND=\"ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no\"");

    try {
      proc = rt.exec(command, (String[])vars.toArray(new String[] {}), workingDirectory);
      processErrorStream = new ProcessStreamReader(proc.getErrorStream(), "ERROR");
      processOutputStream = new ProcessStreamReader(proc.getInputStream(), "OUTPUT");

      processErrorStream.start();
      processOutputStream.start();
      proc.waitFor();
      return proc.exitValue();
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    }
  }

  public static Map map(Object... params) {
    LinkedHashMap ret = new LinkedHashMap();
    for (int i=0; i<params.length; i+=2) {
      Object o = ret.put(params[i], params[i+1]);
      // TODO: handle multi-valued map?
    }
    return ret;
  }

  /**
   * A method used for get an available free port for running the
   * solr/zookeeper node on.
   * 
   * @return int
   * @throws Exception 
   */
  public static int getFreePort() throws Exception {

    int port = ThreadLocalRandom.current().nextInt(10000, 60000);
    logger.debug("Looking for a free port ... Checking availability of port number: " + port);
    ServerSocket serverSocket = null;
    DatagramSocket datagramSocket = null;
    try {
      serverSocket = new ServerSocket(port);
      serverSocket.setReuseAddress(true);
      datagramSocket = new DatagramSocket(port);
      datagramSocket.setReuseAddress(true);
      logger.debug("Port " + port + " is free to use. Using this port !!");
      return port;
    } catch (IOException e) {
    } finally {
      if (datagramSocket != null) {
        datagramSocket.close();
      }

      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          logger.error(e.getMessage());
          throw new IOException(e.getMessage());
        }
      }
      // Marking for GC
      serverSocket = null;
      datagramSocket = null;
    }

    logger.debug("Port " + port + " looks occupied trying another port number ... ");
    return getFreePort();
  }

  /**
   * 
   * @param plaintext
   * @return String
   * @throws Exception 
   */
  static public String md5(String plaintext) throws Exception {
    MessageDigest m;
    String hashtext = null;
    try {
      m = MessageDigest.getInstance("MD5");
      m.reset();
      m.update(plaintext.getBytes());
      byte[] digest = m.digest();
      BigInteger bigInt = new BigInteger(1, digest);
      hashtext = bigInt.toString(16);
      // Now we need to zero pad it if you actually want the full 32
      // chars.
      while (hashtext.length() < 32) {
        hashtext = "0" + hashtext;
      }
    } catch (NoSuchAlgorithmException e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    }
    return hashtext;
  }

  /**
   * A metod used for extracting files from an archive.
   * 
   * @param zipIn
   * @param filePath
   * @throws Exception 
   */
  public static void extractFile(ZipInputStream zipIn, String filePath) throws Exception {

    BufferedOutputStream bos = null;
    try {

      bos = new BufferedOutputStream(new FileOutputStream(filePath));
      byte[] bytesIn = new byte[4096];
      int read = 0;
      while ((read = zipIn.read(bytesIn)) != -1) {
        bos.write(bytesIn, 0, read);
      }
      bos.close();
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    } finally {
      bos.close();
      // Marking for GC
      bos = null;
    }
  }

  /**
   * A method used for downloading a resource from external sources.
   * 
   * @param downloadURL
   * @param fileDownloadLocation
   * @throws Exception 
   */
  public static void download(String downloadURL, String fileDownloadLocation) throws Exception {

    URL link = null;
    InputStream in = null;
    FileOutputStream fos = null;

    try {

      link = new URL(downloadURL);
      in = new BufferedInputStream(link.openStream());
      fos = new FileOutputStream(fileDownloadLocation);
      byte[] buf = new byte[1024 * 1024]; // 1mb blocks
      int n = 0;
      long size = 0;
      while (-1 != (n = in.read(buf))) {
        size += n;
        logger.debug("\r" + size + " ");
        fos.write(buf, 0, n);
      }
      fos.close();
      in.close();
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    }
  }

  /**
   * A method used for extracting files from a zip archive.
   * 
   * @param filename
   * @throws IOException
   */
  public static void extract(String filename, String destPath) throws IOException {
    logger.debug(" Attempting to unzip the downloaded release ...");
    System.out.println("destPath: "+destPath);
    Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
    archiver.extract(new File(filename), new File(destPath));
  }

  /**
   * A method used for fetching latest commit from a remote repository.
   * 
   * @param repositoryURL
   * @return
   * @throws IOException
   */
  public static String getLatestCommitID(String repositoryURL) throws IOException {
    logger.debug(" Getting the latest commit ID from: " + repositoryURL);
    return new BufferedReader(new InputStreamReader(
        Runtime.getRuntime().exec("git ls-remote " + repositoryURL + " HEAD").getInputStream())).readLine()
        .split("HEAD")[0].trim();
  }

  /**
   * A method used for sending requests to web resources.
   * 
   * @param url
   * @param type
   * @return
   * @throws Exception 
   */
  public static String getResponse(String url, String type) throws Exception {

    Client client;
    ClientResponse response;

    try {
      client = Client.create();
      WebResource webResource = client.resource(url);
      response = webResource.accept(type).get(ClientResponse.class);

      if (response.getStatus() != 200) {
        logger.error("Failed : HTTP error code : " + response.getStatus());
        throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
      }

      return response.getEntity(String.class);
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    } finally {
      // Marking for GC
      client = null;
      response = null;
    }
  }

  /**
   * A method used for generating random sentences for tests.
   * 
   * @param r
   * @param words
   * @return String
   */
  public static String getSentence(Random r, int words) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words; i++) {
      sb.append(TestUtil.randomSimpleString(r, 4 + r.nextInt(10)) + " ");
    }
    return sb.toString().trim();
  }

  /**
   * A method used for locating and killing unused processes.
   * 
   * @param lookFor
   * @throws IOException 
   */
  public static void killProcesses(String lookFor) throws IOException {

    logger.debug(" Searching and killing " + lookFor + " process(es) ...");

    BufferedReader reader;
    String line = "";

    try {
      String[] cmd = { "/bin/sh", "-c", "ps -ef | grep " + lookFor + " | awk '{print $2}'" };
      reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(cmd).getInputStream()));

      while ((line = reader.readLine()) != null) {

        line = line.trim();
        logger.debug(" Found " + lookFor + " Running with PID " + line + " Killing now ..");
        Runtime.getRuntime().exec("kill -9 " + line);
      }

      reader.close();

    } catch (IOException e) {
      logger.error(e.getMessage());
      throw new IOException(e.getMessage());
    } finally {
      // Marking for GC
      reader = null;
      line = null;
    }

  }

  /**
   * A utility method used for getting the head name.
   * @param repo
   * @return string
   * @throws Exception 
   */
  public static String getHeadName(Repository repo) throws Exception {
    String result = null;
    try {
      ObjectId id = repo.resolve(Constants.HEAD);
      result = id.getName();
    } catch (IOException e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    }
    return result;
  }

  public static String getDateString(int epoch) {
    Date date = new Date(1000L * epoch);
    SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
    String dateStr = format.format(date);
    return dateStr;
  }

  public static void outputMetrics(String filename, Map<String, String> timings) throws Exception {
    File outputFile = new File(filename);

    String header[] = new String[0];
    List<Map<String, String>> lines = new ArrayList<>();

    if (outputFile.exists()) {
      CSVReader reader = new CSVReader(new FileReader(outputFile));

      String tmp[] = reader.readNext();
      header = new String[tmp.length];
      for (int i=0; i<header.length; i++) {
        header[i] = tmp[i].trim();
      }

      String line[];
      while((line=reader.readNext()) != null) {
        if (line.length != header.length) {
          continue;
        }
        Map<String, String> mappedLine = new LinkedHashMap<>();

        for (int i=0; i<header.length; i++) {
          mappedLine.put(header[i], line[i]);
        }
        lines.add(mappedLine);
      }
      reader.close();
    }

    LinkedHashSet<String> newHeaders = new LinkedHashSet<>(Arrays.asList(header));
    newHeaders.addAll(timings.keySet());

    lines.add(timings);
    FileWriter out = new FileWriter(filename);
    out.write(Arrays.toString(newHeaders.toArray()).replaceAll("\\[", "").replaceAll("\\]", "") + "\n");
    for (Map<String, String> oldLine: lines) {
      for (int i=0; i<newHeaders.size(); i++) {
        String col = oldLine.get(newHeaders.toArray()[i]);
        out.write(col == null? " ": col);
        if (i==newHeaders.size()-1) {
          out.write("\n");
        } else {
          out.write(",");
        }
      }
    }
    out.close();                            
  }

  public static class ProcessStreamReader extends Thread {

    public final static Logger logger = Logger.getLogger(ProcessStreamReader.class);

    InputStream is;
    String type;

    /**
     * Constructor.
     * 
     * @param is
     * @param type
     */
    ProcessStreamReader(InputStream is, String type) {
      this.is = is;
      this.type = type;
    }

    /**
     * A method invoked by process execution thread.
     */
    public void run() {
      try {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line = null;
        while ((line = br.readLine()) != null) {
          logger.info(">> " + line);
        }

      } catch (IOException ioe) {
        logger.error(ioe.getMessage());
        throw new RuntimeException(ioe.getMessage());
      }
    }
  }
  
  public static Pair<InstanceId, Operation> createNewInstance(String name, Cluster.InstanceConfig config) {
    logger.info("Provisioning GCP instance "+name+" ...");
    Compute compute = ComputeOptions.getDefaultInstance().getService();

    System.out.println("App ID: "+ComputeOptions.getDefaultProjectId());

    //ImageId imageId = ImageId.of("debian-cloud", "debian-10-buster-v20191210");
    ImageId imageId = ImageId.of(config.imageProject, config.imageName);
    NetworkId networkId = NetworkId.of(config.network);
    AttachedDisk attachedDisk = AttachedDisk.of(AttachedDisk.CreateDiskConfiguration.of(imageId));
    NetworkInterface networkInterface = NetworkInterface.of(networkId);
    InstanceId instanceId = InstanceId.of(config.region, name);
    MachineTypeId machineTypeId = MachineTypeId.of(config.region, config.instanceType);

    Operation operation = compute.create(InstanceInfo.of(instanceId, machineTypeId, attachedDisk, networkInterface));

    return new Pair<InstanceId, Operation>(instanceId, operation);
  }
  
  public static Instance waitForInstance(InstanceId instanceId, Operation operation) throws InterruptedException {
    logger.info("Waiting for GCP instance ("+instanceId+") to be RUNNING");
    operation = operation.waitFor();
    if (operation.getErrors() != null) {
      throw new RuntimeException(operation.getErrors().toString());
    }
    // return the instance
    Compute compute = ComputeOptions.getDefaultInstance().getService();
    Instance instance = compute.getInstance(instanceId);
    return instance;
  }

  public static Instance createNewInstanceWithWait(String name, Cluster.InstanceConfig config) throws InterruptedException {
    Pair<InstanceId, Operation> instanceDetailsBeforeProvisioning = Util.createNewInstance(name, config);
    Instance instance = Util.waitForInstance(instanceDetailsBeforeProvisioning.first(), instanceDetailsBeforeProvisioning.second());
    return instance;
  }

  public static void deleteInstances(List<Instance> instances) throws InterruptedException {
    logger.info("Now trying to delete the instance...");
    ArrayList<Operation> deleteOps = new ArrayList();
    for (Instance instance: instances) {
      Operation operation = instance.delete();
      deleteOps.add(operation);
      System.out.println("Deleted the instance "+instance.getInstanceId()+"...");
    }
    System.out.println("Waiting for all instances to be cleaned up completely.");
    for (Operation op: deleteOps) {
      op.waitFor();
    }
  }


  public static String getUntarredDirectory(String file) throws IOException, FileNotFoundException {
    try (GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(new File(file)));) {
      try (TarArchiveInputStream tis = new TarArchiveInputStream(gzis);) {
        TarArchiveEntry tarEntry = null;
        while ((tarEntry = tis.getNextTarEntry()) != null) {
          return (tarEntry.getName().substring(0, tarEntry.getName().indexOf('/')));
        }
      }
    }
    return null;
  }


  private static final Charset CHARSET_US_ASCII = Charset.forName("US-ASCII");

  public static final String INPUT_ENCODING_KEY = "ie";
  private static final byte[] INPUT_ENCODING_BYTES = INPUT_ENCODING_KEY.getBytes(CHARSET_US_ASCII);

  /**
   * Given a url-encoded query string (UTF-8), map it into solr params
   */
  public static MultiMapSolrParams parseQueryString(String queryString) {
	  Map<String,String[]> map = new HashMap<>();
	  parseQueryString(queryString, map);
	  return new MultiMapSolrParams(map);
  }

  /**
   * Given a url-encoded query string (UTF-8), map it into the given map
   * @param queryString as given from URL
   * @param map place all parameters in this map
   */
  static void parseQueryString(final String queryString, final Map<String,String[]> map) {
	  if (queryString != null && queryString.length() > 0) {
		  try {
			  final int len = queryString.length();
			  // this input stream emulates to get the raw bytes from the URL as passed to servlet container, it disallows any byte > 127 and enforces to %-escape them:
			  final InputStream in = new InputStream() {
				  int pos = 0;
				  @Override
				  public int read() {
					  if (pos < len) {
						  final char ch = queryString.charAt(pos);
						  if (ch > 127) {
							  throw new SolrException(ErrorCode.BAD_REQUEST, "URLDecoder: The query string contains a not-%-escaped byte > 127 at position " + pos);
						  }
						  pos++;
						  return ch;
					  } else {
						  return -1;
					  }
				  }
			  };
			  parseFormDataContent(in, Long.MAX_VALUE, StandardCharsets.UTF_8, map, true);
		  } catch (IOException ioe) {
			  throw new SolrException(ErrorCode.BAD_REQUEST, ioe);
		  }
	  }
  }

  @SuppressWarnings({"fallthrough", "resource"})
  static long parseFormDataContent(final InputStream postContent, final long maxLen, Charset charset, final Map<String,String[]> map, boolean supportCharsetParam) throws IOException {
	  CharsetDecoder charsetDecoder = supportCharsetParam ? null : getCharsetDecoder(charset);
	  final LinkedList<Object> buffer = supportCharsetParam ? new LinkedList<>() : null;
	  long len = 0L, keyPos = 0L, valuePos = 0L;
	  final ByteArrayOutputStream keyStream = new ByteArrayOutputStream(),
			  valueStream = new ByteArrayOutputStream();
	  ByteArrayOutputStream currentStream = keyStream;
	  for(;;) {
		  int b = postContent.read();
		  switch (b) {
		  case -1: // end of stream
		  case '&': // separator
			  if (keyStream.size() > 0) {
				  final byte[] keyBytes = keyStream.toByteArray(), valueBytes = valueStream.toByteArray();
				  if (Arrays.equals(keyBytes, INPUT_ENCODING_BYTES)) {
					  // we found a charset declaration in the raw bytes
					  if (charsetDecoder != null) {
						  throw new SolrException(ErrorCode.BAD_REQUEST,
								  supportCharsetParam ? (
										  "Query string invalid: duplicate '"+
												  INPUT_ENCODING_KEY + "' (input encoding) key."
										  ) : (
												  "Key '" + INPUT_ENCODING_KEY + "' (input encoding) cannot "+
														  "be used in POSTed application/x-www-form-urlencoded form data. "+
														  "To set the input encoding of POSTed form data, use the "+
														  "'Content-Type' header and provide a charset!"
												  )
								  );
					  }
					  // decode the charset from raw bytes
					  charset = Charset.forName(decodeChars(valueBytes, keyPos, getCharsetDecoder(CHARSET_US_ASCII)));
					  charsetDecoder = getCharsetDecoder(charset);
					  // finally decode all buffered tokens
					  decodeBuffer(buffer, map, charsetDecoder);
				  } else if (charsetDecoder == null) {
					  // we have no charset decoder until now, buffer the keys / values for later processing:
					  buffer.add(keyBytes);
					  buffer.add(Long.valueOf(keyPos));
					  buffer.add(valueBytes);
					  buffer.add(Long.valueOf(valuePos));
				  } else {
					  // we already have a charsetDecoder, so we can directly decode without buffering:
					  final String key = decodeChars(keyBytes, keyPos, charsetDecoder),
							  value = decodeChars(valueBytes, valuePos, charsetDecoder);
					  MultiMapSolrParams.addParam(key.trim(), value, map);
				  }
			  } else if (valueStream.size() > 0) {
				  throw new SolrException(ErrorCode.BAD_REQUEST, "application/x-www-form-urlencoded invalid: missing key");
			  }
			  keyStream.reset();
			  valueStream.reset();
			  keyPos = valuePos = len + 1;
			  currentStream = keyStream;
			  break;
		  case '+': // space replacement
			  currentStream.write(' ');
			  break;
		  case '%': // escape
			  final int upper = digit16(b = postContent.read());
			  len++;
			  final int lower = digit16(b = postContent.read());
			  len++;
			  currentStream.write(((upper << 4) + lower));
			  break;
		  case '=': // kv separator
			  if (currentStream == keyStream) {
				  valuePos = len + 1;
				  currentStream = valueStream;
				  break;
			  }
			  // fall-through
		  default:
			  currentStream.write(b);
		  }
		  if (b == -1) {
			  break;
		  }
		  len++;
		  if (len > maxLen) {
			  throw new SolrException(ErrorCode.BAD_REQUEST, "application/x-www-form-urlencoded content exceeds upload limit of " + (maxLen/1024L) + " KB");
		  }
	  }
	  // if we have not seen a charset declaration, decode the buffer now using the default one (UTF-8 or given via Content-Type):
	  if (buffer != null && !buffer.isEmpty()) {
		  assert charsetDecoder == null;
		  decodeBuffer(buffer, map, getCharsetDecoder(charset));
	  }
	  return len;
  }

  private static CharsetDecoder getCharsetDecoder(Charset charset) {
	  return charset.newDecoder()
			  .onMalformedInput(CodingErrorAction.REPORT)
			  .onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  private static String decodeChars(byte[] bytes, long position, CharsetDecoder charsetDecoder) {
	  try {
		  return charsetDecoder.decode(ByteBuffer.wrap(bytes)).toString();
	  } catch (CharacterCodingException cce) {
		  throw new SolrException(ErrorCode.BAD_REQUEST,
				  "URLDecoder: Invalid character encoding detected after position " + position +
				  " of query string / form data (while parsing as " + charsetDecoder.charset().name() + ")"
				  );
	  }
  }

  private static void decodeBuffer(final LinkedList<Object> input, final Map<String,String[]> map, CharsetDecoder charsetDecoder) {
	  for (final Iterator<Object> it = input.iterator(); it.hasNext(); ) {
		  final byte[] keyBytes = (byte[]) it.next();
		  it.remove();
		  final Long keyPos = (Long) it.next();
		  it.remove();
		  final byte[] valueBytes = (byte[]) it.next();
		  it.remove();
		  final Long valuePos = (Long) it.next();
		  it.remove();
		  MultiMapSolrParams.addParam(decodeChars(keyBytes, keyPos.longValue(), charsetDecoder).trim(),
				  decodeChars(valueBytes, valuePos.longValue(), charsetDecoder), map);
	  }
  }

  private static int digit16(int b) {
	  if (b == -1) {
		  throw new SolrException(ErrorCode.BAD_REQUEST, "URLDecoder: Incomplete trailing escape (%) pattern");
	  }
	  if (b >= '0' && b <= '9') {
		  return b - '0';
	  }
	  if (b >= 'A' && b <= 'F') {
		  return b - ('A' - 10);
	  }
	  if (b >= 'a' && b <= 'f') {
		  return b - ('a' - 10);
	  }
	  throw new SolrException(ErrorCode.BAD_REQUEST, "URLDecoder: Invalid digit (" + ((char) b) + ") in escape (%) pattern");
  }


}
