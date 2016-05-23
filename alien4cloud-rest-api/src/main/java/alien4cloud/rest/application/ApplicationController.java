package alien4cloud.rest.application;

import java.io.IOException;
import java.util.Date;

import javax.annotation.Resource;
import javax.validation.Valid;

import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.index.query.FilterBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import alien4cloud.application.ApplicationEnvironmentService;
import alien4cloud.application.ApplicationService;
import alien4cloud.application.ApplicationVersionService;
import alien4cloud.audit.annotation.Audit;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.FacetedSearchResult;
import alien4cloud.exception.DeleteDeployedException;
import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.images.IImageDAO;
import alien4cloud.images.exception.ImageUploadException;
import alien4cloud.model.application.Application;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.application.ApplicationVersion;
import alien4cloud.model.templates.TopologyTemplateVersion;
import alien4cloud.paas.exception.OrchestratorDisabledException;
import alien4cloud.rest.application.model.CreateApplicationRequest;
import alien4cloud.rest.application.model.UpdateApplicationRequest;
import alien4cloud.rest.component.SearchRequest;
import alien4cloud.rest.model.RestErrorBuilder;
import alien4cloud.rest.model.RestErrorCode;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.security.AuthorizationUtil;
import alien4cloud.security.model.ApplicationRole;
import alien4cloud.security.model.Role;
import alien4cloud.topology.TopologyTemplateVersionService;
import alien4cloud.utils.ReflectionUtil;
import alien4cloud.utils.services.ConstraintPropertyService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * Service that allows managing applications.
 */
@Slf4j
@RestController
@RequestMapping({"/rest/applications", "/rest/v1/applications", "/rest/latest/applications"})
@Api(value = "", description = "Operations on Applications")
public class ApplicationController {
    @Resource
    private ConstraintPropertyService constraintPropertyService;
    @Resource
    private IImageDAO imageDAO;
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;
    @Resource
    private ApplicationService applicationService;
    @Resource
    private ApplicationVersionService applicationVersionService;
    @Resource
    private TopologyTemplateVersionService topologyTemplateVersionService;
    @Resource
    private ApplicationEnvironmentService applicationEnvironmentService;

