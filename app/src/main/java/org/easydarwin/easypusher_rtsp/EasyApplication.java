/*
	Copyright (c) 2012-2017 EasyDarwin.ORG.  All rights reserved.
	Github: https://github.com/EasyDarwin
	WEChat: EasyDarwin
	Website: http://www.easydarwin.org
*/
package org.easydarwin.easypusher_rtsp;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class EasyApplication extends Application {

    private static EasyApplication mApplication;
    private static Boolean mPushState;

    @Override
    public void onCreate() {
        super.onCreate();
        mApplication = this;
        mPushState = false;
    }

    public static EasyApplication getEasyApplication() {
        return mApplication;
    }

    public static Boolean getPushState(){
        return mPushState;
    }

    public static void setPushState(Boolean state){
        mPushState = state;
    }

    public void saveStringIntoPref(String key, String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public String getRTSPUrl() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String id = sharedPreferences.getString(Config.RTSP_URL, Config.DEFAULT_RTSP_URL);
        return id;
    }


    public String getServerIp() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String defValue = Config.DEFAULT_SERVER_IP;
        String ip = sharedPreferences.getString(Config.SERVER_IP, defValue);
        if (ip.equals(defValue)){
            sharedPreferences.edit().putString(Config.SERVER_IP, defValue).apply();
        }
        return ip;
    }

    public String getServerPort() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String defValue = Config.DEFAULT_SERVER_PORT;
        String port = sharedPreferences.getString(Config.SERVER_PORT, defValue);
        if (port.equals(defValue)) {
            sharedPreferences.edit().putString(Config.SERVER_PORT, defValue).apply();
        }
        return port;
    }

    public String getStreamName() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String defValue = Config.DEFAULT_STREAM_NAME;
        String name = sharedPreferences.getString(Config.STREAM_NAME, defValue);
        if (name.equals(defValue)){
            sharedPreferences.edit().putString(Config.STREAM_NAME, defValue).apply();
        }
        return name;
    }
}
