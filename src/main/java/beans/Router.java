package beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Router {

    @JsonProperty
    public String name;
    public void setName(String name) {
         this.name = name;
     }
     public String getName() {
         return name;
     }

}