package com.android.settings.tvout;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import android.util.Log;
import android.content.Context;
import com.android.settings.R;
import android.os.SystemProperties;
import android.provider.Settings;
import com.actions.hardware.DisplayManager;
import com.actions.hardware.DisplayManager.ScaleInfo;

public class TvoutUtils {
    static final String LOG_TAG = "TvoutUtils";
    static DisplayManager mDisplayManager;
    // Context mContext;
    List<String> mSupportedModesList;
    String mSelectMode;
    String mTvoutTypeName;
    private static TvoutUtils sTvoutInstance = new TvoutUtils();
    private static CvbsUtils sCvbsInstance;
    private static HdmiUtils sHdmiInstance;

    public static final String LCD_DISPLAYER = "lcd0";
    public static final String TVOUT_CVBS = "cvbs";
    public static final String TVOUT_HDMI = "hdmi";
    public static final String TVOUT_DISCONNECT = "Disconnect";
    public static final String TVOUT_CVBS_SELECT_MODE = "hw.tvout_cvbs_select_mode";
    public static final String TVOUT_HDMI_SELECT_VID = "hw.tvout_hdmi_select_vid";

    public TvoutUtils() {

    }

    public static TvoutUtils getInstanceByName(String tvoutTypeName) {
        return sTvoutInstance.safeGetInstanceByName(tvoutTypeName);
    }

    TvoutUtils safeGetInstanceByName(String tvoutTypeName) {
        if (mDisplayManager == null) {
            mDisplayManager = new DisplayManager();
        }

        if (tvoutTypeName.equals(TVOUT_CVBS)) {
            if (sCvbsInstance == null) {
                sCvbsInstance = new CvbsUtils();
            }
            return sCvbsInstance;
        } else if (tvoutTypeName.equals(TVOUT_HDMI)) {
            if (sHdmiInstance == null) {
                sHdmiInstance = new HdmiUtils();
            }
            return sHdmiInstance;
        }

        return this;
    }

    /*
     * public void setContext(Context context) { mContext = context; }
     */

    public void setChoosedTvOutMode(String modeName) {
        mSelectMode = modeName;
    }

    public String[] getSupportedModesList() {
        return null;
    }

    public boolean isCurrentConnecting() {
    	String curDisplayers = mDisplayManager.getOutputDisplayerStr();
    	if(curDisplayers != null) {
    	    if(curDisplayers.indexOf(mTvoutTypeName) >= 0) {
    	    	return true;
    	    }
    	}
        return false;
    }

    public boolean isCablePlugIn() {
            int state = mDisplayManager.getCableState();
            if ((state & 0x1) > 0) {
                return true;
            }

            return false;
    }
        
    private void safeSwitchToSelectModeByModeName(String mode) {
        /**
         * set output displayer,contain:"lcd0", "cvbs", "hdmi",if you want to
         * output multiple displayers, use "&&" connect them, but only support
         * "lcd0&&cvbs" and "lcd0&&hdmi" exception "lcd0&&cvbs&&hdmi"
         */
        String keyStr = LCD_DISPLAYER + "&&" + mTvoutTypeName;

        if (mTvoutTypeName.equals(TVOUT_CVBS)) {
            SystemProperties.set(TVOUT_HDMI_SELECT_VID, "-1");
        } else if (mTvoutTypeName.equals(TVOUT_HDMI)) {
            SystemProperties.set(TVOUT_CVBS_SELECT_MODE, "-1");
            if(!isCablePlugIn()) {
            	return;
            }
        }

        Log.d(LOG_TAG, "now switch to tv out,the mode=" + keyStr);
        /**
         * fix the displaymode to 0x7,means high bandwidth mode,reference to
         * libdisplay/displayengine.cpp
         */
        mDisplayManager.setDisplayMode(0x7);

        mDisplayManager.setOutputDisplayer(keyStr);
    }

    public void switchToSelectModeByModeName(String mode) {
        safeSwitchToSelectModeByModeName(mode);
    }

    public void switchToSelectModeByModeValue(int modeValue) {
        safeSwitchToSelectModeByModeName("");
    }

    public void closeTvoutDisplay() {
        Log.d(LOG_TAG, "closeTvoutDisplay");
        SystemProperties.set(TVOUT_CVBS_SELECT_MODE, "-1");
        SystemProperties.set(TVOUT_HDMI_SELECT_VID, "-1");
        mSelectMode = null;
        mDisplayManager.setOutputDisplayer(LCD_DISPLAYER);
    }

