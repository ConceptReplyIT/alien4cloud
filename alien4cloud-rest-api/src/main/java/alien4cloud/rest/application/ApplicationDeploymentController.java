package alien4cloud.rest.application;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.validation.Valid;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.validator.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import alien4cloud.application.ApplicationEnvironmentService;
import alien4cloud.application.ApplicationService;
import alien4cloud.audit.annotation.Audit;
import alien4cloud.deployment.DeployService;
import alien4cloud.deployment.DeploymentRuntimeService;
import alien4cloud.deployment.DeploymentRuntimeStateService;
import alien4cloud.deployment.DeploymentService;
import alien4cloud.deployment.DeploymentTopologyService;
import alien4cloud.deployment.UndeployService;
import alien4cloud.deployment.WorkflowExecutionService;
import alien4cloud.deployment.model.DeploymentConfiguration;
import alien4cloud.exception.AlreadyExistException;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.application.Application;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OrchestratorDisabledException;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.rest.application.model.DeployApplicationRequest;
import alien4cloud.rest.application.model.EnvironmentStatusDTO;
import alien4cloud.rest.model.RestError;
import alien4cloud.rest.model.RestErrorCode;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.security.AuthorizationUtil;
import alien4cloud.security.model.ApplicationEnvironmentRole;
import alien4cloud.security.model.ApplicationRole;
import alien4cloud.security.model.DeployerRole;
import alien4cloud.topology.TopologyValidationResult;

import com.google.common.collect.Maps;

@Slf4j
@RestController
@RequestMapping({"/rest/applications", "/rest/v1/applications", "/rest/latest/applications"})
@Api(value = "", description = "Manage opertions on deployed application.")
public class ApplicationDeploymentController {
    @Resource
    private ApplicationService applicationService;
    @Resource
    private ApplicationEnvironmentService applicationEnvironmentService;
    @Resource
    private DeploymentService deploymentService;
    @Inject
    private DeployService deployService;
    @Inject
    private DeploymentTopologyService deploymentTopologyService;
    @Inject
    private UndeployService undeployService;
    @Inject
    private DeploymentRuntimeStateService deploymentRuntimeStateService;
    @Inject
    private DeploymentRuntimeService deploymentRuntimeService;
    @Inject
    private WorkflowExecutionService workflowExecutionService;

