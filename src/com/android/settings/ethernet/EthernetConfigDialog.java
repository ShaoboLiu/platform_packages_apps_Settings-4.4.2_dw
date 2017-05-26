/*
 * Copyright (C) 2010 The Android-x86 Open Source Project
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
 *
 * Author: Yi Sun <beyounn@gmail.com>
 */

package com.android.settings.ethernet;


import java.util.List;

import com.android.settings.R;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.ethernet.EthernetManager;
import android.net.ethernet.EthernetDevInfo;
import android.preference.Preference;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Slog;
import android.util.Log;

public class EthernetConfigDialog extends AlertDialog implements
        DialogInterface.OnClickListener, AdapterView.OnItemSelectedListener, View.OnClickListener {
    private final String TAG = "EthernetConfigDialog";

    private View mView;
    private RadioButton mConTypeDhcp;
    private RadioButton mConTypeManual;
    private EditText mIpaddr;
    private EditText mDns;
    private EditText mGw;
    private EditText mMask;
    
    
    private boolean mEdit;
    private final DialogInterface.OnClickListener mListener;

    private EthernetManager mEthManager;
    private EthernetDevInfo mEthInfo;
    private boolean mEnablePending;


    public EthernetConfigDialog(Context context, DialogInterface.OnClickListener listener,
             boolean edit) {
        super(context);
        mEdit = edit;
        mListener = listener;
	    mEthManager = (EthernetManager) context.getSystemService(Context.ETHERNET_SERVICE);
        buildDialogContent(context);
        setButton(DialogInterface.BUTTON_POSITIVE,context.getString(R.string.wifi_connect),listener);
        setButton(DialogInterface.BUTTON_NEGATIVE,context.getString(R.string.wifi_cancel),listener);
        //hide keyboard 
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        Log.d(TAG,"EthernetConfigDialog construct ending");
    }

    public int buildDialogContent(Context context) {
        this.setTitle(R.string.ethernet_conf_title);
        this.setView(mView = getLayoutInflater().inflate(R.layout.ethernet_configure, null));
        mConTypeDhcp = (RadioButton) mView.findViewById(R.id.dhcp_radio);
        mConTypeManual = (RadioButton) mView.findViewById(R.id.manual_radio);
        mIpaddr = (EditText)mView.findViewById(R.id.ipaddr_edit);
        mMask = (EditText)mView.findViewById(R.id.netmask_edit);
        mDns = (EditText)mView.findViewById(R.id.eth_dns_edit);
        mGw = (EditText)mView.findViewById(R.id.eth_gw_edit);

        mConTypeDhcp.setChecked(true);
        mConTypeManual.setChecked(false);
        setStaticIpInfoState(false);
        mConTypeManual.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
             setStaticIpInfoState(true);
            }
        });

        mConTypeDhcp.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
             setStaticIpInfoState(false);
            }
        });

        this.setInverseBackgroundForced(true);
            
      if (mEthManager.hasSavedConf()) {
        mEthInfo = mEthManager.getSavedConfig();

        if (mEthInfo.getConnectMode().equals(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP)) {
           setStaticIpInfoState(false);
           setContent();
        } else if(mEthInfo.getConnectMode().equals(EthernetDevInfo.ETHERNET_CONN_MODE_MANUAL)) {
            mConTypeDhcp.setChecked(false);
            mConTypeManual.setChecked(true);
            setStaticIpInfoState(true);
            setContent();
        }
      }
        return 0;
    }


  public void refresh(){
   Log.d(TAG,"refresh()");
   if (mEthManager.hasSavedConf()) {
    mEthInfo = mEthManager.getSavedConfig();
    if(mEthInfo.getConnectMode().equals(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP)) {
        setStaticIpInfoState(false);
        setContent();
    }
   }
    }

  public void onClick(DialogInterface dialogInterface, int button) {
  }

    public void onNothingSelected(android.widget.AdapterView av) {
     Log.d(TAG,"onNothingSelected");
    }
    public void onItemSelected(android.widget.AdapterView<?> av,android.view.View v,int i,long l){
     Log.d(TAG,"onItemSelected");
    }
    public void onClick(android.view.View v){
    }



    private void setStaticIpInfoState(boolean enable){
     mIpaddr.setEnabled(enable);
     mDns.setEnabled(enable);
     mGw.setEnabled(enable);
     mMask.setEnabled(enable);
    }

    private void setContent(){
     mIpaddr.setText(mEthInfo.getIpAddress());
     if(mEthInfo.getRouteAddr().startsWith("/"))
      mGw.setText(mEthInfo.getRouteAddr().substring(1));
     else mGw.setText(mEthInfo.getRouteAddr());
     mDns.setText(mEthInfo.getDnsAddr());
     if(mEthInfo.getNetMask().startsWith("/"))
       mMask.setText(mEthInfo.getNetMask().substring(1));
     else mMask.setText(mEthInfo.getNetMask());
    }

    public void enableAfterConfig() {
        mEnablePending = true;
    }

    public EthernetDevInfo getConf(){
        EthernetDevInfo info = new EthernetDevInfo();
        info.setIfName("eth0");
        if(mConTypeDhcp.isChecked()) {
         info.setConnectMode(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP);
         info.setIpAddress("");
         info.setRouteAddr("");
         info.setDnsAddr("");
         info.setNetMask("");
        }else{
         info.setConnectMode(EthernetDevInfo.ETHERNET_CONN_MODE_MANUAL);
         info.setIpAddress(mIpaddr.getText().toString());
         info.setRouteAddr(mGw.getText().toString());
         info.setDnsAddr(mDns.getText().toString());
         info.setNetMask(mMask.getText().toString());
        }
        return info;
    }
}
