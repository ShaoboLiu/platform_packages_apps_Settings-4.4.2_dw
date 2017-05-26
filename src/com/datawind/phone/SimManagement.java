/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.datawind.phone;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.format.DateFormat;
import android.widget.DatePicker;
import android.widget.TimePicker;

import android.telephony.TelephonyManager;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class SimManagement extends SettingsPreferenceFragment
        implements OnSharedPreferenceChangeListener,
                TimePickerDialog.OnTimeSetListener, DatePickerDialog.OnDateSetListener {

    private Preference              mImeiPref;
    private Preference              mPhoneNumberPref;
    private CheckBoxPreference      mEnablePhonePref;
    private CheckBoxPreference      mEnableMobileDataPref;

    private static final int DIALOG_DATEPICKER = 0;
    private static final int DIALOG_TIMEPICKER = 1;

    private Phone                   mPhone;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.dw_sim_management_prefs);

        // mPhone = PhoneGlobals.getPhone();

        initUI();
    }

    private void initUI() {
        Intent intent = getActivity().getIntent();
        boolean isFirstRun = intent.getBooleanExtra("firstRun", false);
        boolean dataRoaming = getSettingsDataRoaming();

        mImeiPref             = findPreference("dwkey_imei");
        mPhoneNumberPref      = findPreference("dwkey_phone_number");
        mEnablePhonePref      = (CheckBoxPreference)findPreference("dwkey_enable_phone");
        mEnableMobileDataPref = (CheckBoxPreference)findPreference("dwkey_enable_mobile_data");

        if (Utils.isWifiOnly(getActivity()) || isFirstRun || getMobileDataState(getActivity()) == TelephonyManager.DATA_DISCONNECTED) {
        }

        if (isFirstRun) {
        }

        // mImeiPref.setEnabled(dataRoaming);
        // mPhoneNumberPref.setEnabled(dataRoaming);
        mEnablePhonePref.setChecked(true);
        
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        mEnableMobileDataPref.setChecked(cm.getMobileDataEnabled());
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

        getActivity().registerReceiver(mIntentReceiver, filter, null, null);
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        updateDisplayUI(getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mIntentReceiver);
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    public void updateDisplayUI(Context context) {
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        mImeiPref.setSummary(tm.getDeviceId());

        String phoneNumber = tm.getLine1Number();
        if (phoneNumber != null) {
            mPhoneNumberPref.setSummary(phoneNumber);
        } else {
            mPhoneNumberPref.setSummary(android.os.Build.MODEL + " " + android.os.Build.DEVICE + " " + android.os.Build.PRODUCT + " " + android.os.Build.BRAND + " " + android.os.Build.MANUFACTURER);
        }

        //mEnablePhonePref.setChecked(true);
        
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        mEnableMobileDataPref.setChecked(cm.getMobileDataEnabled());
    }


    private boolean isNetworkConnected(Context context) {
        NetworkInfo netInfo = getActiveNetworkInfo(context);
        return netInfo != null && netInfo.isConnected();
    }

    private NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);
        return (cm != null)? cm.getActiveNetworkInfo() : null;
    }


    @Override   // This is a method of DatePickerDialog
    public void onDateSet(DatePicker view, int year, int month, int day) {
        final Activity activity = getActivity();
        if (activity != null) {
            // setDate(activity, year, month, day);
            updateDisplayUI(activity);
        }
    }

    @Override   // This is a method of TimePickerDialog
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        final Activity activity = getActivity();
        if (activity != null) {
            // setTime(activity, hourOfDay, minute);
            updateDisplayUI(activity);
        }

        // We don't need to call timeUpdated() here because the TIME_CHANGED
        // broadcast is sent by the AlarmManager as a side effect of setting the SystemClock time.
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key.equals("dwkey_imei")) {
        }

        else if (key.equals("dwkey_phone_number")) {
        }

        else if (key.equals("dwkey_enable_phone")) {
            /*
            boolean autoEnabled = preferences.getBoolean(key, true);
            Settings.Global.putInt(getContentResolver(), Settings.Global.AUTO_TIME, autoEnabled ? 1 : 0);

            mImeiPref.setEnabled(!autoEnabled);
            mPhoneNumberPref.setEnabled(!autoEnabled);
            */
        } 
        
        else if (key.equals("dwkey_enable_mobile_data")) {
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        final Calendar calendar = Calendar.getInstance();
        switch (id) {
        case DIALOG_DATEPICKER:
            DatePickerDialog d = new DatePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            return d;
        case DIALOG_TIMEPICKER:
            return new TimePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(getActivity()));
        default:
            throw new IllegalArgumentException();
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
    // This method was deprecated in API level 11.
    // This function is not relevant for a modern fragment-based PreferenceActivity.
        if (preference == mImeiPref) {
            // showDialog(DIALOG_DATEPICKER);
        } 
        
        else if (preference == mPhoneNumberPref) {
            // removeDialog(DIALOG_TIMEPICKER);
            // showDialog(DIALOG_TIMEPICKER);
        }
        
        else if (preference == mEnablePhonePref) {
            if (mEnablePhonePref.isChecked()) {
            }
        }
        
        else if (preference == mEnableMobileDataPref) {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.setMobileDataEnabled(mEnableMobileDataPref.isChecked());
        }
        
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        updateDisplayUI(getActivity());
    }

    private void timeUpdated() {
        Intent timeChanged = new Intent(Intent.ACTION_TIME_CHANGED);
        getActivity().sendBroadcast(timeChanged);
    }

    private String getDateFormat() {
        return Settings.System.getString(getContentResolver(),Settings.System.DATE_FORMAT);
    }

    private boolean getSettingsDataRoaming() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.DATA_ROAMING) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    private int getMobileDataState(Context context) {
    	TelephonyManager phMgr = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
    	return phMgr.getDataState();
    }
    
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Activity activity = getActivity();
            if (activity != null) {
                updateDisplayUI(activity);
            }
        }
    };
}
