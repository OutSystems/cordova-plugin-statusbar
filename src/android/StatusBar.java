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

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Arrays;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONException;

public class StatusBar extends CordovaPlugin {
    private static final String TAG = "StatusBar";
    private static final String CORDOVA_STATIC_CHANNEL = "StatusBarStaticChannel";

    private static final String ACTION_HIDE = "hide";
    private static final String ACTION_SHOW = "show";
    private static final String ACTION_READY = "_ready";
    private static final String ACTION_BACKGROUND_COLOR_BY_HEX_STRING = "backgroundColorByHexString";
    private static final String ACTION_OVERLAYS_WEB_VIEW = "overlaysWebView";
    private static final String ACTION_STYLE_DEFAULT = "styleDefault";
    private static final String ACTION_STYLE_LIGHT_CONTENT = "styleLightContent";
    private static final String ACTION_STYLE_DARK_CONTENT = "styleDarkContent";
    private static final String ACTION_IS_OVERLAYS_WEB_VIEW = "isStatusBarOverlayingWebview";
    private static final String ACTION_GET_HEIGHT = "getStatusBarHeight";

    private static final String STYLE_DEFAULT = "default";
    private static final String STYLE_LIGHT_CONTENT = "lightcontent";
    private static final String STYLE_DARK_CONTENT = "darkcontent";

    private boolean doOverlay;
    private String currentStyle = "";

    private AppCompatActivity activity;
    private Window window;

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

        activity = cordova.getActivity();
        window = activity.getWindow();
        ActivityAssistant.getInstance().assistActivity(activity);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                doOverlay = preferences.getBoolean("StatusBarOverlaysWebView", false);

                // Clear flag FLAG_FORCE_NOT_FULLSCREEN which is set initially
                // by the Cordova.
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
                }

                // Read 'StatusBarStyle' from config.xml, default is 'default'.
                String styleSetting = preferences.getString("StatusBarStyle", "default");
                setStatusBarStyle(styleSetting);
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
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) {
        LOG.v(TAG, "Executing action: " + action);

        switch (action) {
            case ACTION_READY:
                boolean statusBarVisible = (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0;
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, statusBarVisible));
                return true;

            case ACTION_SHOW:
                activity.runOnUiThread(() -> {
                    int uiOptions = window.getDecorView().getSystemUiVisibility();
                    uiOptions &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                    uiOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;

                    window.getDecorView().setSystemUiVisibility(uiOptions);

                    // CB-11197 We still need to update LayoutParams to force status bar
                    // to be hidden when entering e.g. text fields
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                    // Return Ok to execute the onVisibilityChange function
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                });
                return true;

            case ACTION_HIDE:
                activity.runOnUiThread(() -> {
                    int uiOptions = window.getDecorView().getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN;

                    window.getDecorView().setSystemUiVisibility(uiOptions);

                    // CB-11197 We still need to update LayoutParams to force status bar
                    // to be hidden when entering e.g. text fields
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                    // Return Ok to execute the onVisibilityChange function
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                });
                return true;

            case ACTION_BACKGROUND_COLOR_BY_HEX_STRING:
                activity.runOnUiThread(() -> {
                    try {
                        setStatusBarBackgroundColor(args.getString(0));
                    } catch (JSONException ignore) {
                        LOG.e(TAG, "Invalid hexString argument, use f.i. '#777777'");
                    }
                });
                return true;


            case ACTION_OVERLAYS_WEB_VIEW:
                activity.runOnUiThread(() -> {
                    try {
                        doOverlay = args.getBoolean(0);
                    } catch (JSONException ignore) {
                        LOG.e(TAG, "Invalid boolean argument");
                    }

                    setStatusBarTransparent(doOverlay);

                    if (doOverlay) {
                        ActivityAssistant.getInstance().applyGlobalLayoutListener();
                    }
                });
                return true;

            case ACTION_STYLE_DEFAULT:
                activity.runOnUiThread(() -> setStatusBarStyle(STYLE_DEFAULT));
                return true;

            case ACTION_STYLE_LIGHT_CONTENT:
                activity.runOnUiThread(() -> setStatusBarStyle(STYLE_LIGHT_CONTENT));
                return true;

            case ACTION_STYLE_DARK_CONTENT:
                activity.runOnUiThread(() -> setStatusBarStyle(STYLE_DARK_CONTENT));
                return true;

            case ACTION_IS_OVERLAYS_WEB_VIEW:
                boolean isVisible = (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0;
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, doOverlay && isVisible));
                return true;

            case ACTION_GET_HEIGHT:
                int statusBarHeight = getStatusBarHeight();
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, statusBarHeight));
                return true;
        }

        return false;
    }

    // Only used with API 21+
    private void setStatusBarBackgroundColor(final String colorPref) {
        if (colorPref.isEmpty()) return;

        int color;
        try {
            color = Color.parseColor(colorPref);
        } catch (IllegalArgumentException ignore) {
            LOG.e(TAG, "Invalid hexString argument, use f.i. '#999999'");
            return;
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // SDK 19-30
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS); // SDK 21
        window.setStatusBarColor(color);
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

    private void setStatusBarTransparent(final boolean isTransparent) {
        int visibility = isTransparent
                ? View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                : View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_VISIBLE;

        window.getDecorView().setSystemUiVisibility(visibility);

        if (isTransparent) {
            window.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    private void setStatusBarStyle(String style) {
        if (!style.isEmpty()) {
            this.currentStyle = style;
            View decorView = window.getDecorView();
            WindowInsetsControllerCompat windowInsetsControllerCompat = WindowCompat.getInsetsController(window, decorView);

            if (style.equals(STYLE_DEFAULT)) {
                style = getStyleFromDeviceTheme();
            }
            if (style.equals(STYLE_DARK_CONTENT)) {
                windowInsetsControllerCompat.setAppearanceLightStatusBars(true);
            } else if (style.equals(STYLE_LIGHT_CONTENT)) {
                windowInsetsControllerCompat.setAppearanceLightStatusBars(false);
            } else {
                LOG.e(TAG, "Invalid style, must be either 'default', 'lightcontent' or 'darkcontent'");
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (this.currentStyle.equals(STYLE_DEFAULT))
            setStatusBarStyle(STYLE_DEFAULT);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
        pluginResult.setKeepCallback(true);
        webView.sendPluginResult(pluginResult, CORDOVA_STATIC_CHANNEL);
    }

    private String getStyleFromDeviceTheme() {
        int nightModeFlags = cordova.getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
            default:
                return STYLE_LIGHT_CONTENT;

            case Configuration.UI_MODE_NIGHT_NO:
                return STYLE_DARK_CONTENT;
        }
    }
}
