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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanAttributeInfo;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import com.j256.simplejmx.client.JmxClient;

/**
 * This class provides utility methods for the package.
 *
 */
public class Util {

  public final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static String WORK_DIRECTORY = getWorkingDir() + File.separator;

  public static String getWorkingDir(){
    try {
      return new File( "." ).getCanonicalPath();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // TODO: nocommit: Clean up this directory mess! No dir should end with a separator!
  public static final String DNAME = "SolrNightlyBenchmarksWorkDirectory";
  public static final String BASE_DIR = WORK_DIRECTORY + DNAME + File.separator;
  public static final String RUN_DIR = BASE_DIR + "RunDirectory" + File.separator;
  public static final String DOWNLOAD_DIR = BASE_DIR + "Download" + File.separator;
  public static final String SOLR_DIR = RUN_DIR;

  public static double getJmxCPULoad(String host, int port, String mbeanName, String attribName) throws Exception {
		try (JmxClient client = new JmxClient(host, port);) {
			Set<ObjectName> objectNames = client.getBeanNames();
			for (ObjectName name : objectNames) {
				MBeanAttributeInfo[] attributes = client.getAttributesInfo(name);
				if (mbeanName.equals(name.toString())) {
					for (MBeanAttributeInfo attrib: attributes) {
						if (attrib.getName().equals(attribName)) {
							Object obj = client.getAttribute(name, attrib.getName());
							return (double)obj;
						}
					}
				}
			}
		} catch (Exception ex) {
			System.err.println("Exception fetching JMX. " + ex);
		}
		return -1;
	}
  
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

	if (!workingDirectory.exists()) {
		logger.info("Working dir [" + workingDirectoryPath + "] not yet exists, creating...");
		workingDirectory.mkdirs();
	}
    workingDirectory.setExecutable(true);

    Runtime rt = Runtime.getRuntime();
    Process proc = null;
    ProcessStreamReader processErrorStream = null;
    ProcessStreamReader processOutputStream = null;
    
    List<String> vars = new ArrayList<String>();
    for (Map.Entry<String, String> e: System.getenv().entrySet()) {
    	vars.add(e.toString());
    }

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

  
  public static Map map(Object... params) {
    LinkedHashMap ret = new LinkedHashMap();
    for (int i=0; i<params.length; i+=2) {
      Object o = ret.put(params[i], params[i+1]);
      // TODO: handle multi-valued map?
    }
    return ret;
  }

  private static boolean isPortFree(int port) {
	  try (ServerSocket socket = new ServerSocket(port);) {
		  return true;
	  } catch (IOException ex) {
		  return false;
	  }
  }

  /**
   * Gets a free port number where there's a guarantee of num consecutive ports are free
   */
  public static int getFreePort(int num) throws Exception {
	  int start = 50000;
	  for (int i=start; i<60000; i++) {
		  int j = i;
		  for (j=i; j<i+num && j<60000; j++) {
			  if (isPortFree(j) == false) break;
		  }
		  if (j==i+num) return i;
	  }
	  return -1;
  }
  
  public static class ProcessStreamReader extends Thread {

	  public final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

  public static File resolveSuitePath(String input) {
	  if (input.startsWith("/")) {
		  File file = new File(input);
		  if (file.exists()) {
			  return file; // this is an absolute path
		  } else {
			  throw new RuntimeException("Absolute path " + input + " doesn't exist.");
		  }
	  }
	  File absPathFile = new File(System.getProperty("SUITE_BASE_DIRECTORY") + File.separator + input);
	  if (absPathFile.exists()) {
		  return absPathFile;
	  } else {
		  throw new RuntimeException("File " + input + " doesn't exist.");
	  }
  }
}
