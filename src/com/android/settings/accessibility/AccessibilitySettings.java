/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import java.io.File;
import android.os.FileUtils;
import java.io.IOException;
import android.util.Log;

import com.android.internal.content.PackageMonitor;
import com.android.internal.view.RotationPolicy;
import com.android.internal.view.RotationPolicy.RotationPolicyListener;
import com.android.settings.DialogCreatable;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import com.actions.hardware.PerformanceManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

/**
 * Activity with the accessibility settings.
 */
public class AccessibilitySettings extends SettingsPreferenceFragment implements DialogCreatable,
        Preference.OnPreferenceChangeListener {
    private static final String LOG_TAG = "AccessibilitySettings";

    private static final String DEFAULT_SCREENREADER_MARKET_LINK =
            "market://search?q=pname:com.google.android.marvin.talkback";

    private static final float LARGE_FONT_SCALE = 1.3f;

    private static final String SYSTEM_PROPERTY_MARKET_URL = "ro.screenreader.market";

    static final char ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR = ':';

    private static final String KEY_INSTALL_ACCESSIBILITY_SERVICE_OFFERED_ONCE =
            "key_install_accessibility_service_offered_once";

    // Preference categories
    private static final String SERVICES_CATEGORY = "services_category";
    private static final String SYSTEM_CATEGORY = "system_category";

    // Preferences
    private static final String TOGGLE_LARGE_TEXT_PREFERENCE =
            "toggle_large_text_preference";
    private static final String TOGGLE_POWER_BUTTON_ENDS_CALL_PREFERENCE =
        "toggle_power_button_ends_call_preference";
    private static final String TOGGLE_IMPROVED_COMPATIBILITY =
        "toggle_improved_compatibility";
    private static final String TOGGLE_LOCK_SCREEN_ROTATION_PREFERENCE =
            "toggle_lock_screen_rotation_preference";
    private static final String TOGGLE_SPEAK_PASSWORD_PREFERENCE =
            "toggle_speak_password_preference";
    private static final String SELECT_LONG_PRESS_TIMEOUT_PREFERENCE =
            "select_long_press_timeout_preference";
    private static final String ENABLE_ACCESSIBILITY_GESTURE_PREFERENCE_SCREEN =
            "enable_global_gesture_preference_screen";
    private static final String CAPTIONING_PREFERENCE_SCREEN =
            "captioning_preference_screen";
    private static final String DISPLAY_MAGNIFICATION_PREFERENCE_SCREEN =
            "screen_magnification_preference_screen";
    private static final String TOGGLE_SYSTEM_ACTION_QUICK_BOOT = 
    		"toggle_system_actions_quick_boot";
    private static final String SYSTEM_FREQ_MODE_PREFERENCE = 
            "system_freq_mode_preference";
    private static final String TOGGLE_LOGO = 
            "toggle_logo";
    

    private static final String SYSTEM_FREQ_USER_LOCK_FILE =
            "/sys/devices/system/cpu/autoplug/usr_lock";
    private static final String SYSTEM_FREQ_MODE_FILE =
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";
    private static final String SYSTEM_FREQ_CPU_ONLINE_FILE =
            "/sys/devices/system/cpu/cpu0/online";

    private static final String SWITCH_LOGO_FILE =
            "/sys/miscinfo/infos/bf";
    
    private static final String BOOT_PIC1 =
            "/misc/boot_pic.bin";
    private static final String BOOT_PIC2 =
            "/misc/boot_p2.bin";
    		
    // Extras passed to sub-fragments.
    static final String EXTRA_PREFERENCE_KEY = "preference_key";
    static final String EXTRA_CHECKED = "checked";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_SUMMARY = "summary";
    static final String EXTRA_SETTINGS_TITLE = "settings_title";
    static final String EXTRA_COMPONENT_NAME = "component_name";
    static final String EXTRA_SETTINGS_COMPONENT_NAME = "settings_component_name";

    // Timeout before we update the services if packages are added/removed
    // since the AccessibilityManagerService has to do that processing first
    // to generate the AccessibilityServiceInfo we need for proper
    // presentation.
    private static final long DELAY_UPDATE_SERVICES_MILLIS = 1000;

    // Dialog IDs.
    private static final int DIALOG_ID_NO_ACCESSIBILITY_SERVICES = 1;

    // Auxiliary members.
    final static SimpleStringSplitter sStringColonSplitter =
            new SimpleStringSplitter(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR);

    static final Set<ComponentName> sInstalledServices = new HashSet<ComponentName>();

    private final Map<String, String> mLongPressTimeoutValuetoTitleMap =
            new HashMap<String, String>();

    private final Configuration mCurConfig = new Configuration();

    private final Handler mHandler = new Handler();

    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            loadInstalledServices();
            updateServicesPreferences();
        }
    };

    private final PackageMonitor mSettingsPackageMonitor = new PackageMonitor() {
        @Override
        public void onPackageAdded(String packageName, int uid) {
            sendUpdate();
        }

        @Override
        public void onPackageAppeared(String packageName, int reason) {
            sendUpdate();
        }

        @Override
        public void onPackageDisappeared(String packageName, int reason) {
            sendUpdate();
        }

        @Override
        public void onPackageRemoved(String packageName, int uid) {
            sendUpdate();
        }

        private void sendUpdate() {
            mHandler.postDelayed(mUpdateRunnable, DELAY_UPDATE_SERVICES_MILLIS);
        }
    };

    private final SettingsContentObserver mSettingsContentObserver =
            new SettingsContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    loadInstalledServices();
                    updateServicesPreferences();
                }
            };

    private final RotationPolicyListener mRotationPolicyListener = new RotationPolicyListener() {
        @Override
        public void onChange() {
            updateLockScreenRotationCheckbox();
        }
    };

    // Preference controls.
    private PreferenceCategory mServicesCategory;
    private PreferenceCategory mSystemsCategory;

    private CheckBoxPreference mToggleLargeTextPreference;
    private CheckBoxPreference mTogglePowerButtonEndsCallPreference;
    private CheckBoxPreference mToggleLockScreenRotationPreference;
    private CheckBoxPreference mToggleImprovedCompatibilityPreference;
    private CheckBoxPreference mToggleSpeakPasswordPreference;
    private CheckBoxPreference mToggleQuickBoot;
    private ListPreference mSelectLongPressTimeoutPreference;
    private Preference mNoServicesMessagePreference;
    private PreferenceScreen mCaptioningPreferenceScreen;
    private PreferenceScreen mDisplayMagnificationPreferenceScreen;
    private PreferenceScreen mGlobalGesturePreferenceScreen;
    private ListPreference mSystemFreqModePreference;
    private CheckBoxPreference mToggleLogo;
    private int mLongPressTimeoutDefault;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.accessibility_settings);
        initializeAllPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadInstalledServices();
        updateAllPreferences();

        offerInstallAccessibilitySerivceOnce();

        mSettingsPackageMonitor.register(getActivity(), getActivity().getMainLooper(), false);
        mSettingsContentObserver.register(getContentResolver());
        if (RotationPolicy.isRotationSupported(getActivity())) {
            RotationPolicy.registerRotationPolicyListener(getActivity(),
                    mRotationPolicyListener);
        }
        if( mSystemFreqModePreference != null){
            mSystemFreqModePreference.setValue(getSystemFreqMode());
        }
    }

    @Override
    public void onPause() {
        mSettingsPackageMonitor.unregister();
        mSettingsContentObserver.unregister(getContentResolver());
        if (RotationPolicy.isRotationSupported(getActivity())) {
            RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                    mRotationPolicyListener);
        }
        mHandler.removeMessages(0);            
        super.onPause();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mSelectLongPressTimeoutPreference) {
            String stringValue = (String) newValue;
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LONG_PRESS_TIMEOUT, Integer.parseInt(stringValue));
            mSelectLongPressTimeoutPreference.setSummary(
                    mLongPressTimeoutValuetoTitleMap.get(stringValue));
            return true;
        }
        if(preference == mSystemFreqModePreference) {
        	setSystemFreqMode((String)newValue);
            Intent intent = new Intent();
            intent.setAction("com.android.actions.RUN_MODE_STATUS_CHANGED");
            this.getActivity().sendBroadcast(intent);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (mToggleLargeTextPreference == preference) {
            handleToggleLargeTextPreferenceClick();
            return true;
        } else if (mTogglePowerButtonEndsCallPreference == preference) {
            handleTogglePowerButtonEndsCallPreferenceClick();
            return true;
        } else if (mToggleLockScreenRotationPreference == preference) {
            handleLockScreenRotationPreferenceClick();
            return true;
        } else if (mToggleImprovedCompatibilityPreference == preference) {
        	handleImprovedCompatibilityPreferenceClick();
            return true;
        } else if (mToggleSpeakPasswordPreference == preference) {
            handleToggleSpeakPasswordPreferenceClick();
            return true;
        } else if (mGlobalGesturePreferenceScreen == preference) {
            handleTogglEnableAccessibilityGesturePreferenceClick();
            return true;
        } else if (mDisplayMagnificationPreferenceScreen == preference) {
            handleDisplayMagnificationPreferenceScreenClick();
            return true;
        } else if(mToggleQuickBoot == preference) {
        	handleToggleQuickBootClick();
        } else if(mToggleLogo == preference) {
        	handleToggleLogo();
        } 
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void handleToggleLargeTextPreferenceClick() {
        try {
            mCurConfig.fontScale = mToggleLargeTextPreference.isChecked() ? LARGE_FONT_SCALE : 1;
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException re) {
            /* ignore */
        }
    }

    private void handleTogglePowerButtonEndsCallPreferenceClick() {
        Settings.Secure.putInt(getContentResolver(),
                Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
                (mTogglePowerButtonEndsCallPreference.isChecked()
                        ? Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP
                        : Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_SCREEN_OFF));
    }

    private void handleLockScreenRotationPreferenceClick() {
        RotationPolicy.setRotationLockForAccessibility(getActivity(),
                !mToggleLockScreenRotationPreference.isChecked());
    }

    private void handleImprovedCompatibilityPreferenceClick() {
    	SystemProperties.set("persist.sys.extra_features", mToggleImprovedCompatibilityPreference.isChecked() ? "1" : "0");
    }
    
    private void handleToggleSpeakPasswordPreferenceClick() {
        Settings.Secure.putInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD,
                mToggleSpeakPasswordPreference.isChecked() ? 1 : 0);
    }

    private void handleTogglEnableAccessibilityGesturePreferenceClick() {
        Bundle extras = mGlobalGesturePreferenceScreen.getExtras();
        extras.putString(EXTRA_TITLE, getString(
                R.string.accessibility_global_gesture_preference_title));
        extras.putString(EXTRA_SUMMARY, getString(
                R.string.accessibility_global_gesture_preference_description));
        extras.putBoolean(EXTRA_CHECKED, Settings.Global.getInt(getContentResolver(),
                Settings.Global.ENABLE_ACCESSIBILITY_GLOBAL_GESTURE_ENABLED, 0) == 1);
        super.onPreferenceTreeClick(mGlobalGesturePreferenceScreen,
                mGlobalGesturePreferenceScreen);
    }

    private void handleDisplayMagnificationPreferenceScreenClick() {
        Bundle extras = mDisplayMagnificationPreferenceScreen.getExtras();
        extras.putString(EXTRA_TITLE, getString(
                R.string.accessibility_screen_magnification_title));
        extras.putCharSequence(EXTRA_SUMMARY, getActivity().getResources().getText(
                R.string.accessibility_screen_magnification_summary));
        extras.putBoolean(EXTRA_CHECKED, Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED, 0) == 1);
        super.onPreferenceTreeClick(mDisplayMagnificationPreferenceScreen,
                mDisplayMagnificationPreferenceScreen);
    }

    private void handleToggleQuickBootClick() {
    	Settings.System.putInt(getContentResolver(),
                "actions_quick_boot",
                mToggleQuickBoot.isChecked() ? 1 : 0);
    }
    
    private void handleToggleLogo() {
        setSwitchLogoValue(mToggleLogo.isChecked() ? 1 : 0);
        mToggleLogo.setChecked((getSwitchLogoValue() == 1));
    }

    private void initializeAllPreferences() {
        mServicesCategory = (PreferenceCategory) findPreference(SERVICES_CATEGORY);
        mSystemsCategory = (PreferenceCategory) findPreference(SYSTEM_CATEGORY);

        // Large text.
        mToggleLargeTextPreference =
                (CheckBoxPreference) findPreference(TOGGLE_LARGE_TEXT_PREFERENCE);

        // Power button ends calls.
        mTogglePowerButtonEndsCallPreference =
                (CheckBoxPreference) findPreference(TOGGLE_POWER_BUTTON_ENDS_CALL_PREFERENCE);
        if (!KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_POWER)
                || !Utils.isVoiceCapable(getActivity())) {
            mSystemsCategory.removePreference(mTogglePowerButtonEndsCallPreference);
        }

        // Lock screen rotation.
        mToggleLockScreenRotationPreference =
                (CheckBoxPreference) findPreference(TOGGLE_LOCK_SCREEN_ROTATION_PREFERENCE);
        if (!RotationPolicy.isRotationSupported(getActivity())) {
            mSystemsCategory.removePreference(mToggleLockScreenRotationPreference);
        }
		
		// Improved compatibility
        mToggleImprovedCompatibilityPreference =
            (CheckBoxPreference) findPreference(TOGGLE_IMPROVED_COMPATIBILITY);
        
        String build_mode = SystemProperties.get("ro.build_mode");
        String extra_features = SystemProperties.get("persist.sys.extra_features","1");
        mToggleImprovedCompatibilityPreference.setChecked(extra_features.trim().equals("1"));
        if (build_mode ==null || !build_mode.trim().equals("CTS")) {
        	mSystemsCategory.removePreference(mToggleImprovedCompatibilityPreference);
		}

        // Speak passwords.
        mToggleSpeakPasswordPreference =
                (CheckBoxPreference) findPreference(TOGGLE_SPEAK_PASSWORD_PREFERENCE);

        // Long press timeout.
        mSelectLongPressTimeoutPreference =
                (ListPreference) findPreference(SELECT_LONG_PRESS_TIMEOUT_PREFERENCE);
        mSelectLongPressTimeoutPreference.setOnPreferenceChangeListener(this);
        if (mLongPressTimeoutValuetoTitleMap.size() == 0) {
            String[] timeoutValues = getResources().getStringArray(
                    R.array.long_press_timeout_selector_values);
            mLongPressTimeoutDefault = Integer.parseInt(timeoutValues[0]);
            String[] timeoutTitles = getResources().getStringArray(
                    R.array.long_press_timeout_selector_titles);
            final int timeoutValueCount = timeoutValues.length;
            for (int i = 0; i < timeoutValueCount; i++) {
                mLongPressTimeoutValuetoTitleMap.put(timeoutValues[i], timeoutTitles[i]);
            }
        }

        // Captioning.
        mCaptioningPreferenceScreen = (PreferenceScreen) findPreference(
                CAPTIONING_PREFERENCE_SCREEN);
        mToggleQuickBoot = (CheckBoxPreference)findPreference(TOGGLE_SYSTEM_ACTION_QUICK_BOOT);

        if(!isQucikbootSupported()) {
            mSystemsCategory.removePreference(mToggleQuickBoot);
        }

        mSystemFreqModePreference = (ListPreference)findPreference(SYSTEM_FREQ_MODE_PREFERENCE);

        filterPerformanceOptions(mSystemsCategory, mSystemFreqModePreference);

        mSystemFreqModePreference.setOnPreferenceChangeListener(this);
        mSystemFreqModePreference.setValue(getSystemFreqMode());

        // Display magnification.
        mDisplayMagnificationPreferenceScreen = (PreferenceScreen) findPreference(
                DISPLAY_MAGNIFICATION_PREFERENCE_SCREEN);

        // Global gesture.
        mGlobalGesturePreferenceScreen =
                (PreferenceScreen) findPreference(ENABLE_ACCESSIBILITY_GESTURE_PREFERENCE_SCREEN);
        final int longPressOnPowerBehavior = getActivity().getResources().getInteger(
                com.android.internal.R.integer.config_longPressOnPowerBehavior);
        final int LONG_PRESS_POWER_GLOBAL_ACTIONS = 1;
        if (!KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_POWER)
                || longPressOnPowerBehavior != LONG_PRESS_POWER_GLOBAL_ACTIONS) {
            // Remove accessibility shortcut if power key is not present
            // nor long press power does not show global actions menu.
            mSystemsCategory.removePreference(mGlobalGesturePreferenceScreen);
        }

        mToggleLogo = (CheckBoxPreference)findPreference(TOGGLE_LOGO);
        if(!switchLogoEnabled()){
            mSystemsCategory.removePreference(mToggleLogo);
        }

    }
	
    private boolean isQucikbootSupported(){
        boolean supported = false;
        String hardware = android.os.SystemProperties.get("ro.hardware", "");
        long memsize = getMemoryTotalSizeFromProp();
        //default to enable quickboot
	boolean enable = false;
	boolean has_swap = false; 
   //     boolean bquickboot = (android.os.SystemProperties.getInt("ro.settings.quickboot", 1) == 1);
	boolean bquickboot = (android.os.SystemProperties.getInt("ro.config.quickboot", 1) == 1);

	File swap = new File("/dev/block/actk");
	if(swap.exists()==true)
	{
		Log.d(LOG_TAG,"/dev/block/actk exist!! ");
		has_swap = true;
	}

        Log.v(LOG_TAG, "memsize="+memsize);
        Log.v(LOG_TAG, "hardware="+hardware);
        Log.v(LOG_TAG, "ro.settings.quickboot="+enable);

        //disable quickboot for gs702c/gs702c2
     /*   if(hardware.equals("gs702c")){
            enable = false;
        }
		*/
	if(has_swap&&bquickboot)
		enable = true;

        if(enable){
            if(memsize >= 1024) {
                supported = true;
            }
        }
        return supported;

    }
    
    private long getMemoryTotalSizeFromProp() {
        return android.os.SystemProperties.getLong("system.ram.total", 512);
    }

    public static String getPerformanceMode(){
        PerformanceManager performanceService = new PerformanceManager();
        String mode = performanceService.getPerformanceMode();
        if(mode == null) {
            //API not support by Performance Server
            try {
                mode = FileUtils.readTextFile(new File(SYSTEM_FREQ_MODE_FILE), 0, null).trim();
            } catch (IOException e) {
                Log.e(LOG_TAG, "failed to read from " + SYSTEM_FREQ_MODE_FILE);
            }
        }	
        if(mode == null) {
            mode = "interactive";
        }
        if(mode.equals("benchmark")) {
            mode = "performance";
        }
        return mode;
    }
    public static void setPerformanceMode(String mode){
        if(mode ==null) {
            mode = "interactive";
        }
        PerformanceManager performanceService = new PerformanceManager();
        boolean ret = performanceService.setPerformanceMode(mode);
        if(ret == false) {
            //API not support by Performance Server
            try {
                FileUtils.stringToFile(SYSTEM_FREQ_USER_LOCK_FILE, "1");
                FileUtils.stringToFile(SYSTEM_FREQ_MODE_FILE, mode);
                for(int i = 1; i < 4; i++) {
                    String tmpStr = SYSTEM_FREQ_CPU_ONLINE_FILE.replaceAll("0", Integer.toString(i));
                    try {
                        String online = FileUtils.readTextFile(new File(tmpStr), 0, null).trim();
                        if(!online.equals("1")) {
                            FileUtils.stringToFile(tmpStr, "1");
                        }
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "failed to read from " + tmpStr);
                    }
                    tmpStr = SYSTEM_FREQ_MODE_FILE.replaceAll("0", Integer.toString(i));
                    FileUtils.stringToFile(tmpStr, mode);
                }
                FileUtils.stringToFile(SYSTEM_FREQ_USER_LOCK_FILE, "0");
            } catch (IOException e) {
                try {
                    FileUtils.stringToFile(SYSTEM_FREQ_USER_LOCK_FILE, "0");
                } catch (IOException ex) {
                }
                Log.e(LOG_TAG, "failed to write to " + SYSTEM_FREQ_MODE_FILE);
            }
        }
    }

    public static  String getSystemFreqMode() {
        return getPerformanceMode();
    }

    public static void setSystemFreqMode(String mode) {
        setPerformanceMode(mode);
    }

    public static int getSwitchLogoValue(){
        String info = "0";
        int value = 0;

        try {
            info = FileUtils.readTextFile(new File(SWITCH_LOGO_FILE), 0, null).trim();
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to read " + SWITCH_LOGO_FILE);
        }

        if(info.charAt(0) == '1'){
            value = 1;
        }
        return value;
    }    

    public static void setSwitchLogoValue(int value){
        String info = "0";
        if(value == 1){
            info = "1";
        }

        try {
            FileUtils.stringToFile(SWITCH_LOGO_FILE, info);
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to write to" + SWITCH_LOGO_FILE);
        }
    }    

    public boolean switchLogoEnabled(){
        boolean supportduallogo = SystemProperties.getBoolean("ro.boot.supportduallogo", false);
        if(supportduallogo && new File(BOOT_PIC1).exists() && new File(BOOT_PIC2).exists()) {
            return true;
        }
        return false;
    }
    
    private void updateAllPreferences() {
        updateServicesPreferences();
        updateSystemPreferences();
    }

    private void updateServicesPreferences() {
        // Since services category is auto generated we have to do a pass
        // to generate it since services can come and go and then based on
        // the global accessibility state to decided whether it is enabled.

        // Generate.
        mServicesCategory.removeAll();

        AccessibilityManager accessibilityManager = AccessibilityManager.getInstance(getActivity());

        List<AccessibilityServiceInfo> installedServices =
                accessibilityManager.getInstalledAccessibilityServiceList();
        Set<ComponentName> enabledServices = AccessibilityUtils.getEnabledServicesFromSettings(
                getActivity());

        final boolean accessibilityEnabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;

        for (int i = 0, count = installedServices.size(); i < count; ++i) {
            AccessibilityServiceInfo info = installedServices.get(i);

            PreferenceScreen preference = getPreferenceManager().createPreferenceScreen(
                    getActivity());
            String title = info.getResolveInfo().loadLabel(getPackageManager()).toString();

            ServiceInfo serviceInfo = info.getResolveInfo().serviceInfo;
            ComponentName componentName = new ComponentName(serviceInfo.packageName,
                    serviceInfo.name);

            preference.setKey(componentName.flattenToString());

            preference.setTitle(title);
            final boolean serviceEnabled = accessibilityEnabled
                    && enabledServices.contains(componentName);
            if (serviceEnabled) {
                preference.setSummary(getString(R.string.accessibility_feature_state_on));
            } else {
                preference.setSummary(getString(R.string.accessibility_feature_state_off));
            }

            preference.setOrder(i);
            preference.setFragment(ToggleAccessibilityServicePreferenceFragment.class.getName());
            preference.setPersistent(true);

            Bundle extras = preference.getExtras();
            extras.putString(EXTRA_PREFERENCE_KEY, preference.getKey());
            extras.putBoolean(EXTRA_CHECKED, serviceEnabled);
            extras.putString(EXTRA_TITLE, title);

            String description = info.loadDescription(getPackageManager());
            if (TextUtils.isEmpty(description)) {
                description = getString(R.string.accessibility_service_default_description);
            }
            extras.putString(EXTRA_SUMMARY, description);

            String settingsClassName = info.getSettingsActivityName();
            if (!TextUtils.isEmpty(settingsClassName)) {
                extras.putString(EXTRA_SETTINGS_TITLE,
                        getString(R.string.accessibility_menu_item_settings));
                extras.putString(EXTRA_SETTINGS_COMPONENT_NAME,
                        new ComponentName(info.getResolveInfo().serviceInfo.packageName,
                                settingsClassName).flattenToString());
            }

            extras.putParcelable(EXTRA_COMPONENT_NAME, componentName);

            mServicesCategory.addPreference(preference);
        }

        if (mServicesCategory.getPreferenceCount() == 0) {
            if (mNoServicesMessagePreference == null) {
                mNoServicesMessagePreference = new Preference(getActivity()) {
                        @Override
                    protected void onBindView(View view) {
                        super.onBindView(view);
                        TextView summaryView = (TextView) view.findViewById(R.id.summary);
                        String title = getString(R.string.accessibility_no_services_installed);
                        summaryView.setText(title);
                    }
                };
                mNoServicesMessagePreference.setPersistent(false);
                mNoServicesMessagePreference.setLayoutResource(
                        R.layout.text_description_preference);
                mNoServicesMessagePreference.setSelectable(false);
            }
            mServicesCategory.addPreference(mNoServicesMessagePreference);
        }
    }

    private void updateSystemPreferences() {
        // Large text.
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException re) {
            /* ignore */
        }
        mToggleLargeTextPreference.setChecked(mCurConfig.fontScale == LARGE_FONT_SCALE);

        // Power button ends calls.
        if (KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_POWER)
                && Utils.isVoiceCapable(getActivity())) {
            final int incallPowerBehavior = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT);
            final boolean powerButtonEndsCall =
                    (incallPowerBehavior == Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP);
            mTogglePowerButtonEndsCallPreference.setChecked(powerButtonEndsCall);
        }

        // Auto-rotate screen
        updateLockScreenRotationCheckbox();

        // Speak passwords.
        final boolean speakPasswordEnabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD, 0) != 0;
        mToggleSpeakPasswordPreference.setChecked(speakPasswordEnabled);

        // Long press timeout.
        final int longPressTimeout = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.LONG_PRESS_TIMEOUT, mLongPressTimeoutDefault);
        String value = String.valueOf(longPressTimeout);
        mSelectLongPressTimeoutPreference.setValue(value);
        mSelectLongPressTimeoutPreference.setSummary(mLongPressTimeoutValuetoTitleMap.get(value));

        // Captioning.
        final boolean captioningEnabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0) == 1;
        if (captioningEnabled) {
            mCaptioningPreferenceScreen.setSummary(R.string.accessibility_feature_state_on);
        } else {
            mCaptioningPreferenceScreen.setSummary(R.string.accessibility_feature_state_off);
        }

        // Screen magnification.
        final boolean magnificationEnabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED, 0) == 1;
        if (magnificationEnabled) {
            mDisplayMagnificationPreferenceScreen.setSummary(
                    R.string.accessibility_feature_state_on);
        } else {
            mDisplayMagnificationPreferenceScreen.setSummary(
                    R.string.accessibility_feature_state_off);
        }

        // Global gesture
        final boolean globalGestureEnabled = Settings.Global.getInt(getContentResolver(),
                Settings.Global.ENABLE_ACCESSIBILITY_GLOBAL_GESTURE_ENABLED, 0) == 1;
        if (globalGestureEnabled) {
            mGlobalGesturePreferenceScreen.setSummary(
                    R.string.accessibility_global_gesture_preference_summary_on);
        } else {
            mGlobalGesturePreferenceScreen.setSummary(
                    R.string.accessibility_global_gesture_preference_summary_off);
        }
        final boolean quickBootEnabled = Settings.System.getInt(getContentResolver(), 
        		"actions_quick_boot", 0) != 0;
        mToggleQuickBoot.setChecked(quickBootEnabled);

        final boolean toggleLogo = (getSwitchLogoValue() == 1);
        mToggleLogo.setChecked(toggleLogo);
    }

    private void updateLockScreenRotationCheckbox() {
        Context context = getActivity();
        if (context != null) {
            mToggleLockScreenRotationPreference.setChecked(
                    !RotationPolicy.isRotationLocked(context));
        }
    }

    private void offerInstallAccessibilitySerivceOnce() {
        // There is always one preference - if no services it is just a message.
        if (mServicesCategory.getPreference(0) != mNoServicesMessagePreference) {
            return;
        }
        SharedPreferences preferences = getActivity().getPreferences(Context.MODE_PRIVATE);
        final boolean offerInstallService = !preferences.getBoolean(
                KEY_INSTALL_ACCESSIBILITY_SERVICE_OFFERED_ONCE, false);
        if (offerInstallService) {
            String screenreaderMarketLink = SystemProperties.get(
                    SYSTEM_PROPERTY_MARKET_URL,
                    DEFAULT_SCREENREADER_MARKET_LINK);
            Uri marketUri = Uri.parse(screenreaderMarketLink);
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);

            if (getPackageManager().resolveActivity(marketIntent, 0) == null) {
                // Don't show the dialog if no market app is found/installed.
                return;
            }

            preferences.edit().putBoolean(KEY_INSTALL_ACCESSIBILITY_SERVICE_OFFERED_ONCE,
                    true).commit();
            // Notify user that they do not have any accessibility
            // services installed and direct them to Market to get TalkBack.
            showDialog(DIALOG_ID_NO_ACCESSIBILITY_SERVICES);
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case DIALOG_ID_NO_ACCESSIBILITY_SERVICES:
                return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.accessibility_service_no_apps_title)
                        .setMessage(R.string.accessibility_service_no_apps_message)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // dismiss the dialog before launching
                                        // the activity otherwise the dialog
                                        // removal occurs after
                                        // onSaveInstanceState which triggers an
                                        // exception
                                        removeDialog(DIALOG_ID_NO_ACCESSIBILITY_SERVICES);
                                        String screenreaderMarketLink = SystemProperties.get(
                                                SYSTEM_PROPERTY_MARKET_URL,
                                                DEFAULT_SCREENREADER_MARKET_LINK);
                                        Uri marketUri = Uri.parse(screenreaderMarketLink);
                                        Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                                                marketUri);
                                        try {
                                            startActivity(marketIntent);
                                        } catch (ActivityNotFoundException anfe) {
                                            Log.w(LOG_TAG, "Couldn't start play store activity",
                                                    anfe);
                                        }
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
            default:
                return null;
        }
    }

    private void loadInstalledServices() {
        Set<ComponentName> installedServices = sInstalledServices;
        installedServices.clear();

        List<AccessibilityServiceInfo> installedServiceInfos =
                AccessibilityManager.getInstance(getActivity())
                        .getInstalledAccessibilityServiceList();
        if (installedServiceInfos == null) {
            return;
        }

        final int installedServiceInfoCount = installedServiceInfos.size();
        for (int i = 0; i < installedServiceInfoCount; i++) {
            ResolveInfo resolveInfo = installedServiceInfos.get(i).getResolveInfo();
            ComponentName installedService = new ComponentName(
                    resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name);
            installedServices.add(installedService);
        }
    }

    void filterPerformanceOptions(PreferenceCategory category, Preference performance) {
        ListPreference pref = (ListPreference) performance;
        ArrayList<String> validValues = new ArrayList<String>();
        ArrayList<String> validSummaries = new ArrayList<String>();
        String[] values = getResources().getStringArray(R.array.system_freq_mode_preference_values);
        String[] summaries = getResources().getStringArray(R.array.system_freq_mode_preference_entries);
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            String summary = summaries[i];
            if (!value.equals("performance") || !SystemProperties.get("ro.settings.runmode.performance", "0").equals("0")) {
                validValues.add(value);
                validSummaries.add(summary);
            }
        }
        int count = validValues.size();
        if (count <= 1) {
            // no choices, so remove preference
            category.removePreference(performance);
        } else {
            pref.setEntryValues(validValues.toArray(new String[count]));
            pref.setEntries(validSummaries.toArray(new String[count]));
        }
    }

}
