package alien4cloud.model.orchestrators.locations;

import java.util.List;

import com.google.common.collect.Lists;

import alien4cloud.orchestrators.locations.services.LocationResourceTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiModel("Contains the types and templates of elements configured for a given location.")
public class LocationResources extends LocationResourceTypes {
    @ApiModelProperty(value = "List of configuration templates already configured for the location. Usually abstract types.")
    private List<LocationResourceTemplate> configurationTemplates = Lists.newArrayList();
    @ApiModelProperty(value = "List of node templates already configured for the location.")
    private List<LocationResourceTemplate> nodeTemplates = Lists.newArrayList();
    /*
     * @ApiModelProperty(value = "List of policy templates already configured for the location.")
     * private List<LocationPolicyTemplate> policyTemplates = Lists.newArrayList();
     */

    public LocationResources(LocationResourceTypes locationResourceTypes) {
        super(locationResourceTypes);
    }
}