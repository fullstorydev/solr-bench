package beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Shard {

    @JsonProperty
    private String range;

    @JsonProperty
    private String state;

    @JsonProperty
    private Map<String, Replica> replicas;

    @JsonProperty
    public String   stateTimestamp;

    public void setRange(String range) {
         this.range = range;
     }
     public String getRange() {
         return range;
     }

    public void setState(String state) {
         this.state = state;
     }
     public String getState() {
         return state;
     }

     public Map<String, Replica> getReplicas() {
         return replicas;
     }

}