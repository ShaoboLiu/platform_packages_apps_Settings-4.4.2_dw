package com.android.settings;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class G3information extends Activity{

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		WebView localWebView = new WebView(this);
		localWebView.getSettings().setJavaScriptEnabled(true);
		localWebView.loadUrl("file:///system/etc/NOTICE.html");
		localWebView.getSettings().setSupportZoom(true);
		localWebView.getSettings().setBuiltInZoomControls(true);
		setContentView(localWebView);
	}
  
	
}