    /**
     * Trigger deployment of the application on the current configured PaaS.
     *
     * @param deployApplicationRequest application details for deployment (applicationId + deploymentProperties)
     * @return An empty rest response.
     */
    @ApiOperation(value = "Deploys the application on the configured Cloud.", notes = "Application role required [ APPLICATION_MANAGER | APPLICATION_DEVOPS ] and Application environment role required [ DEPLOYMENT_MANAGER ]")
    @RequestMapping(value = "/deployment", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<?> deploy(@Valid @RequestBody DeployApplicationRequest deployApplicationRequest) throws OrchestratorDisabledException {
        String applicationId = deployApplicationRequest.getApplicationId();
        String environmentId = deployApplicationRequest.getApplicationEnvironmentId();
        Application application = applicationService.checkAndGetApplication(applicationId);
        ApplicationEnvironment environment = applicationEnvironmentService.getEnvironmentByIdOrDefault(applicationId, environmentId);
        if (!environment.getApplicationId().equals(applicationId)) {
            throw new NotFoundException("Unable to find environment with id <" + environmentId + "> for application <" + applicationId + ">");
        }
        // Security check user must be authorized to deploy the environment (or be application manager)
        if (!AuthorizationUtil.hasAuthorizationForApplication(application, ApplicationRole.APPLICATION_MANAGER)) {
            AuthorizationUtil.checkAuthorizationForEnvironment(environment, ApplicationEnvironmentRole.DEPLOYMENT_MANAGER);
        }

        // check that the environment is not already deployed
        boolean isEnvironmentDeployed = applicationEnvironmentService.isDeployed(environment.getId());
        if (isEnvironmentDeployed) {
            throw new AlreadyExistException("Environment with id <" + environmentId + "> for application <" + applicationId + "> is already deployed");
        }
        // Get the deployment configurations
        DeploymentConfiguration deploymentConfiguration = deploymentTopologyService.getDeploymentConfiguration(environment.getId());
        DeploymentTopology deploymentTopology = deploymentConfiguration.getDeploymentTopology();
        // Check authorization on the location
        // get the target locations of the deployment topology
        Map<String, Location> locationMap = deploymentTopologyService.getLocations(deploymentTopology);
        for (Location location : locationMap.values()) {
            AuthorizationUtil.checkAuthorizationForLocation(location, DeployerRole.DEPLOYER);
        }

        // prepare the deployment
        TopologyValidationResult validation = deployService.prepareForDeployment(deploymentTopology);

        // if not valid, then return validation errors
        if (!validation.isValid()) {
            return RestResponseBuilder.<TopologyValidationResult> builder()
                    .error(new RestError(RestErrorCode.INVALID_DEPLOYMENT_TOPOLOGY.getCode(), "The deployment topology for the application <"
                            + application.getName() + "> on the environment <" + environment.getName() + "> is not valid."))
                    .data(validation).build();
        }

        // process with the deployment
        deployService.deploy(deploymentTopology, application);
        // TODO OrchestratorDisabledException handling in the ExceptionHandler
        // return RestResponseBuilder.<Void> builder().error(
        // new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), "Cloud with id <" + environment.getCloudId() + "> is disabled or not found"))
        // .build();

        return RestResponseBuilder.<Void> builder().build();
    }

    /**
     * Trigger un-deployment of the application for a given environment on the current configured PaaS.
     *
     * @param applicationId The id of the application to undeploy.
     * @return An empty rest response.
     */
    @ApiOperation(value = "Un-Deploys the application on the configured PaaS.", notes = "The logged-in user must have the [ APPLICATION_MANAGER ] role for this application. Application environment role required [ DEPLOYMENT_MANAGER ]")
    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/deployment", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<Void> undeploy(@PathVariable String applicationId, @PathVariable String applicationEnvironmentId) {
        ApplicationEnvironment environment = applicationEnvironmentService.getEnvironmentByIdOrDefault(applicationId, applicationEnvironmentId);
        Application application = applicationService.checkAndGetApplication(applicationId);
        if (!AuthorizationUtil.hasAuthorizationForApplication(application, ApplicationRole.APPLICATION_MANAGER)) {
            AuthorizationUtil.checkAuthorizationForEnvironment(environment, ApplicationEnvironmentRole.DEPLOYMENT_MANAGER);
        }
        try {
            undeployService.undeployEnvironment(applicationEnvironmentId);
        } catch (OrchestratorDisabledException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), e.getMessage())).build();
        }
        return RestResponseBuilder.<Void> builder().build();
    }

    /**
     * Get only the active deployment for the given application on the given cloud
     *
     * @param applicationId id of the topology
     * @return the active deployment
     */
    @ApiOperation(value = "Get active deployment for the given application on the given cloud.", notes = "Application role required [ APPLICATION_MANAGER | APPLICATION_DEVOPS ] and Application environment role required [ DEPLOYMENT_MANAGER ]")
    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/active-deployment", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public RestResponse<Deployment> getActiveDeployment(@PathVariable String applicationId, @PathVariable String applicationEnvironmentId) {
        Application application = applicationService.checkAndGetApplication(applicationId);
        // get the topology from the version and the cloud from the environment
        ApplicationEnvironment environment = applicationEnvironmentService.getEnvironmentByIdOrDefault(application.getId(), applicationEnvironmentId);
        // when a user is application manager, he can manipulate environment
        if (!AuthorizationUtil.hasAuthorizationForApplication(application, ApplicationRole.APPLICATION_MANAGER)) {
            AuthorizationUtil.checkAuthorizationForEnvironment(environment, ApplicationEnvironmentRole.DEPLOYMENT_MANAGER,
                    ApplicationEnvironmentRole.APPLICATION_USER);
        }
        Deployment deployment = deploymentService.getActiveDeployment(environment.getId());
        return RestResponseBuilder.<Deployment> builder().data(deployment).build();
    }

    @ApiOperation(value = "Get the deployment status for the environements that the current user is allowed to see for a given application.", notes = "Returns the current status of an application list from the PaaS it is deployed on for all environments.")
    @RequestMapping(value = "/statuses", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public RestResponse<Map<String, Map<String, EnvironmentStatusDTO>>> getApplicationsStatuses(@RequestBody List<String> applicationIds) {
        Map<String, Map<String, EnvironmentStatusDTO>> statuses = Maps.newHashMap();

        for (String applicationId : applicationIds) {
            Map<String, EnvironmentStatusDTO> environmentStatuses = Maps.newHashMap();
            Application application = applicationService.checkAndGetApplication(applicationId);
            // get all environments status for the current application
            ApplicationEnvironment[] environments = applicationEnvironmentService.getByApplicationId(application.getId());
            for (ApplicationEnvironment env : environments) {
                if (AuthorizationUtil.hasAuthorizationForEnvironment(env, ApplicationEnvironmentRole.values())) {
                    DeploymentStatus status = DeploymentStatus.UNKNOWN;
                    try {
                        status = applicationEnvironmentService.getStatus(env);
                    } catch (Exception e) {
                        log.debug("Getting status for the environment <" + env.getId()
                                + "> failed because the associated orchestrator seems disabled. Returned status is UNKNOWN.", e);
                    }
                    environmentStatuses.put(env.getId(), new EnvironmentStatusDTO(env.getName(), status));
                }
            }
            statuses.put(applicationId, environmentStatuses);
        }
        return RestResponseBuilder.<Map<String, Map<String, EnvironmentStatusDTO>>> builder().data(statuses).build();
    }

    /**
     * Get detailed informations for every instances of every node of the application on the PaaS.
     *
     * @param applicationId The id of the application to be deployed.
     * @return A {@link RestResponse} that contains detailed informations (See {@link InstanceInformation}) of the application on the PaaS it is deployed.
     */
    @ApiOperation(value = "Get detailed informations for every instances of every node of the application on the PaaS.", notes = "Application role required [ APPLICATION_MANAGER | APPLICATION_DEVOPS ] and Application environment role required [ APPLICATION_USER | DEPLOYMENT_MANAGER ]")
    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/deployment/informations", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public DeferredResult<RestResponse<Map<String, Map<String, InstanceInformation>>>> getInstanceInformation(@PathVariable String applicationId,
            @PathVariable String applicationEnvironmentId) {
        Application application = applicationService.checkAndGetApplication(applicationId);
        ApplicationEnvironment environment = applicationEnvironmentService.getEnvironmentByIdOrDefault(application.getId(), applicationEnvironmentId);
        if (!AuthorizationUtil.hasAuthorizationForApplication(application, ApplicationRole.APPLICATION_MANAGER)) {
            AuthorizationUtil.checkAuthorizationForEnvironment(environment, ApplicationEnvironmentRole.values());
        }

        Deployment deployment = applicationEnvironmentService.getActiveDeployment(environment.getId());
        final DeferredResult<RestResponse<Map<String, Map<String, InstanceInformation>>>> instancesDeferredResult = new DeferredResult<>(5L * 60L * 1000L);
        if (deployment == null) { // if there is no topology associated with the version it could not have been deployed.
            instancesDeferredResult.setResult(RestResponseBuilder.<Map<String, Map<String, InstanceInformation>>> builder().build());
        } else {
            try {
                deploymentRuntimeStateService.getInstancesInformation(deployment, new IPaaSCallback<Map<String, Map<String, InstanceInformation>>>() {
                    @Override
                    public void onSuccess(Map<String, Map<String, InstanceInformation>> data) {
                        instancesDeferredResult.setResult(RestResponseBuilder.<Map<String, Map<String, InstanceInformation>>> builder().data(data).build());
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        instancesDeferredResult.setErrorResult(throwable);
                    }
                });
            } catch (OrchestratorDisabledException e) {
                log.error("Cannot get instance informations as topology plugin cannot be found.", e);
                instancesDeferredResult.setResult(RestResponseBuilder.<Map<String, Map<String, InstanceInformation>>> builder().build());
            }
        }
        return instancesDeferredResult;
    }

    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/deployment/maintenance", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<Void> switchMaintenanceModeOn(@PathVariable String applicationId, @PathVariable String applicationEnvironmentId) {
        ApplicationEnvironment environment = getAppEnvironmentAndCheckAuthorization(applicationId, applicationEnvironmentId);
        try {
            deploymentRuntimeService.switchMaintenanceMode(environment.getId(), true);
        } catch (OrchestratorDisabledException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), e.getMessage())).build();
        } catch (MaintenanceModeException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.MAINTENANCE_MODE_ERROR.getCode(), e.getMessage())).build();
        }
        return RestResponseBuilder.<Void> builder().build();
    }

    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/deployment/maintenance", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<Void> switchMaintenanceModeOff(@PathVariable String applicationId, @PathVariable String applicationEnvironmentId) {
        ApplicationEnvironment environment = getAppEnvironmentAndCheckAuthorization(applicationId, applicationEnvironmentId);
        try {
            deploymentRuntimeService.switchMaintenanceMode(environment.getId(), false);
        } catch (OrchestratorDisabledException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), e.getMessage())).build();
        } catch (MaintenanceModeException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.MAINTENANCE_MODE_ERROR.getCode(), e.getMessage())).build();
        }
        return RestResponseBuilder.<Void> builder().build();
    }

    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/deployment/{nodeTemplateId}/{instanceId}/maintenance", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<Void> switchInstanceMaintenanceModeOn(@PathVariable String applicationId, @PathVariable String applicationEnvironmentId,
            @PathVariable String nodeTemplateId, @PathVariable String instanceId) {
        ApplicationEnvironment environment = getAppEnvironmentAndCheckAuthorization(applicationId, applicationEnvironmentId);
        try {
            deploymentRuntimeService.switchInstanceMaintenanceMode(environment.getId(), nodeTemplateId, instanceId, true);
        } catch (OrchestratorDisabledException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), e.getMessage())).build();
        } catch (MaintenanceModeException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.MAINTENANCE_MODE_ERROR.getCode(), e.getMessage())).build();
        }
        return RestResponseBuilder.<Void> builder().build();
    }

    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/deployment/{nodeTemplateId}/{instanceId}/maintenance", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<Void> switchInstanceMaintenanceModeOff(@PathVariable String applicationId, @PathVariable String applicationEnvironmentId,
            @PathVariable String nodeTemplateId, @PathVariable String instanceId) {
        ApplicationEnvironment environment = getAppEnvironmentAndCheckAuthorization(applicationId, applicationEnvironmentId);
        try {
            deploymentRuntimeService.switchInstanceMaintenanceMode(environment.getId(), nodeTemplateId, instanceId, false);
        } catch (OrchestratorDisabledException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), e.getMessage())).build();
        } catch (MaintenanceModeException e) {
            return RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.MAINTENANCE_MODE_ERROR.getCode(), e.getMessage())).build();
        }
        return RestResponseBuilder.<Void> builder().build();
    }

    private ApplicationEnvironment getAppEnvironmentAndCheckAuthorization(String applicationId, String applicationEnvironmentId) {
        Application application = applicationService.checkAndGetApplication(applicationId);

        // get the topology from the version and the cloud from the environment
        ApplicationEnvironment environment = applicationEnvironmentService.getEnvironmentByIdOrDefault(application.getId(), applicationEnvironmentId);
        if (!AuthorizationUtil.hasAuthorizationForApplication(application, ApplicationRole.APPLICATION_MANAGER)) {
            AuthorizationUtil.checkAuthorizationForEnvironment(environment, ApplicationEnvironmentRole.DEPLOYMENT_MANAGER);
        }
        return environment;
    }

    /**
     * Scale an application on a particular node.
     *
     * @param applicationId The id of the application to be scaled.
     * @param nodeTemplateId The id of the node template to be scaled.
     * @param instances The instances number to be scaled up (if > 0)/ down (if < 0)
     * @return A {@link RestResponse} that contains the application's current {@link DeploymentStatus}.
     */
    @ApiOperation(value = "Scale the application on a particular node.", notes = "Returns the detailed informations of the application on the PaaS it is deployed."
            + " Application role required [ APPLICATION_MANAGER | APPLICATION_DEVOPS ] and Application environment role required [ DEPLOYMENT_MANAGER ]")
    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/scale/{nodeTemplateId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public DeferredResult<RestResponse<Void>> scale(@PathVariable String applicationId, @PathVariable String applicationEnvironmentId,
            @PathVariable String nodeTemplateId, @RequestParam int instances) {
        final DeferredResult<RestResponse<Void>> result = new DeferredResult<>(15L * 60L * 1000L);
        ApplicationEnvironment environment = getAppEnvironmentAndCheckAuthorization(applicationId, applicationEnvironmentId);

        try {
            deploymentRuntimeService.scale(environment.getId(), nodeTemplateId, instances, new IPaaSCallback<Object>() {
                @Override
                public void onSuccess(Object data) {
                    result.setResult(RestResponseBuilder.<Void> builder().build());
                }

                @Override
                public void onFailure(Throwable e) {
                    result.setErrorResult(
                            RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.SCALING_ERROR.getCode(), e.getMessage())).build());
                }
            });
        } catch (OrchestratorDisabledException e) {
            result.setErrorResult(
                    RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), e.getMessage())).build());
        } catch (PaaSDeploymentException e) {
            result.setErrorResult(RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.SCALING_ERROR.getCode(), e.getMessage())).build());
        }

        return result;
    }

    @ApiOperation(value = "Launch a given workflow.", authorizations = { @Authorization("ADMIN"), @Authorization("APPLICATION_MANAGER") })
    @RequestMapping(value = "/{applicationId}/environments/{applicationEnvironmentId}/workflows/{workflowName}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public DeferredResult<RestResponse<Void>> launchWorkflow(
            @ApiParam(value = "Application id.", required = true) @Valid @NotBlank @PathVariable String applicationId,
            @ApiParam(value = "Deployment id.", required = true) @Valid @NotBlank @PathVariable String applicationEnvironmentId,
            @ApiParam(value = "Workflow name.", required = true) @Valid @NotBlank @PathVariable String workflowName) {

        final DeferredResult<RestResponse<Void>> result = new DeferredResult<>(15L * 60L * 1000L);
        ApplicationEnvironment environment = getAppEnvironmentAndCheckAuthorization(applicationId, applicationEnvironmentId);

        // TODO merge with incoming params
        Map<String, Object> params = Maps.newHashMap();

        try {
            workflowExecutionService.launchWorkflow(environment.getId(), workflowName, params, new IPaaSCallback<Object>() {
                @Override
                public void onSuccess(Object data) {
                    result.setResult(RestResponseBuilder.<Void> builder().build());
                }

                @Override
                public void onFailure(Throwable e) {
                    result.setErrorResult(RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.SCALING_ERROR.getCode(), e.getMessage()))
                            .build());
                }
            });
        } catch (OrchestratorDisabledException e) {
            result.setErrorResult(RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), e.getMessage()))
                    .build());
        } catch (PaaSDeploymentException e) {
            result.setErrorResult(RestResponseBuilder.<Void> builder().error(new RestError(RestErrorCode.SCALING_ERROR.getCode(), e.getMessage())).build());
        }

        return result;
    }

}
