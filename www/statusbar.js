/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

/* global cordova */

var exec = require('cordova/exec');
var channel = require('cordova/channel');

var IOS_11_VERSION = 11;

var namedColors = {
    black: '#000000',
    darkGray: '#A9A9A9',
    lightGray: '#D3D3D3',
    white: '#FFFFFF',
    gray: '#808080',
    red: '#FF0000',
    green: '#00FF00',
    blue: '#0000FF',
    cyan: '#00FFFF',
    yellow: '#FFFF00',
    magenta: '#FF00FF',
    orange: '#FFA500',
    purple: '#800080',
    brown: '#A52A2A'
};

var StatusBar = {
    isVisible: true,

    disableViewportFitiOS12: false,

    overlaysWebView: function (doOverlay) {
        exec(checkIfStatusBarOverlaysWebview, null, 'StatusBar', 'overlaysWebView', [doOverlay]);
    },

    styleDefault: function () {
        // dark text ( to be used on a light background and on iOS automatically chooses light or dark content based on the user interface style)
        exec(null, null, 'StatusBar', 'styleDefault', []);
    },

    styleLightContent: function () {
        // light text ( to be used on a dark background )
        exec(null, null, 'StatusBar', 'styleLightContent', []);
    },

    styleDarkContent: function () {
        // dark text ( to be used on a light background )
        exec(null, null, 'StatusBar', 'styleDarkContent', []);
    },

    backgroundColorByName: function (colorname) {
        return StatusBar.backgroundColorByHexString(namedColors[colorname]);
    },

    backgroundColorByHexString: function (hexString) {
        if (hexString.charAt(0) !== '#') {
            hexString = '#' + hexString;
        }

        if (hexString.length === 4) {
            var split = hexString.split('');
            hexString = '#' + split[1] + split[1] + split[2] + split[2] + split[3] + split[3];
        }

        exec(null, null, 'StatusBar', 'backgroundColorByHexString', [hexString]);
    },

    hide: function () {
        exec(onVisibilityChange, null, 'StatusBar', 'hide', []);
        StatusBar.isVisible = false;
    },

    show: function () {
        exec(onVisibilityChange, null, 'StatusBar', 'show', []);
        StatusBar.isVisible = true;
    }
};

var onVisibilityChange = function () {
    exec(checkIfStatusBarOverlaysWebview,
        null,
        'StatusBar',
        'isStatusBarOverlayingWebview',
        []);
};

var checkIfStatusBarOverlaysWebview = function (overlaying) {
    var overlayClassName = 'statusbar-overlay';

    var statusBarOverlaysWebview = document.body.className.indexOf(overlayClassName);

    if (StatusBar.isVisible) {
        if (overlaying) {
            if (statusBarOverlaysWebview < 0) {
                document.body.className += (document.body.className.length > 0 ? ' ' : '') + overlayClassName;
            }

            addStatusBarDataElement();
        } else {
            document.body.className = document.body.className.replace(overlayClassName, '').trim();
            document.body.removeAttribute('data-status-bar-height');
        }
    } else {
        if (statusBarOverlaysWebview >= 0) {
            document.body.className = document.body.className.replace(overlayClassName, '').trim();
            document.body.removeAttribute('data-status-bar-height');
        }
    }
};

var addStatusBarDataElement = function () {
    var getStatusBarHeight = function (height) {
        document.body.setAttribute('data-status-bar-height', height);
        document.body.style.setProperty('--status-bar-height', height + 'px');
    };

    exec(getStatusBarHeight,
        null,
        'StatusBar',
        'getStatusBarHeight',
        []);
};

var injectViewportMetaTag = function () {
    if (/(iPad)|(iPhone)/i.test(navigator.userAgent)) {
        var version = navigator.appVersion.match(/OS (\d+)_(\d+)_?(\d+)?/);

        if (Array.isArray(version) && version.length > 1 && !isNaN(version[1])) {
            if (Number(version[1]) === IOS_11_VERSION || (Number(version[1]) > IOS_11_VERSION && !StatusBar.disableViewportFitiOS12)) {
                var viewportMetaElem = document.getElementsByTagName('meta').namedItem('viewport');

                if (viewportMetaElem && !viewportMetaElem.content.includes('viewport-fit')) {
                    viewportMetaElem.setAttribute('content', 'viewport-fit=cover,' + viewportMetaElem.content);
                }
            }
        }
    }
};

module.exports = StatusBar;

// Called after 'deviceready' event
channel.deviceready.subscribe(function () {
    exec(function (res) {
        if (typeof res === 'object') {
            if (res.type === 'tap') {
                cordova.fireWindowEvent('statusTap');
            } else {
                if (res.type === 'viewport') {
                    StatusBar.disableViewportFitiOS12 = res.disableiOS12;
                    injectViewportMetaTag();
                }
            }
        } else {
            StatusBar.isVisible = res;
        }
    }, null, 'StatusBar', '_ready', []);

    onVisibilityChange();
});

// Called by the native side when a configuration change
cordova.callbacks.StatusBarStaticChannel = {
    success: function () {
        onVisibilityChange();
    },
    fail: function () {
    }
};
