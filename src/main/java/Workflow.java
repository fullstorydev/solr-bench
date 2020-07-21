import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Workflow {
	@JsonProperty("task-types")
	Map<String, TaskType> taskTypes;
	
	@JsonProperty("global-variables")
	Map<String, Integer> globalVariables;

	@JsonProperty("global-constants")
	Map<String, String> globalConstants;

	@JsonProperty("execution-plan")
	List<TaskInstance> executionPlan;
}
