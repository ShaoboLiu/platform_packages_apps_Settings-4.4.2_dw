package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.Handler;
import android.util.Log;
import android.location.LocationManager;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.view.WindowManagerPolicy;
import com.android.settings.BrightnessPreference;
import com.android.settings.DisplaySettings;
import com.android.settings.accessibility.AccessibilitySettings;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Debug;
import android.provider.Settings;
import com.actions.hardware.PerformanceManager;

import com.android.settings.tvout.TvoutUtils;
import com.android.settings.tvout.TvoutScreenResizeActivity;

/**
 * because WindowManagerPolicy.ACTION_HDMI_PLUGGED have
 * Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT flag reference to
 * PhoneWindowManager.java, we can't receive it through define Receiver in
 * AndroidManifest.xml. so we first receive BOOT_COMPLETED broadcast, then
 * register WindowManagerPolicy.ACTION_HDMI_PLUGGED receiver in code
 */
public class BootCompletedBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "BootCompletedBroadcastReceiver";
    static boolean DEBUG = false;

    private static final int HDMI_UNPLUGGED = 0;
    private static final int HDMI_PLUGGED = 1;
    private static final int MASS_STORAGE_ENABLE = 2;

    private StorageManager mStorageManager;

    public static final String mIntelligentSettingsName = "Intelligent_brightness_mode";

    BroadcastReceiver mHdmiPlugReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (WindowManagerPolicy.ACTION_HDMI_PLUGGED.equals(intent.getAction())) {
                boolean pluggedIn = false;
                pluggedIn = intent.getBooleanExtra(WindowManagerPolicy.EXTRA_HDMI_PLUGGED_STATE, false);
                int what = pluggedIn ? HDMI_PLUGGED : HDMI_UNPLUGGED;
                mhandler.sendEmptyMessageDelayed(what, 200);
                Log.d(TAG, "here receive HDMI_PLUGGED broadcast,plug flag=" + what);
            }
        }
    };

    private void dealSomeThingInNormalBoot(Context context) {
        /** auto switch to tv when boot complete, this function mainly used for QC, don't modify */
        if(false) {
            TvoutUtils.getInstanceByName(TvoutUtils.TVOUT_CVBS).switchToSelectModeByModeName("");
            mhandler.sendEmptyMessageDelayed(HDMI_PLUGGED, 50000);
            return;
        }
        /** auto switch to usb massstorage, this function mainly used for nand auto test system, don't modify */
        String usb_massstorage = SystemProperties.get("ro.settings.config.usb_u_pan", "off");
        if(usb_massstorage!=null && usb_massstorage.trim().equals("on")) {
            mStorageManager = (StorageManager)context.getApplicationContext().getSystemService(Context.STORAGE_SERVICE);
            mhandler.sendEmptyMessageDelayed(MASS_STORAGE_ENABLE, 5000);
            return;
        }

        if(false) {
            setUseLocationForServicesIfNeeded(context);
        }

        DisplaySettings.getEnhancedColorSystem(context.getContentResolver());

        initIntelligentBacklight(context);

        /** if not support hdmi hardware, immediately return */
        if (context.getPackageManager().hasSystemFeature(PackageManager.ACTIONS_FEATURE_TVOUT_HDMI)) {

            IntentFilter filter = new IntentFilter();
            filter.addAction(WindowManagerPolicy.ACTION_HDMI_PLUGGED);
            context.getApplicationContext().registerReceiver(mHdmiPlugReceiver, filter);

            /**
             * off:don't auto detect hdmi, initially off; on:always switch to hdmi;
             * autodetect:auto detect hdmi plug
             */
            String hdmiMode = SystemProperties.get("ro.settings.config.hdmi", "on");
            Log.d(TAG, "dealSomeThingInNormalBoot hdmiMode="+hdmiMode);
            if (hdmiMode.equals("on")) {
                SharedPreferences hdmi_scale = context.getSharedPreferences(TvoutScreenResizeActivity.HDMI_SCALES, Context.MODE_PRIVATE);  
                boolean save_flag = hdmi_scale.getBoolean("save_flag", false);
                int default_scale_x = 50;
                int default_scale_y = 50;
                Log.d(TAG, "dealSomeThingInNormalBoot save_flag="+save_flag);
                if (save_flag) {
                    int hdmi_scale_x = hdmi_scale.getInt("hdmi_scale_x", default_scale_x);
                    int hdmi_scale_y = hdmi_scale.getInt("hdmi_scale_y", default_scale_x);
                    Log.d(TAG, "dealSomeThingInNormalBoot save_flag="+save_flag+";hdmi_scale_x="+hdmi_scale_x+";hdmi_scale_y="+hdmi_scale_y);
                    TvoutUtils.getInstanceByName(TvoutUtils.TVOUT_HDMI).setTvDisplayScale(hdmi_scale_x, hdmi_scale_y);
                }else{
                    TvoutUtils.getInstanceByName(TvoutUtils.TVOUT_HDMI).switchToSelectModeByModeName("");
                }
            } else {
            }   
        }
    }

    private boolean getIntelligentBrightnessMode(Context context) {
        int intelligentBrightnessMode = 0;
        try {
            if(context.getResources().getBoolean(
                        com.android.internal.R.bool.config_intelligent_brightness_available)){
                intelligentBrightnessMode = Settings.System.getInt(context.getContentResolver(), 
                        mIntelligentSettingsName, 0);
                        }
        } catch (Exception e) {
            Log.d(TAG, "getIntelligentBrightnessMode: " + e);
        }
        return intelligentBrightnessMode == 1;
    }

    private void initIntelligentBacklight(Context context){
        if (getIntelligentBrightnessMode(context)) {
            new PerformanceManager().enbleAutoAdjustBacklight();
        }else{
            new PerformanceManager().disableAutoAdjustBacklight();
        }
    }

    private void dealSomeThingInQuickBoot(Context context) {
        AccessibilitySettings.setSystemFreqMode("interactive");
        SystemProperties.set(DisplaySettings.TVOUT_STATUS_PROPERTY, "1");
        TvoutUtils.HdmiUtils hdmiUtils = (TvoutUtils.HdmiUtils)TvoutUtils.getInstanceByName(TvoutUtils.TVOUT_HDMI); 
        hdmiUtils.setHdmiEnable(true);
        IntentFilter filter = new IntentFilter();
        filter.addAction(WindowManagerPolicy.ACTION_HDMI_PLUGGED);
        context.getApplicationContext().registerReceiver(mHdmiPlugReceiver, filter);
    }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            dealSomeThingInNormalBoot(context);
        } else if(action.equals("android.intent.action.QUICKBOOT_COMPLETED")) {
            dealSomeThingInQuickBoot(context);
        }
    }

    private Handler mhandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HDMI_UNPLUGGED:
                    SystemProperties.set(TvoutUtils.TVOUT_HDMI_SELECT_VID, "-1");
                case HDMI_PLUGGED:
                    DisplaySettings.tryToUpdateTvoutMenuStatus(true);
                    break;
                case MASS_STORAGE_ENABLE:
                    mStorageManager.enableUsbMassStorage();
                    break;
            }
        }
    };

    private void setUseLocationForServicesIfNeeded(Context context) {
        SharedPreferences sp = context.getSharedPreferences("need_set_location_for_service", Context.MODE_PRIVATE);
        boolean need1=sp.getBoolean("if_set_network_location_opt_in", true);
        if (DEBUG) Log.i(TAG, "if_set_network_location_opt_in:" + need1);
        boolean need = sp.getBoolean("if_need", true);
        if (DEBUG) Log.i(TAG, "need set use location for service:" + need);
        if(!need||!need1) return;
        Uri GOOGLE_SETTINGS_CONTENT_URI =
            Uri.parse("content://com.google.settings/partner");
        ContentResolver resolver = context.getContentResolver();
        Cursor c = null;
        String stringValue = null;
        String stringOpt =null;
        Cursor c1 =null;
        try {
            c = resolver.query(GOOGLE_SETTINGS_CONTENT_URI, new String[] { "value" }, "name=?",
                    new String[] { "use_location_for_services" }, null);

            if (c != null && c.moveToNext()) {
                stringValue = c.getString(0);
            }
            c1 = resolver.query(GOOGLE_SETTINGS_CONTENT_URI, new String[] { "value" }, "name=?",
                    new String[] { "network_location_opt_in"}, null);

            if (c1 != null && c1.moveToNext()) {
                stringOpt= c1.getString(0);
            }
        } catch (RuntimeException e) {
            if (DEBUG)Log.w(TAG, "Failed to get 'Use My Location' setting", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (stringValue == null||stringOpt==null) {
            if(DEBUG) Log.i(TAG,"error");
        }
        int value;
        int valueOpt;
        try {
            value = Integer.parseInt(stringValue);
            valueOpt =Integer.parseInt(stringOpt);
        } catch (NumberFormatException nfe) {
            value = 2;
            valueOpt =2;
        }
        if(DEBUG)  Log.i(TAG, "query value:" + value+" value="+valueOpt);
        if(value != 1||valueOpt !=1) {
            try {
                ContentValues values = new ContentValues();
                values.put("value", "0");
                ContentValues values1 = new ContentValues();
                values1.put("value", "1");
                int i =resolver.update(GOOGLE_SETTINGS_CONTENT_URI,values, "name=?",
                        new String[] {"use_location_for_services"});
                if (DEBUG)Log.v(TAG, "update="+ i);
                int i1 =resolver.update(GOOGLE_SETTINGS_CONTENT_URI,values1, "name=?",
                        new String[] {"network_location_opt_in"});
                if(i == 0 || i1==0) {
                    values.put("name", "use_location_for_services");
                    values1.put("name", "network_location_opt_in");
                    Uri sUri = resolver.insert(GOOGLE_SETTINGS_CONTENT_URI, values);
                    Uri mUri = resolver.insert(GOOGLE_SETTINGS_CONTENT_URI, values1);
                    if(sUri == null || sUri.getPath().contains("null")||mUri==null ||mUri.getPath().contains("null")) {
                        Log.v("caichsh", "insert, uri=null");
                        return;
                    }
                    if (DEBUG) Log.v(TAG, "insert, muri="+mUri.getPath());
                    if (DEBUG) Log.v(TAG, "insert, uri="+sUri.getPath());
                } 
                Settings.Secure.setLocationProviderEnabled(context.getContentResolver(), "network", true);
                if (DEBUG) Log.v(TAG, "setLocationProviderEnabled");
                Editor ed = context.getSharedPreferences("need_set_location_for_service", Context.MODE_PRIVATE).edit();
                ed.putBoolean("if_need", false);
                ed.putBoolean("if_set_network_location_opt_in", false);
                ed.commit();
            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
            }
        }
    }
}
