

package com.android.settings.ethernet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.ethernet.EthernetManager;     
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.WirelessSettings;

import java.util.concurrent.atomic.AtomicBoolean;

public class EthernetEnabler implements CompoundButton.OnCheckedChangeListener  {
    public static final String ENABLED = "ethernet.ui.enabled";
    public static final String ISUSED = "isused";
    public static final String ISCHECKED = "ischecked";
    private static final String TAG = "EthernetEnabler";
    private final Context mContext;
    private Switch mSwitch;
    private boolean isLoadDriver = false;
    private AtomicBoolean mConnected = new AtomicBoolean(false);

    Intent intent = new Intent(ENABLED);

    private final EthernetManager mEthernetManager;
    private boolean mStateMachineEvent;
    private final IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (EthernetManager.ETHERNET_STATE_CHANGED_ACTION.equals(action)) {
                 updateEthernetState(intent.getIntExtra(EthernetManager.EXTRA_ETHERNET_STATE,
                    EthernetManager.ETHERNET_STATE_UNKNOWN));
            } 
        }
    };

    public EthernetEnabler(Context context, Switch switch_) {
        mContext = context;
        mSwitch = switch_;

        mEthernetManager = (EthernetManager) context.getSystemService(Context.ETHERNET_SERVICE);
        mIntentFilter = new IntentFilter(EthernetManager.ETHERNET_STATE_CHANGED_ACTION);
        Log.d(TAG,"EthernetEnabler construct");
    }

    public void resume() {
        // Wi-Fi state is sticky, so just let the receiver update UI
        mContext.registerReceiver(mReceiver, mIntentFilter);
        mSwitch.setOnCheckedChangeListener(this);
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
        mSwitch.setOnCheckedChangeListener(null);
    }

    public void setSwitch(Switch switch_) {
     if (mSwitch == switch_) return;
	 Log.d(TAG,"setSwitch called! ");
     mSwitch.setOnCheckedChangeListener(null);
     mSwitch = switch_;
     mSwitch.setOnCheckedChangeListener(this);
     final int ethernetState = mEthernetManager.getState();
     isLoadDriver = mEthernetManager.isEnabled();
     mSwitch.setChecked(isLoadDriver);
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        //Do nothing if called as a result of a state machine event
        Log.d(TAG,"onCheckedChanged");
        if (mStateMachineEvent) {
            return;
        }

       // if(isChecked)Log.d(TAG,"isChecked true");
        //else Log.d(TAG,"isChecked false");


        isLoadDriver = mEthernetManager.isEnabled();
        if(isChecked != isLoadDriver){
            Log.d(TAG,"isChecked != isLoadDriver");
            mEthernetManager.loadDriver(isChecked);
            mSwitch.setEnabled(false);
        }

        if(!isChecked){
            intent.putExtra(ISUSED,ISUSED);
            intent.putExtra(ISCHECKED,isChecked);
            mContext.sendBroadcast(intent);
        }
    }

    private void updateEthernetState(int state) {
        switch(state){
         case EthernetManager.ETHERNET_DRIVER_STATE_ENABLED:
            setSwitchChecked(true);
            mSwitch.setEnabled(true);
            break;
         case EthernetManager.ETHERNET_DRIVER_LOAD_FAILURE:
         case EthernetManager.ETHERNET_DRIVER_STATE_DISABLED:
            setSwitchChecked(false);
            Log.d(TAG,"updateEthernetState setSwitchChecked false");
            mSwitch.setEnabled(true);
            break;
         default:
            Log.d(TAG,"updateEthernetState default state");
        }
    }

    

    private void setSwitchChecked(boolean checked) {
     if(checked != mSwitch.isChecked()){
      mStateMachineEvent = true;
      mSwitch.setChecked(checked);
      intent.putExtra("isChecked",checked);
      mContext.sendBroadcast(intent);
      mStateMachineEvent = false;
     }
    }

    private void handleStateChanged(@SuppressWarnings("unused") NetworkInfo.DetailedState state) {
        // After the refactoring from a CheckBoxPreference to a Switch, this method is useless since
        // there is nowhere to display a summary.
        // This code is kept in case a future change re-introduces an associated text.
        /*
        // WifiInfo is valid if and only if Wi-Fi is enabled.
        // Here we use the state of the switch as an optimization.
        if (state != null && mSwitch.isChecked()) {
            WifiInfo info = mWifiManager.getConnectionInfo();
            if (info != null) {
                //setSummary(Summary.get(mContext, info.getSSID(), state));
            }
        }
        */
    }
}
