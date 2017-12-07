package com.mic.andy.awsmqtttest;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import ai.kitt.snowboy.MsgEnum;

public class AVSService extends Service {
    public AVSService() {
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
       return null;
    }

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private Handler uiHandler;
    AudioRecorder audioRecorder;
    private String recordName;
    private static PowerManager.WakeLock wakeLock;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            MsgEnum message = MsgEnum.getMsgEnum(msg.what);
            switch(message) {
                case MSG_HOTWORD_DETECT:
                    if ( MyApplication.getInstance().applicationInForeground()){
                        Log.e("detect","app in foreground");

                    }else {
                        Log.e("detect","app in background");
                        Intent alarmIntent = new Intent();
                        alarmIntent.setClass(getApplicationContext(), MainActivity.class);
                        alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        alarmIntent.putExtra("time",bData.getString("time")+bData.getString("type"));
                        startActivity(alarmIntent);

                        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
                        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "");
                        wakeLock.acquire();

                        wakeLock.release();
                    }
                    break;

                case MSG_RECORD_START:
                    //Update ui
//                    circleMic.setProgress(100);
                    break;
                case MSG_RECORD_STOP:
//                    uploadAVS();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        Log.e("onstart","start service");
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        if (null == audioRecorder){
            recordName = getApplicationContext().getCacheDir().getAbsolutePath() + "/audio.wav";
            audioRecorder = new AudioRecorder(this, mServiceHandler, recordName);
            audioRecorder.start();
        }

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }



    @Override
    public void onDestroy() {
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
        Log.e("onDestroy","destroy service");
        mServiceHandler.removeMessages(1);
        mServiceHandler = null;
        audioRecorder.stop();
        audioRecorder = null;
    }

}
