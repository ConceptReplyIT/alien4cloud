/* global browser, protractor, by, element */

'use strict';

var screenshot = require('./screenshot');

// Load languages strings for locale related tests
var frLanguage = require(__dirname + '/../../../../main/webapp/data/languages/locale-fr-fr.json');
var usLanguage = require(__dirname + '/../../../../main/webapp/data/languages/locale-en-us.json');
module.exports.frLanguage = frLanguage;
module.exports.usLanguage = usLanguage;

function log(message) {
  browser.sleep(0).then(function() {
    console.log(message);
  });
}
module.exports.log = log;

var navigationIds = {
  main: {
    applications: 'menu.applications',
    topologyTemplates: 'menu.topologytemplates',
    components: 'menu.components',
    admin: 'menu.admin'
  },
  admin: {
    users: 'am.admin.users',
    plugins: 'am.admin.plugins',
    meta: 'am.admin.metaprops',
    clouds: 'am.admin.clouds',
    'cloud-images': 'am.admin.cloud-images.list'
  },
  applications: {
    info: 'am.applications.info',
    topology: 'am.applications.detail.topology',
    plan: 'am.applications.detail.plans',
    deployment: 'am.applications.detail.deployment',
    runtime: 'am.applications.detail.runtime',
    users: 'am.applications.detail.users',
    versions: 'am.applications.detail.versions',
    environments: 'am.applications.detail.environments'
  },
  applicationDeployment: {
    location: 'am.applications.detail.deployment.locations',
    substitution: 'am.applications.detail.deployment.match',
    input: 'am.applications.detail.deployment.input',
    deploy: 'am.applications.detail.deployment.deploy'
  },
  components: {
    components: 'cm.components',
    csars: 'cm.components.csars.list',
    git: 'cm.components.git'
  }
};

module.exports.home = function() {
  browser.get('#/');
  browser.waitForAngular();
};

module.exports.go = function(menu, menuItem) {
  browser.element(by.id(navigationIds[menu][menuItem])).click();
  browser.waitForAngular();
};

module.exports.isPresentButDisabled = function(menu, menuItem) {
  var menuItem = element(by.id(navigationIds[menu][menuItem]));
  expect(menuItem.isDisplayed()).toBe(true);
  expect(menuItem.getAttribute('class')).toContain('disabled');
};

module.exports.isNavigable = function(menu, menuItem) {
  var menuItem = element(by.id(navigationIds[menu][menuItem]));
  expect(menuItem.isDisplayed()).toBe(true);
  expect(menuItem.getAttribute('class')).not.toContain('disabled');
};

module.exports.isNotNavigable = function(menu, menuItem) {
  var menuItem = element(by.id(navigationIds[menu][menuItem]));
  expect(menuItem.isPresent()).toBe(false);
};

// Common utilities to work with protractor
function wElement(selector, fromElement, timeout) {
  var selectorStr = selector.toString();
  // wait for the element to be there for 3 sec
  var timeoutMsg = 'Timed out when waiting for element using selector ' + selectorStr;
  var elementToWait;
  if (fromElement && fromElement !== null) {
    elementToWait = fromElement.element(selector);
  } else {
    elementToWait = browser.element(selector);
  }

  if (!timeout) {
    timeout = 3000;
  }

  browser.wait(function() {
    var deferred = protractor.promise.defer();
    var isPresentPromise = elementToWait.isPresent();
    isPresentPromise.then(function(isPresent) {
      if (!isPresent) {
        log('waiting for element using selector ' + selectorStr);
      }
      deferred.fulfill(isPresent);
    });
    return deferred.promise;
  }, timeout, timeoutMsg);

  return elementToWait;
}
module.exports.element = wElement;

function click(selector, fromElement, skipWaitAngular, scrollTo) {
  var target = wElement(selector, fromElement);
  if(scrollTo) {
    // first let's scroll to the element position taking the navbar into account
    browser.wait(function() {
      var deferred = protractor.promise.defer();
      var location = target.getLocation();
      browser.executeScript('window.scrollTo(' + location.x + ',' + location.y + ');').then(function () {
          deferred.fulfill(true);
      });
      return deferred.promise;
    }, 3000, 'Timed out while scrolling to element for click');
  }
  browser.actions().click(target).perform();
  if (!skipWaitAngular) {
    browser.waitForAngular();
  }
  return target;
}
module.exports.click = click;

