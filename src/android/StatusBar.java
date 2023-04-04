/*
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
package org.apache.cordova.statusbar;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import java.util.Arrays;

public class StatusBar extends CordovaPlugin {
    private static final String TAG = "StatusBar";
    private static final String CORDOVA_STATIC_CHANNEL = "StatusBarStaticChannel";

    private boolean doOverlay;

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    @Override
    public void initialize(final CordovaInterface cordova, CordovaWebView webView) {
        LOG.v(TAG, "StatusBar: initialization");
        super.initialize(cordova, webView);

        Activity activity = cordova.getActivity();
        ActivityAssistant.getInstance().assistActivity(activity);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                doOverlay = preferences.getBoolean("StatusBarOverlaysWebView", false);

                // Clear flag FLAG_FORCE_NOT_FULLSCREEN which is set initially
                // by the Cordova.
                Window window = cordova.getActivity().getWindow();
                window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

                // Allows app to overlap cutout area from device when in landscape mode (same as iOS)
                // More info: https://developer.android.com/reference/android/R.attr.html#windowLayoutInDisplayCutoutMode
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }

                // Added to override logic if plugin is installed in OutSystems Now app.
                boolean isOutSystemsNow = preferences.getBoolean("IsOutSystemsNow", false);

                if(isOutSystemsNow || (doOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)){
                    // Read 'StatusBarOverlaysWebView' from config.xml, and if the value is true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setStatusBarTransparent(doOverlay);
                    }
                    else if(Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    }
                    else{
                        LOG.e(TAG, "Translucent status bar not supported in your Android version");
                    }

                    ActivityAssistant.getInstance().applyGlobalLayoutListener();
                } else {
                    // Read 'StatusBarBackgroundColor' from config.xml, default is #000000.
                    setStatusBarBackgroundColor(preferences.getString("StatusBarBackgroundColor", "#000000"));

                    // Read 'StatusBarStyle' from config.xml, default is 'lightcontent'.
                    String styleSetting = preferences.getString("StatusBarStyle", "lightcontent");
                    if (styleSetting.equalsIgnoreCase("blacktranslucent") || styleSetting.equalsIgnoreCase("blackopaque")) {
                        LOG.w(TAG, styleSetting +" is deprecated and will be removed in next major release, use lightcontent");
                    }
                    setStatusBarStyle(styleSetting);
                }
            }
        });
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     */
    @Override
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        LOG.v(TAG, "Executing action: " + action);
        final Activity activity = this.cordova.getActivity();
        final Window window = activity.getWindow();

        if ("_ready".equals(action)) {
            boolean statusBarVisible = (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0;
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, statusBarVisible));
            return true;
        }

        if("isStatusBarOverlayingWebview".equals(action)) {
            boolean statusBarVisible = (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0;
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, doOverlay && statusBarVisible));
            return true;
        }

        if ("show".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // SYSTEM_UI_FLAG_FULLSCREEN is available since JellyBean, but we
                    // use KitKat here to be aligned with "Fullscreen"  preference
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        int uiOptions = window.getDecorView().getSystemUiVisibility();
                        uiOptions &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                        uiOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;

                        window.getDecorView().setSystemUiVisibility(uiOptions);
                    }

                    // CB-11197 We still need to update LayoutParams to force status bar
                    // to be hidden when entering e.g. text fields
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                    // Return Ok to execute the onVisibilityChange function
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                }
            });
            return true;
        }

        if ("hide".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // SYSTEM_UI_FLAG_FULLSCREEN is available since JellyBean, but we
                    // use KitKat here to be aligned with "Fullscreen"  preference
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        int uiOptions = window.getDecorView().getSystemUiVisibility()
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_FULLSCREEN;

                        window.getDecorView().setSystemUiVisibility(uiOptions);
                    }

                    // CB-11197 We still need to update LayoutParams to force status bar
                    // to be hidden when entering e.g. text fields
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                    // Return Ok to execute the onVisibilityChange function
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                }
            });
            return true;
        }

        if ("backgroundColorByHexString".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        setStatusBarBackgroundColor(args.getString(0));
                    } catch (JSONException ignore) {
                        LOG.e(TAG, "Invalid hexString argument, use f.i. '#777777'");
                    }
                }
            });
            return true;
        }

        if ("getStatusBarHeight".equals(action)) {
            int statusBarHeight = getStatusBarHeight();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, statusBarHeight));
            return true;
        }

        if ("overlaysWebView".equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                this.cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            doOverlay = args.getBoolean(0);
                        } catch (JSONException ignore) {
                            LOG.e(TAG, "Invalid boolean argument, please use true or false values");
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            setStatusBarTransparent(doOverlay);
                        } else if(Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                            if(doOverlay) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                            }
                        }
                        else {
                            LOG.e(TAG, "Translucent status bar not supported in your Android version");
                        }

                        if(doOverlay) {
                            ActivityAssistant.getInstance().applyGlobalLayoutListener();
                        }
                    }
                });
                return true;
            }
            else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, doOverlay));
                return doOverlay == false;
            }
        }

        if ("styleDefault".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setStatusBarStyle("default");
                }
            });
            return true;
        }

        if ("styleLightContent".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setStatusBarStyle("lightcontent");
                }
            });
            return true;
        }

        if ("styleBlackTranslucent".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setStatusBarStyle("blacktranslucent");
                }
            });
            return true;
        }

        if ("styleBlackOpaque".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setStatusBarStyle("blackopaque");
                }
            });
            return true;
        }

        return false;
    }

    // Only used with API 21+
    private void setStatusBarBackgroundColor(final String colorPref) {
        if (Build.VERSION.SDK_INT >= 21) {
            if (colorPref != null && !colorPref.isEmpty()) {
                final Window window = cordova.getActivity().getWindow();
                // Method and constants not available on all SDKs but we want to be able to compile this code with any SDK
                window.clearFlags(0x04000000); // SDK 19: WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.addFlags(0x80000000); // SDK 21: WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                try {
                    // Using reflection makes sure any 5.0+ device will work without having to compile with SDK level 21
                    window.getClass().getMethod("setStatusBarColor", int.class).invoke(window, Color.parseColor(colorPref));
                } catch (IllegalArgumentException ignore) {
                    LOG.e(TAG, "Invalid hexString argument, use f.i. '#999999'");
                } catch (Exception ignore) {
                    // this should not happen, only in case Android removes this method in a version > 21
                    LOG.w(TAG, "Method window.setStatusBarColor not found for SDK level " + Build.VERSION.SDK_INT);
                }
            }
        }
    }

    // A method to find height of the status bar
    public int getStatusBarHeight() {

        int statusbarHeight = 0;
        int resourceId = this.cordova.getActivity().getApplicationContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusbarHeight =  (int)this.cordova.getActivity().getApplicationContext().getResources().getDimension(resourceId);
        }

        DisplayMetrics metrics = this.cordova.getActivity().getApplicationContext().getResources().getDisplayMetrics();
        float densityDpi = metrics.density;

        int result = (int)(statusbarHeight / densityDpi);

        return result;
    }

    // Only used with API 21+
    private void setStatusBarTransparent(final boolean transparent) {
        if (Build.VERSION.SDK_INT >= 21) {
            final Window window = cordova.getActivity().getWindow();
            if (transparent) {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                window.setStatusBarColor(Color.TRANSPARENT);
            }
            else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    private void setStatusBarStyle(final String style) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (style != null && !style.isEmpty()) {
                Window window = cordova.getActivity().getWindow();
                View decorView = window.getDecorView();
                WindowInsetsControllerCompat windowInsetsControllerCompat = WindowCompat.getInsetsController(window, decorView);

                String[] darkContentStyles = {
                        "default",
                };

                String[] lightContentStyles = {
                        "lightcontent",
                        "blacktranslucent",
                        "blackopaque",
                };

                if (Arrays.asList(darkContentStyles).contains(style.toLowerCase())) {
                    windowInsetsControllerCompat.setAppearanceLightStatusBars(true);
                    return;
                }

                if (Arrays.asList(lightContentStyles).contains(style.toLowerCase())) {
                    windowInsetsControllerCompat.setAppearanceLightStatusBars(false);
                    return;
                }

                LOG.e(TAG, "Invalid style, must be either 'default', 'lightcontent' or the deprecated 'blacktranslucent' and 'blackopaque'");
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
        pluginResult.setKeepCallback(true);
        webView.sendPluginResult(pluginResult, CORDOVA_STATIC_CHANNEL);
    }

}