    public String getLastSelectModeValue() {
    	String curDisplayers = mDisplayManager.getOutputDisplayerStr();
    	if(curDisplayers.indexOf(TVOUT_CVBS) < 0) {
    		SystemProperties.set(TVOUT_CVBS_SELECT_MODE, "-1");
    	}
    	if(curDisplayers.indexOf(TVOUT_HDMI) < 0) {
    	    //SystemProperties.set(TVOUT_HDMI_SELECT_VID, "-1");
    	} else {
    		int hdmiVid = mDisplayManager.getHdmiVid();
    		SystemProperties.set(TVOUT_HDMI_SELECT_VID, String.valueOf(hdmiVid));
    	}
    	  	   
        if (mTvoutTypeName.equals(TVOUT_CVBS)) {
            return SystemProperties.get(TVOUT_CVBS_SELECT_MODE, "-1");
        } else if (mTvoutTypeName.equals(TVOUT_HDMI)) {
            return SystemProperties.get(TVOUT_HDMI_SELECT_VID, "-1");
        }
        return "-1";
    }

    public static int[] getTvDisplayScale() {
        int[] scales = new int[2];
        ScaleInfo info = new ScaleInfo();
        if (!mDisplayManager.getTvDisplayScale(info)) {
            return null;
        }
        scales[0] = info.scale_x;
        scales[1] = info.scale_y;
        return scales;
    }

    public static boolean setTvDisplayScale(int xscale, int yscale) {
        return mDisplayManager.setTvDisplayScale(xscale, yscale);
    }

    public class CvbsUtils extends TvoutUtils {
        public CvbsUtils() {
            mTvoutTypeName = TVOUT_CVBS;
        }

        public String[] getSupportedModesList() {
            return null;
        }

        private void setTheCvbsFormat(String format) {
            int mode = 1;
            if (format.equals("pal")) {
                mode = 0;
            } else {
                format = "ntsc";
            }
            Log.d(LOG_TAG, "mode=" + mode);
            SystemProperties.set(TVOUT_CVBS_SELECT_MODE, String.valueOf(mode));
            Log.d(LOG_TAG, "in setTheCvbsFormat=" + SystemProperties.get(TVOUT_CVBS_SELECT_MODE));
            mDisplayManager.setFormat(format);
        }

        public void switchToSelectModeByModeName(String mode) {
            String lMode = "";

            if (mode != null && !mode.isEmpty()) {
                lMode = mode;
            } else if (mSelectMode != null && !mSelectMode.isEmpty()) {
                lMode = mSelectMode;
            }
            // check and set cvbs format, if the format not supported, default
            // set it to NTSC
            if (lMode.indexOf("PAL") >= 0) {
                setTheCvbsFormat("pal");
            } else if (lMode.equals(TVOUT_DISCONNECT)) {
                closeTvoutDisplay();
                return;
            } else {
                setTheCvbsFormat("ntsc");
            }
            super.switchToSelectModeByModeName(lMode);
        }

        public void switchToSelectModeByModeValue(int modeValue) {
            Log.d(LOG_TAG, "the cvbs select mode value=" + modeValue);
            String mode = "";
            if (mTvoutTypeName.equals(TVOUT_CVBS)) {
                if (modeValue == 0) {
                    mode = "PAL";
                } else if (modeValue < 0) {
                    mode = TVOUT_DISCONNECT;
                } else {
                    mode = "NTSC";
                }
            }

            switchToSelectModeByModeName(mode);
        }
    }

    public class HdmiUtils extends TvoutUtils {
        private Map<String, String> mHdmiCapMap = new LinkedHashMap<String, String>();
        public static final int HDMI_DEFAULT_VID = 19;

        public HdmiUtils() {
            mTvoutTypeName = TVOUT_HDMI;
            mHdmiCapMap.clear();
            mHdmiCapMap.put(TVOUT_DISCONNECT, "-1");
        }

        private int getHdmiCapMap() {
        	/*
            if (mHdmiCapMap.size() > 1) {
                return 0;
            }*/
            int i, j;
            String hdmiCap = mDisplayManager.getHdmiCap();
            if (hdmiCap == null || hdmiCap.isEmpty()) {
                return -1;
            }
            Log.d(LOG_TAG, "hdmiCap=" + hdmiCap);
            mHdmiCapMap.clear();
            mHdmiCapMap.put(TVOUT_DISCONNECT, "-1");
            String[] hdmiCapArray = hdmiCap.split(";");
            for (i = 0; i < hdmiCapArray.length; i++) {
                String oneLine[] = hdmiCapArray[i].split(",");
                mHdmiCapMap.put(oneLine[0], oneLine[1]);
            }
            return 0;
        }

