package com.mic.andy.awsmqtttest;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;


import com.amazon.identity.auth.device.AuthError;
import com.amazon.identity.auth.device.api.Listener;
import com.amazon.identity.auth.device.api.authorization.AuthCancellation;
import com.amazon.identity.auth.device.api.authorization.AuthorizationManager;
import com.amazon.identity.auth.device.api.authorization.AuthorizeListener;
import com.amazon.identity.auth.device.api.authorization.AuthorizeRequest;
import com.amazon.identity.auth.device.api.authorization.AuthorizeResult;
import com.amazon.identity.auth.device.api.authorization.Scope;
import com.amazon.identity.auth.device.api.authorization.ScopeFactory;
import com.amazon.identity.auth.device.api.workflow.RequestContext;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;

import ai.kitt.snowboy.AppResCopy;
import ai.kitt.snowboy.Constants;
import ai.kitt.snowboy.MsgEnum;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.cert.Certificate;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends Activity {
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com,
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "a1c5bvsmdje7oz.iot.us-east-1.amazonaws.com";
    // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
    // AWS IoT permissions.
    private static final String COGNITO_POOL_ID  = "us-east-1:c1926415-40b6-4068-8d15-49b7b6bbc4c9";
    private static final String SHADOWUPDATE     = "$aws/things/lightbulb/shadow/update/accepted";
    private static final String SHADOWGET        = "$aws/things/lightbulb/shadow/get";
    private static final String SHADOWGETACCEPT  = "$aws/things/lightbulb/shadow/get/accepted";
    private static final String navTopic         = "mio/dummyProduct/dummyModel/81f8180d-2b6d-4060-864c-42abb058040f/commands/navigateTo";

    private static final String PRODUCT_ID = "test_alexa";
    private static final String PRODUCT_DSN = "amzn1.application-oa2-client.9a76ce9adef149a7a62a2f3b8fea3d10";
    private String avsToken;
    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.US_EAST_1;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private MediaPlayer mediaPlayer = new MediaPlayer();
    UIUpdateReceiver uiUpdateReceiver;
    AWSIotMqttManager mqttManager;

    private static final Scope ALEXA_ALL_SCOPE = ScopeFactory.scopeNamed("alexa:all");


    CognitoCachingCredentialsProvider credentialsProvider;


    RelativeLayout layoutLoading, layoutMic;
    CircleProgressBar circleMic;

    TextView textStatus;
    TextView textColor;

    Button btnTalk, btnLogin;
    boolean isRecording = false;

    String clientId;
    String filename;
    AudioRecorder audioRecorder;

    SurfaceView viewColor;


    private RequestContext mRequestContext;

    public Handler handle = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            MsgEnum message = MsgEnum.getMsgEnum(msg.what);
            switch(message) {
                case MSG_RECORD_START:
                    //Update ui
                    circleMic.setProgress(100);
                    break;
                case MSG_RECORD_STOP:
                    uploadAVS();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private class UIUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.ACTION_RECORD_START)) {
                // Do stuff - maybe update my view based on the changed DB contents
            }
            if (intent.getAction().equals(Constants.ACTION_RECORD_STOP)) {
                // Do stuff - maybe update my view based on the changed DB contents
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findView();
        initMqttManager();
        AppResCopy.copyResFromAssetsToSD(this);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    0);

        }

        Intent i = new Intent(this, AVSService.class);
        startService(i);

//        filename = getApplicationContext().getCacheDir().getAbsolutePath() + "/audio.wav";
//        audioRecorder = new AudioRecorder(this, handle, filename);

        mRequestContext = RequestContext.create(this);
        mRequestContext.registerListener(new AuthorizeListener() {
            @Override
            public void onSuccess(AuthorizeResult authorizeResult) {
                AuthorizationManager.getToken(MainActivity.this, new Scope[] { ALEXA_ALL_SCOPE }, new TokenListener());
            }

            @Override
            public void onError(AuthError authError) {
                showAlertDialog("Error","auth failed");
            }

            @Override
            public void onCancel(AuthCancellation authCancellation) {
                showAlertDialog("","User canceled");
            }
        });

        onClick();
//        gotoMagellanGPS("San francisco");

    }

    private void findView(){
        layoutLoading = (RelativeLayout)findViewById(R.id.layout_loading);
        textStatus = (TextView)findViewById(R.id.text_status);
        textColor = (TextView)findViewById(R.id.text_color);
        viewColor = (SurfaceView)findViewById(R.id.view_color);
        btnTalk = (Button)findViewById(R.id.button_talk);
        btnLogin = (Button)findViewById(R.id.button_login);
        layoutMic = (RelativeLayout)findViewById(R.id.layout_mic);
        circleMic = (CircleProgressBar)findViewById(R.id.circle_mic);
    }

    //---------------
    // MARK - OnClick
    //---------------
    private void onClick(){
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginAVS();
            }
        });


        layoutMic.setClickable(false);
        layoutMic.setEnabled(true);
        layoutMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, AVSService.class);
                stopService(i);
            }
        });
