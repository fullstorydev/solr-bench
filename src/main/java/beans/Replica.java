package beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Replica {

    private String core;
    @JsonProperty("base_url")
    private String baseUrl;
    @JsonProperty("node_name")
    private String nodeName;
    private String state;
    private String type;
    @JsonProperty("force_set_state")
    private String forceSetState;
    private String leader;
    public void setCore(String core) {
         this.core = core;
     }
     public String getCore() {
         return core;
     }

    public void setBaseUrl(String baseUrl) {
         this.baseUrl = baseUrl;
     }
     public String getBaseUrl() {
         return baseUrl;
     }

    public void setNodeName(String nodeName) {
         this.nodeName = nodeName;
     }
     public String getNodeName() {
         return nodeName;
     }

    public void setState(String state) {
         this.state = state;
     }
     public String getState() {
         return state;
     }

    public void setType(String type) {
         this.type = type;
     }
     public String getType() {
         return type;
     }

    public void setForceSetState(String forceSetState) {
         this.forceSetState = forceSetState;
     }
     public String getForceSetState() {
         return forceSetState;
     }

    public void setLeader(String leader) {
         this.leader = leader;
     }
     public String getLeader() {
         return leader;
     }

}