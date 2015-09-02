/* global by, element */
'use strict';

var authentication = require('../../authentication/authentication');
var common = require('../../common/common');
var applications = require('../../applications/applications');
var cloudsCommon = require('../../admin/clouds_common');
var navigation = require('../../common/navigation');
var topologyEditorCommon = require('../../topology/topology_editor_common');
var componentData = require('../../topology/component_data');
var rolesCommon = require('../../common/roles_common');

function assertCountEnvironment(expectedCount) {
  var environments = element.all(by.repeater('environment in searchAppEnvResult'));
  expect(environments.count()).toEqual(expectedCount);
}

describe('Application environments security check', function() {

  /* Before each spec in the tests suite */
  beforeEach(function() {
    topologyEditorCommon.beforeTopologyTest();
  });

  /* After each spec in the tests suite(s) */
  afterEach(function() {
    // Logout action
    authentication.logout();
  });

  it('should be able to deploy on default Environment and check output properties / attributes on deployment / info page', function() {
    console.log('################# should be able to deploy on default Environment and check output properties / attributes on deployment / info page');

    cloudsCommon.giveRightsOnCloudToUser('testcloud', 'applicationManager', rolesCommon.cloudRoles.cloudDeployer);
    navigation.go('main', 'applications');
    browser.element(by.binding('application.name')).click();
    navigation.go('applications', 'topology');
    topologyEditorCommon.addNodeTemplatesCenterAndZoom({
      compute: componentData.toscaBaseTypes.compute()
    });
    topologyEditorCommon.editNodeProperty('Compute', 'os_arch', 'x86_64');
    topologyEditorCommon.editNodeProperty('Compute', 'os_type', 'windows');
    topologyEditorCommon.editNodeProperty('Compute', 'containee_types', 'test', 'cap');
    topologyEditorCommon.editNodeProperty('Compute', 'disk_size', '1024', 'pro', 'MIB');

    // check properties / attributes as output
    topologyEditorCommon.togglePropertyOutput('Compute', 'disk_size');
    topologyEditorCommon.togglePropertyOutput('Compute', 'os_type');
    topologyEditorCommon.toggleAttributeOutput('Compute', 'ip_address');
    topologyEditorCommon.toggleAttributeOutput('Compute', 'tosca_name');

    // check after toggle
    topologyEditorCommon.expectPropertyOutputState('Compute', 'os_arch', false);
    topologyEditorCommon.expectPropertyOutputState('Compute', 'disk_size', true);
    topologyEditorCommon.expectPropertyOutputState('Compute', 'os_type', true);
    topologyEditorCommon.expectAttributeOutputState('Compute', 'ip_address', true);
    topologyEditorCommon.expectAttributeOutputState('Compute', 'tosca_name', true);
    topologyEditorCommon.expectAttributeOutputState('Compute', 'ip_address', true);
    topologyEditorCommon.expectAttributeOutputState('Compute', 'tosca_id', false);

    // Deploy the app
    applications.deploy('Alien', null, null, null, applications.mockPaaSDeploymentProperties);
    var btnUndeploy = common.waitElement(by.id('btn-undeploy'));
    // browser.sleep(10000); // Wait for mock deployment to finish

    // checking deployment page
    applications.expectOutputValue('deployment', null, 'attribute', 'Compute', 1, 'ip_address', '10.52.0.1');
    applications.expectOutputValue('deployment', null, 'attribute', 'Compute', 1, 'tosca_name', 'TOSCA-Simple-Profile-YAML');
    applications.expectOutputValue('deployment', null, 'property', 'Compute', 1, 'disk_size', '1024');
    applications.expectOutputValue('deployment', null, 'property', 'Compute', 1, 'os_type', 'windows');

    // check on info page
    applications.expectOutputValue('info', null, 'attribute', 'Compute', 1, 'ip_address', '10.52.0.1');
    applications.expectOutputValue('info', null, 'attribute', 'Compute', 1, 'tosca_name', 'TOSCA-Simple-Profile-YAML');
    applications.expectOutputValue('info', null, 'property', 'Compute', 1, 'disk_size', '1024');
    applications.expectOutputValue('info', null, 'property', 'Compute', 1, 'os_type', 'windows');

  });

});
