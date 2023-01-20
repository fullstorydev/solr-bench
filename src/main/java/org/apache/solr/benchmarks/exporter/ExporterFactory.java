package org.apache.solr.benchmarks.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.solr.benchmarks.WorkflowResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExporterFactory {
  public static ResultExporter getFileExporter(Path filePath) {
    return new FileResultExporter(filePath);
  }
}

class FileResultExporter implements ResultExporter {
  private final Path resultDir;
  public FileResultExporter(Path resultDir) {
    this.resultDir = resultDir;
  }

  @Override
  public void export(WorkflowResult result) throws IOException {
    Files.createDirectories(resultDir);
    new ObjectMapper().writeValue(new File("results.json"), result.getBenchmarkResults());
		new ObjectMapper().writeValue(new File("metrics.json"), result.getMetrics());
  }
}
