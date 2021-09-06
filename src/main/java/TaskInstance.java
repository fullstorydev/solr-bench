import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskInstance {
	@JsonProperty("type")
	String type;
	
	@JsonProperty("mode")
	String mode;
	
	@JsonProperty("description")
	String description;
	
	@JsonProperty("instances")
	int instances = 1;
	
	@JsonProperty("concurrency")
	int concurrency = 1; // single threaded by default
	
	@JsonProperty("parameters")
	Map<String, String> parameters;
	
	@JsonProperty("pre-task-evals")
	List<String> preTaskEvals;
	
	@JsonProperty("wait-for")
	String waitFor;
}
