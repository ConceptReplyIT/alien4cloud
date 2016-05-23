/** Group management for topology edition. */
define(function (require) {
  'use strict';
  var modules = require('modules');
  var _ = require('lodash');

  modules.get('a4c-topology-editor').factory('topoEditGroups', [ 'topologyServices', 'runtimeColorsService',
    function(topologyServices, runtimeColorsService) {
      var TopologyEditorMixin = function(scope) {
        this.scope = scope;
      };

      TopologyEditorMixin.prototype = {
        constructor: TopologyEditorMixin,
        delete: function(groupId) {
          var instance = this;
          topologyServices.nodeGroups.remove({
            topologyId: instance.scope.topology.topology.id,
            groupId: groupId
          }, {}, function(result) {
            if (!result.error) {
              instance.scope.refreshTopology(result.data);
            }
          });
        },
        updateName: function(groupId, name) {
          var instance = this;
          topologyServices.nodeGroups.rename({
            topologyId: instance.scope.topology.topology.id,
            groupId: groupId
          }, { newName: name }, function(result) {
            if (!result.error) {
              instance.scope.refreshTopology(result.data);
              if (instance.scope.groupCollapsed[groupId]) {
                instance.scope.groupCollapsed[name] = instance.scope.groupCollapsed[groupId];
                delete instance.scope.groupCollapsed[groupId];
              }
            }
          });
          // FIXME at this moment you may have errors in the browser console due to the fact that the topology has not been refreshed.
          // Scope apply should be suspended and triggered only when topology is refreshed.
        },
        removeMember: function(groupId, member) {
          var instance = this;
          topologyServices.nodeGroups.removeMember({
            topologyId: instance.scope.topology.topology.id,
            groupId: groupId,
            nodeTemplateName: member
          }, {}, function(result) {
            if (!result.error) {
              instance.scope.refreshTopology(result.data);
            }
          });
        },

        isMemberOf: function(nodeName, groupId) {
          if (this.scope.selectedNodeTemplate) {
            return _.contains(this.scope.selectedNodeTemplate.groups, groupId);
          }
        },

        create: function(nodeName) {
          var instance = this;
          topologyServices.nodeGroups.addMember({
            topologyId: instance.scope.topology.topology.id,
            groupId: nodeName,
            nodeTemplateName: nodeName
          }, {}, function(result) {
            if (!result.error) {
              instance.scope.refreshTopology(result.data);
              instance.scope.groupCollapsed[nodeName] = { main: false, members: true, policies: true };
            }
          });
        },
        toggleMember: function(groupId, nodeName) {
          var instance = this;
          if (this.isMemberOf(nodeName, groupId)) {
            this.removeMember(groupId, nodeName);
          } else {
            topologyServices.nodeGroups.addMember({
              topologyId: instance.scope.topology.topology.id,
              groupId: groupId,
              nodeTemplateName: nodeName
            }, {}, function(result) {
              if (!result.error) {
                instance.scope.refreshTopology(result.data);
              }
            });
          }
        },

        getColorCss: function(groupId) {
          return runtimeColorsService.groupColorCss(this.scope.topology.topology, groupId);
        }
      };

      return function(scope) {
        var instance = new TopologyEditorMixin(scope);
        scope.groups = instance;
      };
    }
  ]); // modules
}); // define
