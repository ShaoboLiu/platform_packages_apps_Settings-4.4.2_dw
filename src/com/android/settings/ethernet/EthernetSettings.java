package com.android.settings.ethernet;

import java.util.Arrays;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ethernet.EthernetManager;
import android.net.ethernet.EthernetDevInfo;
import android.net.wifi.WifiManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.os.Bundle;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class EthernetSettings extends SettingsPreferenceFragment implements
        DialogInterface.OnClickListener{

  private static final String TAG = "EthernetSettings";
  private static final String KEY_ETH_CONF = "ethernet_conf";
  private static final String KEY_PPPOE_CONF = "pppoe_conf";

  private static final int ETHERNET_DIALOG_ID = 1;

  private final IntentFilter mFilter;
  private final BroadcastReceiver mReceiver;

  private EthernetManager mEthManager;
  private EthernetEnabler mEthEnabler;
  private Preference mEthConfigPref;
  private Preference mPppoeConfigPref;
  
  private EthernetConfigDialog mDialog;
  private PppoeConfigDialog  mPdialog;

  private boolean isLoadDriver = false;
  private String current_using_key = KEY_ETH_CONF;
  

  private TextView mEmptyView;


  public EthernetSettings(){
        mFilter = new IntentFilter();
        mFilter.addAction(EthernetManager.ETHERNET_STATE_CHANGED_ACTION);
        mFilter.addAction(EthernetManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(EthernetEnabler.ENABLED);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(context, intent);
            }
        };

  }

  public void onActivityCreated(Bundle savedInstanceState) {
    Log.d(TAG,"onCreate ");

    addPreferencesFromResource(R.xml.ethernet_settings);
    
    mEthConfigPref = (Preference) findPreference(KEY_ETH_CONF);
    mPppoeConfigPref = (Preference) findPreference(KEY_PPPOE_CONF);
    getPreferenceScreen().removePreference(mPppoeConfigPref);
    
    mEthManager = (EthernetManager)getSystemService(Context.ETHERNET_SERVICE);
    isLoadDriver = mEthManager.isEnabled();
    mEthConfigPref.setEnabled(isLoadDriver);
    mPppoeConfigPref.setEnabled(isLoadDriver);
    
    final Activity activity = getActivity();
    Switch actionBarSwitch = new Switch(activity);
    mEthEnabler = new EthernetEnabler(activity, actionBarSwitch);

    super.onActivityCreated(savedInstanceState);
  }

  public void onResume(){
    super.onResume();
    if(mEthEnabler != null){
        mEthEnabler.resume();
    }
    getActivity().registerReceiver(mReceiver, mFilter);
  }

  public void onPause(){
    super.onPause();
    if(mEthEnabler != null){
        mEthEnabler.pause();
    }
    getActivity().unregisterReceiver(mReceiver);
  }

  public void onSaveInstanceState(Bundle outState){
    super.onSaveInstanceState(outState);
  }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
       
            current_using_key = preference.getKey();
            Log.d(TAG,"current_using_key: " + current_using_key);

            if(mDialog != null || mPdialog != null){
                removeDialog(ETHERNET_DIALOG_ID);
                mDialog = null;
                mPdialog = null;
            }
            showDialog(ETHERNET_DIALOG_ID);

        return super.onPreferenceTreeClick(screen, preference);
   }


  public void onClick(DialogInterface dialogInterface, int button) {
    if(button == DialogInterface.BUTTON_POSITIVE){
        if(current_using_key.equals(KEY_ETH_CONF)){
            EthernetDevInfo info = mDialog.getConf();
            if(checkInfo(info)){
             mEthManager.connect(info);
	         Toast.makeText(getActivity(),R.string.note_connecting, Toast.LENGTH_SHORT).show();
            }else{
             Toast.makeText(getActivity(),R.string.note_eth_conf, Toast.LENGTH_SHORT).show();
            }
        }else if(current_using_key.equals(KEY_PPPOE_CONF)){
            if(mPdialog.saveConfig()){
             EthernetDevInfo info = new EthernetDevInfo();
             info.setIfName("eth0");
             info.setConnectMode(EthernetDevInfo.ETHERNET_CONN_MODE_PPPOE);
             mEthManager.connect(info);
             Toast.makeText(getActivity(),R.string.note_connecting, Toast.LENGTH_SHORT).show();
            }else{
             Toast.makeText(getActivity(),R.string.note_user_passwd, Toast.LENGTH_SHORT).show();
            }   
        }
    }else if(button == DialogInterface.BUTTON_NEGATIVE){
    }
  }


    @Override
    public Dialog onCreateDialog(int dialogId) {

        if(current_using_key.equals(KEY_ETH_CONF)){
         mDialog = new EthernetConfigDialog(getActivity(), this, true);
        }else if(current_using_key.equals(KEY_PPPOE_CONF)){
         mPdialog = new PppoeConfigDialog(getActivity(), this, true);
        }

        return mDialog == null ? mPdialog : mDialog;
    }

    private boolean checkInfo(EthernetDevInfo info){
     if(info.getConnectMode() == EthernetDevInfo.ETHERNET_CONN_MODE_MANUAL){
      if(info.getIpAddress() == null || info.getIpAddress().trim().equals("")
        || info.getNetMask() == null || info.getNetMask().trim().equals("")
        || info.getDnsAddr() == null || info.getDnsAddr().trim().equals("")
        || info.getRouteAddr() == null || info.getRouteAddr().trim().equals("")){
       return false;
      }
     }
     
     return true;
    }

    private void setPreState(boolean enabled){
    mEthConfigPref.setEnabled(enabled);
    mPppoeConfigPref.setEnabled(enabled);
    }

    private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        if (EthernetEnabler.ENABLED.equals(action)){
            String ret = intent.getStringExtra(EthernetEnabler.ISUSED);
            if(EthernetEnabler.ISUSED.equals(ret))
            setPreState(intent.getBooleanExtra(EthernetEnabler.ISCHECKED,false));
        }else if(EthernetManager.ETHERNET_STATE_CHANGED_ACTION.equals(action)){
            updateEthernetState(intent.getIntExtra(EthernetManager.EXTRA_ETHERNET_STATE,
                EthernetManager.ETHERNET_STATE_UNKNOWN));
        }else if(EthernetManager.NETWORK_STATE_CHANGED_ACTION.equals(action)){
            NetworkInfo mNetworkInfo = (NetworkInfo)intent.getParcelableExtra(EthernetManager.EXTRA_NETWORK_INFO);
            NetworkInfo.DetailedState state = mNetworkInfo.getDetailedState();
            if(state == DetailedState.CONNECTED){
             if(mDialog != null){
              mDialog.refresh();
             }
            }
        }

    }

    private void addMessagePreference(int messageId) {
        if (mEmptyView != null) mEmptyView.setText(messageId);
    }


  private void updateEthernetState(int state) {

        switch (state) {
            case EthernetManager.ETHERNET_DRIVER_STATE_ENABLED:
                isLoadDriver = true;
                setPreState(true);
                break;
            case EthernetManager.ETHERNET_DRIVER_LOAD_FAILURE:
            case EthernetManager.ETHERNET_DRIVER_STATE_DISABLED:
                isLoadDriver = false;
                setPreState(false);
                if(EthernetManager.ETHERNET_DRIVER_LOAD_FAILURE == state)
                    Toast.makeText(getActivity(),R.string.eth_driver_failed, Toast.LENGTH_SHORT).show();
                break;
            default:
        }

    }


}