//        layoutMic.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                Log.e("touch","toutoutouototuo");
//                switch (event.getAction()){
//                    case MotionEvent.ACTION_DOWN:
//
//                        toggleMicListening(true);
//                        return true;
//                    case MotionEvent.ACTION_UP:
//                        circleMic.setProgress(0);
//                        toggleMicListening(false);
//                        return true;
//                }
//                return true;
//            }
//        });
    }

    @Override
    protected void onStart(){
        super.onStart();
        AuthorizationManager.getToken(this, new Scope[] { ALEXA_ALL_SCOPE }, new TokenListener());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("MAin","onResume");
//        if (null == uiUpdateReceiver){uiUpdateReceiver = new UIUpdateReceiver();}

//        toggleMicListening(true);
//        if (mqttManager != null){
//            getLatestShadow();
//        }
        mRequestContext.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.e("stop","onStop");
        toggleMicListening(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    //---------------
    // MARK - MQTT
    //---------------
    private void initMqttManager(){
        clientId = UUID.randomUUID().toString();

        credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(), // context
                COGNITO_POOL_ID, // Identity Pool ID
                MY_REGION // Region
        );

        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);
        mqttManager.setKeepAlive(30);
        mqttManager.setAutoReconnect(true);




        AWSIotKeystoreHelper.keystoreContainsAlias()
        mqttManager.connect();
        CreateKeysAndCertificateRequest createKeysAndCertificateRequest =
                new CreateKeysAndCertificateRequest();
        createKeysAndCertificateRequest.setSetAsActive(true);
        final CreateKeysAndCertificateResult createKeysAndCertificateResult;
        createKeysAndCertificateResult =
                mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
        AWSIotClient




        mqttManager.connect(credentialsProvider, new AWSIotMqttClientStatusCallback() {
            @Override
            public void onStatusChanged(final AWSIotMqttClientStatus status, Throwable throwable) {
                Log.e("status","" + status.toString());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (status == AWSIotMqttClientStatus.Connected){
                            textStatus.setText("連接成功");
                            layoutLoading.setVisibility(View.GONE);
//                            subScribeTopic("abc");
                            mqttManager.subscribeToTopic(SHADOWUPDATE, AWSIotMqttQos.QOS1, messageCallback);
                            mqttManager.subscribeToTopic(SHADOWGETACCEPT, AWSIotMqttQos.QOS1, messageCallback);
                            mqttManager.subscribeToTopic(navTopic, AWSIotMqttQos.QOS1, naviCallback);
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    getLatestShadow();
                                }
                            }, 3000);

                        }
                        else if (status == AWSIotMqttClientStatus.Connecting){
                            textStatus.setText("連接中...");
                            layoutLoading.setVisibility(View.VISIBLE);
                        }
                        else if (status == AWSIotMqttClientStatus.Reconnecting){

                        }
                        else if (status == AWSIotMqttClientStatus.ConnectionLost){

                        }
                    }
                });

            }
        });
    }

    private AWSIotMqttNewMessageCallback messageCallback = new AWSIotMqttNewMessageCallback() {
        @Override
        public void onMessageArrived(final String topic,final byte[] data) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String message = new String(data, "UTF-8");
                        Log.d(LOG_TAG, "Message arrived:");
                        Log.d(LOG_TAG, "   Topic: " + topic);
                        Log.d(LOG_TAG, " Message: " + message);

                        JSONObject recJson = new JSONObject(message).getJSONObject("state").getJSONObject("reported");

                        if (recJson.has("isOn")){
                            if (recJson.getBoolean("isOn")){
                                viewColor.setBackgroundColor(Color.YELLOW);
                                textColor.setText("ON");
                            }else {
                                viewColor.setBackgroundColor(Color.BLACK);
                                textColor.setText("OFF");
                            }
                        }

                    } catch (UnsupportedEncodingException e) {
                        Log.e(LOG_TAG, "Message encoding error.", e);
                    } catch (JSONException je){

                    }
                }
            });
        }
    };

    private AWSIotMqttNewMessageCallback naviCallback = new AWSIotMqttNewMessageCallback() {
        @Override
        public void onMessageArrived(final String topic,final byte[] data) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String message = new String(data, "UTF-8");
                        Log.d(LOG_TAG, "Message arrived:");
                        Log.d(LOG_TAG, "   Topic: " + topic);
                        Log.d(LOG_TAG, " Message: " + message);

                        JSONObject recJson = new JSONObject(message);
                        String location = recJson.getJSONObject("command").getJSONObject("navigateTo").getString("location");
                        gotoMagellanGPS(location);

                    } catch (UnsupportedEncodingException e) {
                        Log.e(LOG_TAG, "Message encoding error.", e);
                    } catch (JSONException je){

                    }
                }
            });
        }
    };

    private void getLatestShadow(){
        try {
            mqttManager.publishString("", SHADOWGET, AWSIotMqttQos.QOS1);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Publish error.", e);
        }
    }




    //---------------
    // MARK - AVS
    //---------------
    private void loginAVS(){
        final JSONObject scopeData = new JSONObject();
        final JSONObject productInstanceAttributes = new JSONObject();

        try {
            productInstanceAttributes.put("deviceSerialNumber", PRODUCT_DSN);
            scopeData.put("productInstanceAttributes", productInstanceAttributes);
            scopeData.put("productID", PRODUCT_ID);

            AuthorizationManager.authorize(new AuthorizeRequest.Builder(mRequestContext)
                    .addScope(ScopeFactory.scopeNamed("alexa:all", scopeData))
                    .forGrantType(AuthorizeRequest.GrantType.ACCESS_TOKEN)
                    .shouldReturnUserData(false)
                    .build());
        } catch (JSONException e) {
            // handle exception here
        }
    }



    public class TokenListener implements Listener<AuthorizeResult, AuthError> {
        /* getToken completed successfully. */
        @Override
        public void onSuccess(AuthorizeResult authorizeResult) {
            avsToken = authorizeResult.getAccessToken();
            if (avsToken == null){
                Log.e("Andy","token null");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnLogin.performClick();
                    }
                });

            }else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        layoutMic.setEnabled(true);
                    }
                });
                Log.e("success","token " + avsToken);
            }

        }

        /* There was an error during the attempt to get the token. */
        @Override
        public void onError(AuthError authError) {
            showAlertDialog("Error","auth failed");
        }
    }

    private void toggleMicListening(boolean start){
        if (start)
        {
            btnTalk.setText("Stop and Send");
//            audioRecorder.start();
        }
        else
        {
            btnTalk.setText("Start Talking");
//            audioRecorder.stop();
//            uploadAVS();
        }
    }

    private void uploadAVS(){
        AVSUploader avsUploader = new AVSUploader(avsToken, filename);
        avsUploader.upload(new AVSUploadResponse() {
            @Override
            public void onSuccess(byte[] audioInput) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        circleMic.stopAnimating();
                    }
                });
                playMp3(audioInput);
            }
            @Override
            public void onProgress() {
                Log.e("response","on progress");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        circleMic.startAnimating();
                    }
                });
            }
            @Override
            public void onError(final String errorMessage) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        circleMic.stopAnimating();
                        showAlertDialog("Error",errorMessage);
                    }
                });

            }
        });
    }

    private void playMp3(byte[] mp3SoundByteArray) {
        try {
            // create temp file that will hold byte array
            File tempMp3 = File.createTempFile("recieve", "wav", getCacheDir());
            tempMp3.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempMp3);
            fos.write(mp3SoundByteArray);
            fos.close();

            // resetting mediaplayer instance to evade problems
            mediaPlayer.reset();

            // In case you run into issues with threading consider new instance like:
            // MediaPlayer mediaPlayer = new MediaPlayer();

            // Tried passing path directly, but kept getting
            // "Prepare failed.: status=0x1"
            // so using file descriptor instead
            FileInputStream fis = new FileInputStream(tempMp3);
            mediaPlayer.setDataSource(fis.getFD());

            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException ex) {
            String s = ex.toString();
            ex.printStackTrace();
        }
    }

    private void gotoGoogleMap(String location){
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + location);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        startActivity(mapIntent);
    }

    private void gotoMagellanGPS(String location){
        Intent naviIntent = getPackageManager().getLaunchIntentForPackage("com.magellangps.SmartGPS");
        Bundle naviBundle = new Bundle();
        naviBundle.putString("destination", location);
        naviIntent.putExtra("HAS_START_ROUTE", naviBundle);

        startActivity(naviIntent);
    }


    public void showAlertDialog(String title,String message){
        final android.app.AlertDialog.Builder alert = new android.app.AlertDialog.Builder(this);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setCancelable(true);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alert.show();
            }
        });

    }

}
