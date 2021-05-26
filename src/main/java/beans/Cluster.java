package beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Cluster {

	private Map<String, Collection> collections;

	@JsonProperty("live_nodes")
	private List<String> liveNodes;

	public List<String> getLiveNodes() {
		return liveNodes;
	}

	public Map<String, Collection> getCollections() {
		return collections;
	}
}