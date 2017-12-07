package com.mic.andy.awsmqtttest;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import ai.kitt.snowboy.Constants;
import ai.kitt.snowboy.MsgEnum;
import ai.kitt.snowboy.SnowboyDetect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by andy on 2017/4/28.
 */

public class AudioRecorder {
    static { System.loadLibrary("snowboy-detect-android"); }
//    private MediaRecorder mRecorder = null;
    private static final String TAG = AudioRecorder.class.getSimpleName();
    private AudioRecord mRecorder = null;
    final static int rate = 16000;
    final static int channel = AudioFormat.CHANNEL_IN_MONO;
    final static int format = AudioFormat.ENCODING_PCM_16BIT;
    final static int bufferSize = AudioRecord.getMinBufferSize(rate,channel,format) * 2;
    private Thread recordingThread = null;

    private static final String ACTIVE_RES = Constants.ACTIVE_RES;
    private static final String ACTIVE_UMDL = Constants.ACTIVE_UMDL;
    private static String strEnvWorkSpace = Constants.DEFAULT_WORK_SPACE;
    private String activeModel = strEnvWorkSpace+ACTIVE_UMDL;
    private String commonRes = strEnvWorkSpace+ACTIVE_RES;

    private SnowboyDetect detector = new SnowboyDetect(commonRes, activeModel);
    private MediaPlayer player = new MediaPlayer();
    private Handler handler = null;

    private Context mContext = null;
    boolean isRecording = false;
    boolean micOpening = false;
    final static int COUNTDOWN = 30;
    int countDown = COUNTDOWN;
    String mFilename;

    public AudioRecorder(Context context, Handler handler, String filename){
        mContext = context;
        mFilename = filename;
        this.handler = handler;

        detector.SetSensitivity("0.6");
        //-detector.SetAudioGain(1);
        detector.ApplyFrontend(true);
        try {
            player.setDataSource(strEnvWorkSpace+"ding.wav");
            player.prepare();
        } catch (IOException e) {
            Log.e(TAG, "Playing ding sound error", e);
        }
    }

    public void start(){
        try{
            mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,rate,channel,format,bufferSize);
//            mRecorder = findAudioRecord();
        }catch (Exception e) {
            e.printStackTrace();
        }

        mRecorder.startRecording();
        micOpening = true;
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private AudioRecord findAudioRecord() {
        // TODO Auto-generated method stub
        for (int rate : new int[]{16000}) {

            for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT }) {

                for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {

                    try {

                        Log.d("AudiopRecording", "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "

                                + channelConfig);

                        int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

                        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {

                            // check if we can instantiate and have a success

                            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, channelConfig, audioFormat, bufferSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED)

                                return recorder;

                        }

                    } catch (Exception e) {

                        Log.e("AudiopRecording", rate + "Exception, keep trying.",e);

                    }

                }

            }

        }


        return null;
    }

    public void stop() {
        // stops the recording activity
        if (null != mRecorder) {
            isRecording = false;
            micOpening = false;
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
            recordingThread = null;
        }
//        SystemClock.sleep(1000);
//        playRecording();

    }


    private void writeAudioDataToFile() {
        // Write the output audio in byte

        String filePath = mFilename;
        byte bData[] = new byte[bufferSize];
        byte sData[] = new byte[bufferSize];

        FileOutputStream os = null;
        try{
            os = new FileOutputStream(filePath);
        }catch (FileNotFoundException fe) {
            fe.printStackTrace();
        }


        detector.Reset();

        while (micOpening) {
            // gets the voice output from microphone to byte format

            mRecorder.read(bData, 0, bufferSize);
//            System.out.println("bdata" + ""+bData.length);
//            System.out.println("sdata" + ""+sData.length);

            short[] audioData = new short[bufferSize / 2];
            ByteBuffer.wrap(bData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(audioData);

            if (isRecording)
            {
                System.out.println("After" + sData.length);
                try {
                // // writes the data to file from buffer
                // // stores the voice buffer

                    os.write(bData, 0, bufferSize);

                    countDown = audioData[0]>850 ? COUNTDOWN : countDown-1;

                    Log.i("audio ","" + audioData[0]);
                    Log.i("countdown","" + countDown);

                    if (countDown == 0){
                        isRecording = false;
                        countDown = COUNTDOWN;
                        try {
                            os.close();
                            Message msg = handler.obtainMessage(MsgEnum.MSG_RECORD_STOP.ordinal(), null);
                            handler.sendMessage(msg);
//                            playRecording();
                            try{
                                os = new FileOutputStream(filePath);
                            }catch (FileNotFoundException fe) {
                                fe.printStackTrace();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else //Detecting
            {
                int result = detector.RunDetection(audioData, audioData.length);
                if (result == -2) {
                    Log.i("Snowboy: ", "-2");
                    // post a higher CPU usage:
                    // sendMessage(MsgEnum.MSG_VAD_NOSPEECH, null);
                } else if (result == -1) {
                    Log.i("Snowboy: ", "-1");
                } else if (result == 0) {
                    // post a higher CPU usage:
                    // sendMessage(MsgEnum.MSG_VAD_SPEECH, null);
                } else if (result > 0) {
                    Log.i("Snowboy: ", "Hotword " + Integer.toString(result) + " detected!");
                    player.start();
//                    isRecording = true;
                    Message msg = handler.obtainMessage(MsgEnum.MSG_HOTWORD_DETECT.ordinal(), null);
                    handler.sendMessage(msg);
                }
            }

        }// while


    }


    //TODO Do in Thread
    private void playRecording() {

        String fileName = mFilename;
        File file = new File(fileName);

        byte[] audioData = null;

        try {
            InputStream inputStream = new FileInputStream(fileName);

            audioData = new byte[bufferSize];

            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO, format, bufferSize, AudioTrack.MODE_STREAM);
            audioTrack.play();
            int i=0;

            while((i = inputStream.read(audioData)) != -1) {
                audioTrack.write(audioData,0,i);
            }

        } catch(FileNotFoundException fe) {
            Log.e("audiotrack","File not found");
        } catch(IOException io) {
            Log.e("audiotrack","IO Exception");
        }
    }


}