    /**
     * Create a new application in the system.
     *
     * @param request The new application to create.
     */
    @ApiOperation(value = "Create a new application in the system.", notes = "If successfull returns a rest response with the id of the created application in data. If not successful a rest response with an error content is returned. Role required [ APPLICATIONS_MANAGER ]. "
            + "By default the application creator will have application roles [APPLICATION_MANAGER, DEPLOYMENT_MANAGER]")
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(value = HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<String> create(@Valid @RequestBody CreateApplicationRequest request) {
        AuthorizationUtil.checkHasOneRoleIn(Role.APPLICATIONS_MANAGER);
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // check the topology template id to recover the related topology id
        String topologyId = null;
        if (request.getTopologyTemplateVersionId() != null) {
            TopologyTemplateVersion ttv = topologyTemplateVersionService.getOrFail(request.getTopologyTemplateVersionId());
            topologyId = ttv.getTopologyId();
        }
        // create the application with default environment and version
        String applicationId = applicationService.create(auth.getName(), request.getName(), request.getDescription(), null);
        ApplicationVersion version = applicationVersionService.createApplicationVersion(applicationId, topologyId);
        ApplicationEnvironment environment = applicationEnvironmentService.createApplicationEnvironment(auth.getName(), applicationId, version.getId());
        return RestResponseBuilder.<String> builder().data(applicationId).build();
    }

    /**
     * Get an application from it's id.
     *
     * @param applicationId The application id.
     */
    @ApiOperation(value = "Get an application based from its id.", notes = "Returns the application details. Application role required [ APPLICATION_MANAGER | APPLICATION_USER | APPLICATION_DEVOPS | DEPLOYMENT_MANAGER ]")
    @RequestMapping(value = "/{applicationId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public RestResponse<Application> get(@PathVariable String applicationId) {
        Application data = alienDAO.findById(Application.class, applicationId);
        AuthorizationUtil.checkAuthorizationForApplication(data, ApplicationRole.values());
        return RestResponseBuilder.<Application> builder().data(data).build();
    }

    /**
     * Search for an application.
     *
     * @param searchRequest The element that contains criterias for search operation.
     * @return A rest response that contains a {@link FacetedSearchResult} containing applications.
     */
    @ApiOperation(value = "Search for applications", notes = "Returns a search result with that contains applications matching the request. A application is returned only if the connected user has at least one application role in [ APPLICATION_MANAGER | APPLICATION_USER | APPLICATION_DEVOPS | DEPLOYMENT_MANAGER ]")
    @RequestMapping(value = "/search", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public RestResponse<FacetedSearchResult> search(@RequestBody SearchRequest searchRequest) {
        FilterBuilder authorizationFilter = AuthorizationUtil.getResourceAuthorizationFilters();
        FacetedSearchResult searchResult = alienDAO.facetedSearch(Application.class, searchRequest.getQuery(), searchRequest.getFilters(), authorizationFilter,
                null, searchRequest.getFrom(), searchRequest.getSize());
        return RestResponseBuilder.<FacetedSearchResult> builder().data(searchResult).build();
    }

    /**
     * Delete an application based on it's id.
     *
     * @param applicationId The id of the application to delete.
     * @return A rest response.
     */
    @ApiOperation(value = "Delete an application from its id.", notes = "The logged-in user must have the application manager role for this application. Application role required [ APPLICATION_MANAGER ]")
    @RequestMapping(value = "/{applicationId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<Boolean> delete(@PathVariable String applicationId) {
        Application application = applicationService.getOrFail(applicationId);
        AuthorizationUtil.checkAuthorizationForApplication(application, ApplicationRole.APPLICATION_MANAGER);

        try {
            boolean deleted = applicationService.delete(applicationId);
            if (!deleted) {
                throw new DeleteDeployedException(
                        "Application with id <" + applicationId + "> cannot be deleted since one of its environment is still deployed.");
            }
        } catch (OrchestratorDisabledException e) {
            log.error("Failed to delete the application due to Cloud error", e);
            return RestResponseBuilder.<Boolean> builder().data(false).error(RestErrorBuilder.builder(RestErrorCode.CLOUD_DISABLED_ERROR)
                    .message("Could not delete the application with id <" + applicationId + "> with error : " + e.getMessage()).build()).build();

        }
        return RestResponseBuilder.<Boolean> builder().data(true).build();
    }

    /**
     * Update application's image.
     *
     * @param applicationId The application id.
     * @param image new image of the application
     * @return nothing if success, error will be handled in global exception strategy
     */
    @ApiOperation(value = "Updates the image for the application.", notes = "The logged-in user must have the application manager role for this application. Application role required [ APPLICATION_MANAGER ]")
    @RequestMapping(value = "/{applicationId}/image", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<String> updateImage(@PathVariable String applicationId, @RequestParam("file") MultipartFile image) {
        Application data = applicationService.getOrFail(applicationId);
        AuthorizationUtil.checkAuthorizationForApplication(data, ApplicationRole.APPLICATION_MANAGER);
        String imageId;
        try {
            imageId = imageDAO.writeImage(image.getBytes());
        } catch (IOException e) {
            throw new ImageUploadException(
                    "Unable to read image from file upload [" + image.getOriginalFilename() + "] to update application [" + applicationId + "]", e);
        }
        data.setImageId(imageId);
        data.setLastUpdateDate(new Date());
        alienDAO.save(data);
        return RestResponseBuilder.<String> builder().data(imageId).build();
    }

    /**
     * Update application's name
     *
     * @param applicationId The application id.
     * @return nothing if success, error will be handled in global exception strategy
     */
    @ApiOperation(value = "Updates by merging the given request into the given application .", notes = "The logged-in user must have the application manager role for this application. Application role required [ APPLICATION_MANAGER ]")
    @RequestMapping(value = "/{applicationId:.+}", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<Void> update(@PathVariable String applicationId, @RequestBody UpdateApplicationRequest applicationUpdateRequest) {
        Application application = applicationService.getOrFail(applicationId);
        AuthorizationUtil.checkAuthorizationForApplication(application, ApplicationRole.APPLICATION_MANAGER);
        String currentName = application.getName();
        ReflectionUtil.mergeObject(applicationUpdateRequest, application);
        if (application.getName() == null || application.getName().isEmpty()) {
            throw new InvalidArgumentException("Application's name cannot be set to null or empty");
        }
        if (!currentName.equals(application.getName())) {
            applicationService.ensureNameUnicity(application.getName());
        }
        // update updateDate
        application.setLastUpdateDate(new Date());
        alienDAO.save(application);
        return RestResponseBuilder.<Void> builder().build();
    }

}