        public String[] getSupportedModesList() {
            int ret = getHdmiCapMap();
            if (ret >= 0) {
                return getHdmiSupportedDisplayNameList();
            } else {
                return null;
            }
        }

        public String[] getSupportedVidList() {
            return getHdmiSupportedVidList();
        }

        private String[] getHdmiSupportedDisplayNameList() {
            if (mHdmiCapMap.size() <= 1) {
                if (getHdmiCapMap() < 0) {
                    return null;
                }
            }

            int i = 0, len = mHdmiCapMap.size();
            String[] displayNames = new String[len];

            Set<String> lname = mHdmiCapMap.keySet();
            Iterator iter = lname.iterator();
            while (iter.hasNext()) {
                displayNames[i++] = (String) iter.next();
            }

            return displayNames;
        }

        private String[] getHdmiSupportedVidList() {
            if (mHdmiCapMap.size() <= 1) {
                if (getHdmiCapMap() < 0) {
                    return null;
                }
            }

            int i = 0, len = mHdmiCapMap.size();
            String[] vids = new String[len];

            Collection<String> lVid = mHdmiCapMap.values();
            Iterator iter = lVid.iterator();
            while (iter.hasNext()) {
                vids[i++] = (String) iter.next();
            }

            return vids;
        }

        private String getNameByVid(int vid) {
            String strVid = String.valueOf(vid);
            String[] names = getHdmiSupportedDisplayNameList();
            if (names == null) {
                return null;
            }

            for (int i = 0; i < names.length; i++) {
                if (strVid.equals(mHdmiCapMap.get(names[i]))) {
                    return names[i];
                }
            }

            return null;
        }

        private int getVidByName(String name) {
            if (mHdmiCapMap.size() > 1) {
                String strVid = mHdmiCapMap.get(name);
                return Integer.valueOf(strVid);
            }
            return -1;
        }

        // if HDMI_DEFAULT_VID can't find in vid list, use the first vid
        public int getHdmiDefaultVid() {
            String[] vids = getHdmiSupportedVidList();
            if(vids != null) {
            	return Integer.valueOf(vids[0]);
            }
            
            if (vids != null) {
                for (int i = 0; i < vids.length; i++) {
                    if (vids[i].equals(String.valueOf(HDMI_DEFAULT_VID))) {
                        return HDMI_DEFAULT_VID;
                    }
                }
                return Integer.valueOf(vids[0]);
            }
            return 19;
        }

        private void setTheHdmiVid(int vid) {
            SystemProperties.set(TVOUT_HDMI_SELECT_VID, String.valueOf(vid));
            mDisplayManager.setHdmiVid(vid);
        }
        
        public void setHdmiEnable(boolean flag) {
                mDisplayManager.setHdmiDisconnectFlag(!flag);
        }
        
        public void switchToSelectModeByModeName(String mode) {
            int i, vid = 0;
            String lMode = "";

            if (mode != null && !mode.isEmpty()) {
                lMode = mode;
            } else if (mSelectMode != null && !mSelectMode.isEmpty()) {
                lMode = mSelectMode;
            }

            String strVid = mHdmiCapMap.get(lMode);
            if (strVid != null) {
                vid = Integer.valueOf(strVid);
            }

            if (vid == 0) {
                vid = getHdmiDefaultVid();
            } else if (vid < 0) {
                closeTvoutDisplay();
                return;
            }

            Log.d(LOG_TAG, "set hdmi,the name=" + lMode + "the vid=" + vid);
            setTheHdmiVid(vid);
            super.switchToSelectModeByModeName(lMode);
        }

        public void switchToSelectModeByModeValue(int modeValue) {
            Log.d(LOG_TAG, "the hdmi select mode vid=" + modeValue);
            if (modeValue <= 0) {
                closeTvoutDisplay();
            } else {
                setTheHdmiVid(modeValue);
                super.switchToSelectModeByModeValue(modeValue);
            }
        }

        public void closeTvoutDisplay() {
            mHdmiCapMap.clear();
            mHdmiCapMap.put(TVOUT_DISCONNECT, "-1");
            super.closeTvoutDisplay();
        }
    }
}
