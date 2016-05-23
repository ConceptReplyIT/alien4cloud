package alien4cloud.it.runtime;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.elasticsearch.common.collect.Lists;
import org.junit.Assert;

import alien4cloud.it.Context;
import alien4cloud.it.application.ApplicationStepDefinitions;
import alien4cloud.it.common.CommonStepDefinitions;
import alien4cloud.it.topology.TopologyStepDefinitions;
import alien4cloud.paas.model.OperationExecRequest;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.utils.JsonUtil;

import com.google.common.collect.Maps;

import cucumber.api.DataTable;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

@Slf4j
public class RuntimeStepDefinitions {

    private TopologyStepDefinitions topoSteps = new TopologyStepDefinitions();
    private ApplicationStepDefinitions appSteps = new ApplicationStepDefinitions();
    private CommonStepDefinitions commonSteps = new CommonStepDefinitions();

    @When("^I trigger on the node template \"([^\"]*)\" the custom command \"([^\"]*)\" of the interface \"([^\"]*)\" for application \"([^\"]*)\"$")
    public void I_trigger_on_the_node_template_the_custom_command_of_the_interface_on_the_cloud(String nodeTemplateName, String commandName,
            String interfaceName, String appName) throws Throwable {
        I_trigger_on_the_node_template_the_custom_command_of_the_interface_for_application_with_parameters(nodeTemplateName, commandName, interfaceName,
                appName, null);
    }

    @When("^I ask the runtime topology of the application \"([^\"]*)\" on the location \"([^\"]*)\" of \"([^\"]*)\"$")
    public void I_ask_the_runtime_topology_of_the_application_on_the_cloud(String applicationName, String locationName, String orchestratorName) throws Throwable {
        String orchestratorId = Context.getInstance().getOrchestratorId(orchestratorName);
        NameValuePair nvp = new BasicNameValuePair("locationId", Context.getInstance().getLocationId(orchestratorId, locationName));
        String applicationId = Context.getInstance().getApplication().getId();
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().getUrlEncoded(
                "/rest/v1/runtime/" + applicationId + "/environment/" + Context.getInstance().getDefaultApplicationEnvironmentId(applicationName) + "/topology",
                Lists.newArrayList(nvp)));

    }

    @Then("^The operation response should contain the result \"([^\"]*)\" for instance \"([^\"]*)\"$")
    public void The_operation_response_should_contain_the_result_for_instance(String expectedResponse, String instanceId) throws Throwable {
        RestResponse<?> restResponse = JsonUtil.read(Context.getInstance().getRestResponse());
        Map<String, String> executionResults = JsonUtil.toMap(JsonUtil.toString(restResponse.getData()), String.class, String.class);
        Assert.assertNotNull(executionResults.get(instanceId));
        Assert.assertTrue(executionResults.get(instanceId).contains(expectedResponse));
    }

    @When("^I trigger on the node template \"([^\"]*)\" the custom command \"([^\"]*)\" of the interface \"([^\"]*)\" for application \"([^\"]*)\" with parameters:$")
    public void I_trigger_on_the_node_template_the_custom_command_of_the_interface_for_application_with_parameters(String nodeTemplateName, String commandName,
            String interfaceName, String appName, DataTable operationParameters) throws Throwable {
        OperationExecRequest commandRequest = new OperationExecRequest();
        commandRequest.setNodeTemplateName(nodeTemplateName);
        commandRequest.setInterfaceName(interfaceName);
        commandRequest.setOperationName(commandName);
        commandRequest.setApplicationEnvironmentId(Context.getInstance().getDefaultApplicationEnvironmentId(appName));
        if (operationParameters != null) {
            Map<String, String> parameters = Maps.newHashMap();
            for (List<String> operationParameter : operationParameters.raw()) {
                parameters.put(operationParameter.get(0), operationParameter.get(1));
            }
            commandRequest.setParameters(parameters);
        }
        String jSon = JsonUtil.toString(commandRequest);
        String restResponse = Context.getRestClientInstance().postJSon("/rest/v1/runtime/" + Context.getInstance().getApplication().getId() + "/operations/",
                jSon);
        Context.getInstance().registerRestResponse(restResponse);

    }

    private String scale(String nodeName, int instancesToScale) throws IOException {
        return Context.getRestClientInstance().postUrlEncoded(
                "/rest/v1/applications/" + ApplicationStepDefinitions.CURRENT_APPLICATION.getId() + "/environments/"
                        + Context.getInstance().getDefaultApplicationEnvironmentId(ApplicationStepDefinitions.CURRENT_APPLICATION.getName()) + "/scale/"
                        + nodeName, Lists.<NameValuePair> newArrayList(new BasicNameValuePair("instances", String.valueOf(instancesToScale))));
    }

    @When("^I scale up the node \"([^\"]*)\" by adding (\\d+) instance\\(s\\)$")
    public void I_scale_up_the_node_by_adding_instance_s(String nodeName, int instancesToAdd) throws Throwable {
        log.info("Scale up the node " + nodeName + " by " + instancesToAdd);
        Context.getInstance().registerRestResponse(scale(nodeName, instancesToAdd));
        log.info("Finished scaling up the node " + nodeName + " by " + instancesToAdd);
    }

    @When("^I scale down the node \"([^\"]*)\" by removing (\\d+) instance\\(s\\)$")
    public void I_scale_down_the_node_by_removing_instance_s(String nodeName, int instancesToRemove) throws Throwable {
        log.info("Scale down the node " + nodeName + " by " + instancesToRemove);
        Context.getInstance().registerRestResponse(scale(nodeName, -1 * instancesToRemove));
        log.info("Finished scaling down the node " + nodeName + " by " + instancesToRemove);
    }
}