module.exports.clear = function(selector, fromElement) {
  var target = wElement(selector, fromElement);
  target.clear();
};

module.exports.sendKeys = function(selector, keys, fromElement) {
  var target = wElement(selector, fromElement);
  target.clear();
  target.sendKeys(keys);
};

module.exports.select = function(selector, value) {
  var selectElement = wElement(selector);
  selectElement.all(by.tagName('option')).then(function() {
    var desiredOption;
    selectElement.all(by.tagName('option'))
      .then(function findMatchingOption(options) {
        options.some(function(option) {
          option.getText().then(function doesOptionMatch(text) {
            if (text.trim() === value) {
              desiredOption = option;
              return true;
            }
          });
        });
      })
      .then(function clickOption() {
        if (desiredOption) {
          desiredOption.click();
        }
      });
  });
};

module.exports.selectBSDropdown = function(selector, value) {
  var selectElement = wElement(selector);
  var selectElementButton = selectElement.element(by.tagName('button'));
  selectElementButton.getText().then(function doesOptionMatch(text) {
    if (text.trim() !== value) {
      selectElementButton.click();
      var selectElementList = selectElement.element(by.tagName('ul'));
      selectElementList.all(by.tagName('li')).then(function() {
        var desiredOption;
        selectElementList.all(by.tagName('li'))
          .then(function findMatchingOption(options) {
            options.some(function(option) {
              option.getText().then(function doesOptionMatch(text) {
                if (text.trim() === value) {
                  desiredOption = option;
                  return true;
                }
              });
            });
          })
          .then(function clickOption() {
            if (desiredOption) {
              desiredOption.element(by.tagName('a')).click();
            }
          });
      });
    }
  });
};

var confirmAction = function(confirm) {
  // get popover div and grab the Yes / No buttons
  var divPopover = wElement(by.css('.popover'));
  if (confirm === true) { // confirm deletion
    click(by.css('.btn-success'), divPopover);
  } else { // cancel
    click(by.css('.btn-danger'), divPopover);
  }
};
module.exports.confirmAction = confirmAction;

// Remove an element with confirm popover
// The button/a generated by <delete-confirm/> directive has no id
// we have to get the directive child <a> to click on it (or not)
var deleteWithConfirm = function(deleteConfirmDirectiveId, confirm) {
  var deleteConfirm = wElement(by.id(deleteConfirmDirectiveId));
  click(by.tagName('a'), deleteConfirm);
  confirmAction(confirm);
};
module.exports.deleteWithConfirm = deleteWithConfirm;

module.exports.uploadFile = function(path) {
  browser.driver.executeScript('var $scope = angular.element($(\'#fileUpload\')).scope(); $scope.dropSupported=false; $scope.$apply();').then(function() {
    browser.waitForAngular();
    var fileInput = browser.element(by.css('input[type="file"]'));
    fileInput.sendKeys(path);
    screenshot.take('upload');
    browser.waitForAngular();
  });
  browser.waitForAngular();
};

// For a SELECT element : select by value
// WARNING : no error is the item is not found
var selectDropdownByText = function selectOption(selectElement, item) {
  var desiredOption = null;
  var deferred = protractor.promise.defer();
  selectElement.all(by.tagName('option'))
    .then(function findMatchingOption(options) {
      options.some(function(option) {
        option.getText().then(function doesOptionMatch(text) {
          if (text.indexOf(item) !== -1) {
            desiredOption = option;
            return true;
          }
        });
      });
    })
    .then(function clickOption() {
      var itemFoundInSelect = false;
      if (desiredOption) {
        desiredOption.click();
        itemFoundInSelect = true;
      } else {
        console.error('Desired item {', item, '} not found in the select');
      }
      deferred.fulfill(itemFoundInSelect);
    });

  // waiting time after selection
  var milliseconds = 300;
  if (typeof milliseconds !== 'undefined') {
    browser.sleep(milliseconds);
  }
  return deferred.promise;
};
module.exports.selectDropdownByText = selectDropdownByText;

// For a SELECT element : return select count
var selectCount = function selectCount(selectId) {
  var deferred = protractor.promise.defer();
  var selectOptions = element.all(by.css('select[id="' + selectId + '"] option'));
  selectOptions.count().then(function(count) {
    deferred.fulfill(count);
  });
  return deferred.promise;
};
module.exports.selectCount = selectCount;
