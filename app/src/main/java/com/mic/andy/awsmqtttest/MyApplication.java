package com.mic.andy.awsmqtttest;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Created by andy on 2017/7/5.
 */

public class MyApplication extends Application {
    private static MyApplication mInstance;

    public static MyApplication getInstance(){
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }

    public boolean applicationInForeground() {
        ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> services = activityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo appProcess : services) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(getPackageName())) {
                                Log.e("app",appProcess.processName);
                return true;
            }
        }
        return false;

    }
}
