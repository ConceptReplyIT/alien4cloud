package alien4cloud.it.orchestrators;

import alien4cloud.it.Context;
import alien4cloud.model.orchestrators.OrchestratorConfiguration;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.utils.JsonUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;

public class OrchestratorsConfigurationDefinitionsSteps {

    @When("^I get configuration for orchestrator \"([^\"]*)\"$")
    public void I_get_configuration_for_orchestrator(String orchestratorName) throws Throwable {
        String orchestratorId = Context.getInstance().getOrchestratorId(orchestratorName);
        String restResponse = Context.getRestClientInstance().get("/rest/v1/orchestrators/" + orchestratorId + "/configuration");
        Context.getInstance().registerRestResponse(restResponse);
        RestResponse<OrchestratorConfiguration> orchestratorConfigurationResponse = JsonUtil.read(Context.getInstance().getRestResponse(),
                OrchestratorConfiguration.class);
        Map<String, Object> configuration = (Map<String, Object>) orchestratorConfigurationResponse.getData().getConfiguration();
        Context.getInstance().setOrchestratorConfiguration(configuration);
    }

    @And("^I update cloudify (\\d+) manager's url to \"([^\"]*)\" with login \"([^\"]*)\" and password \"([^\"]*)\" for orchestrator with name \"([^\"]*)\"$")
    public void I_update_cloudify_manager_s_url_to_with_login_and_password_for_cloud_with_name(int cloudifyVersion, String cloudifyUrl, String login,
            String password, String orchestratorName) throws Throwable {
        String orchestratorId = Context.getInstance().getOrchestratorId(orchestratorName);
        Map<String, Object> config = Context.getInstance().getOrchestratorConfiguration();
        switch (cloudifyVersion) {
        case 3:
            config.put("url", cloudifyUrl);
            config.put("userName", login);
            config.put("password", password);
            config.put("disableSSLVerification", true);
            break;
        default:
            throw new IllegalArgumentException("Cloudify version not supported " + cloudifyVersion);
        }
        Context.getInstance().setOrchestratorConfiguration(config);
        String restResponse = Context.getRestClientInstance().putJSon("/rest/v1/orchestrators/" + orchestratorId + "/configuration", JsonUtil.toString(config));
        Context.getInstance().registerRestResponse(restResponse);
    }

    @And("^I update cloudify (\\d+) manager's \"([^\"]*)\" property to \"([^\"]*)\" for orchestrator with name \"([^\"]*)\"$")
    public void I_update_cloudify_manager_s_property_to_for_cloud_with_name(int cloudifyVersion, String propertyName, String propertyValue, String orchestratorName) throws Throwable {
        String orchestratorId = Context.getInstance().getOrchestratorId(orchestratorName);
        Map<String, Object> config = Context.getInstance().getOrchestratorConfiguration();
        switch (cloudifyVersion) {
            case 3:
                if (config.containsKey(propertyName)) {
                    config.remove(propertyName);
                }
                config.put(propertyName, propertyValue);
                break;
            default:
                throw new IllegalArgumentException("Cloudify version not supported " + cloudifyVersion);
        }
        Context.getInstance().setOrchestratorConfiguration(config);
        String restResponse = Context.getRestClientInstance().putJSon("/rest/v1/orchestrators/" + orchestratorId + "/configuration", JsonUtil.toString(config));
        Context.getInstance().registerRestResponse(restResponse);
    }

    @When("^I update orchestrator \"([^\"]*)\"'s configuration property \"([^\"]*)\" to \"([^\"]*)\"$")
    public void I_update_orchestrator_configuration_property_to(String orchestratorName, String configurationProperty, String value) throws Throwable {
        String orchestratorId = Context.getInstance().getOrchestratorId(orchestratorName);
        Map<String, Object> config = Context.getInstance().getOrchestratorConfiguration();
        config.put(configurationProperty, value);
        String restResponse = Context.getRestClientInstance().putJSon("/rest/v1/orchestrators/" + orchestratorId + "/configuration", JsonUtil.toString(config));
        Context.getInstance().registerRestResponse(restResponse);
    }

    @When("^I update orchestrator \"([^\"]*)\"'s configuration property \"([^\"]*)\" to the value defined in environment variable \"([^\"]*)\"$")
    public void I_update_orchestrator_configuration_property_to_the_value_defined_in_environment_variable(String orchestratorName, String configurationProperty,
            String envName) throws Throwable {
        String postDeploymentAppURL = System.getenv(envName);
        Assert.assertTrue(envName + " is not defined", StringUtils.isNotBlank(postDeploymentAppURL));
        I_update_orchestrator_configuration_property_to(orchestratorName, configurationProperty, postDeploymentAppURL);
    }

    @Given("^I update \"(.*?)\" location import param for orchestrator with name \"(.*?)\" using \"(.*?)\"$")
    public void i_update_import_param_for_orchestrator_with_name_using(String infraType, String orchestratorName, String importsCsv) throws Throwable {
        String orchestratorId = Context.getInstance().getOrchestratorId(orchestratorName);
        Map<String, Object> config = Context.getInstance().getOrchestratorConfiguration();
        Map<String, Object> locations = (Map<String, Object>) config.get("locations");
        if (locations == null) {
            locations = Maps.newHashMap();
        }
        config.put("locations", locations);
        Map<String, Object> openstack = (Map<String, Object>) locations.get(infraType);
        if (openstack == null) {
            openstack = Maps.newHashMap();
        }
        locations.put(infraType, openstack);
        List<String> imports = Lists.newArrayList(importsCsv.split(","));
        openstack.put("imports", imports);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().putJSon("/rest/v1/orchestrators/" + orchestratorId + "/configuration", JsonUtil.toString(config)));
    }

    @And("^I update cloudify (\\d+) manager's url to value defined in environment variable \"([^\"]*)\" for orchestrator with name \"([^\"]*)\"$")
    public void iUpdateCloudifyManagerSUrlToValueDefinedInEnvironmentVariableForOrchestratorWithName(int cloudifyVersion, String envVar,
            String orchestratorName) throws Throwable {
        String managerURL = System.getenv(envVar);
        Assert.assertTrue(envVar + " is not defined", StringUtils.isNotBlank(managerURL));
        I_update_cloudify_manager_s_url_to_with_login_and_password_for_cloud_with_name(cloudifyVersion, managerURL, "admin", "admin", orchestratorName);
    }

}
