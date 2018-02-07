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

var IOS_11_VERSION = "11";
var IOS_PLATFORM = "ios";

var namedColors = {
    "black": "#000000",
    "darkGray": "#A9A9A9",
    "lightGray": "#D3D3D3",
    "white": "#FFFFFF",
    "gray": "#808080",
    "red": "#FF0000",
    "green": "#00FF00",
    "blue": "#0000FF",
    "cyan": "#00FFFF",
    "yellow": "#FFFF00",
    "magenta": "#FF00FF",
    "orange": "#FFA500",
    "purple": "#800080",
    "brown": "#A52A2A"
};

var StatusBar = {

    isVisible: true,

    overlaysWebView: function (doOverlay) {
        exec(checkIfStatusBarOverlaysWebview, null, "StatusBar", "overlaysWebView", [doOverlay]);
    },

    styleDefault: function () {
        // dark text ( to be used on a light background )
        exec(null, null, "StatusBar", "styleDefault", []);
    },

    styleLightContent: function () {
        // light text ( to be used on a dark background )
        exec(null, null, "StatusBar", "styleLightContent", []);
    },

    styleBlackTranslucent: function () {
        // #88000000 ? Apple says to use lightContent instead
        exec(null, null, "StatusBar", "styleBlackTranslucent", []);
    },

    styleBlackOpaque: function () {
        // #FF000000 ? Apple says to use lightContent instead
        exec(null, null, "StatusBar", "styleBlackOpaque", []);
    },

    backgroundColorByName: function (colorname) {
        return StatusBar.backgroundColorByHexString(namedColors[colorname]);
    },

    backgroundColorByHexString: function (hexString) {
        if (hexString.charAt(0) !== "#") {
            hexString = "#" + hexString;
        }

        if (hexString.length === 4) {
            var split = hexString.split("");
            hexString = "#" + split[1] + split[1] + split[2] + split[2] + split[3] + split[3];
        }

        exec(null, null, "StatusBar", "backgroundColorByHexString", [hexString]);
    },

    hide: function () {
        exec(onVisibilityChange, null, "StatusBar", "hide", []);
        StatusBar.isVisible = false;
    },

    show: function () {
        exec(onVisibilityChange, null, "StatusBar", "show", []);
        StatusBar.isVisible = true;
    }

};


var onVisibilityChange = function (){
  
    exec(checkIfStatusBarOverlaysWebview, 
         null,
         "StatusBar", 
         "isStatusBarOverlayingWebview",
        []);

}


var checkIfStatusBarOverlaysWebview = function(overlaying){

    var overlayClassName = "statusbar-overlay";

    var statusBarOverlaysWebview = document.body.className.indexOf(overlayClassName);

    if(StatusBar.isVisible){
        if(overlaying){
            if(statusBarOverlaysWebview < 0){
                document.body.className += (document.body.className.length > 0 ? " ":"")+overlayClassName;
            }  

            addStatusBarDataElement();
        }
        else{
            document.body.className = document.body.className.replace(overlayClassName,"").trim();
            document.body.removeAttribute("data-status-bar-height");
        }      
    }
    else {
        if(statusBarOverlaysWebview >= 0){
            document.body.className = document.body.className.replace(overlayClassName,"").trim();
            document.body.removeAttribute("data-status-bar-height");
        }         
    }

}


var addStatusBarDataElement = function(){
   
    var getStatusBarHeight = function (height){
        document.body.setAttribute("data-status-bar-height",height);
    };

    exec(getStatusBarHeight, 
         null,
         "StatusBar", 
         "getStatusBarHeight",
        []);
}


var injectViewportMetaTag = function(){

    if(device.platform.toLowerCase().indexOf(IOS_PLATFORM) == 0 && device.version.split(".")[0].indexOf(IOS_11_VERSION) == 0){
        var viewportMetaElem = document.getElementsByTagName("meta").namedItem("viewport");

        if(viewportMetaElem){
            if(!viewportMetaElem.content.includes("viewport-fit")){
                viewportMetaElem.setAttribute("content", "viewport-fit=cover," + viewportMetaElem.content)
            }
        }
    }

}


module.exports = StatusBar;


// Called after 'deviceready' event
channel.deviceready.subscribe(function () {

    exec(function (res) {
            if (typeof res == 'object') {
                if (res.type == 'tap') {
                    cordova.fireWindowEvent('statusTap');
                }
            } else {
                StatusBar.isVisible = res;
            }
        }, null, "StatusBar", "_ready", []);
    
  
    onVisibilityChange();

    injectViewportMetaTag();

});
