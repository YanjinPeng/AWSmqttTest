package com.mic.andy.awsmqtttest;

import android.util.Log;

import com.amazonaws.util.IOUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.mail.BodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by andy on 2017/5/2.
 */

public class AVSUploader {
    private static String token;
    private static String filePath;
    private final OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final MediaType AUDIO = MediaType.parse("application/octet-stream");

    public AVSUploader(String token, String filePath){
        this.token = token;
        this.filePath = filePath;
    }


    public void upload(final AVSUploadResponse listener)  {

        File sendAudio = new File(filePath);
        if (!sendAudio.exists()){
            listener.onError("File not found");
            return;
        }

        listener.onProgress();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata",null, RequestBody.create(JSON,getMetaData().toString()))
                .addFormDataPart("audio", null, RequestBody.create(AUDIO, sendAudio))
                .build();

        final Request request = new Request.Builder()
                .addHeader("Authorization","Bearer " + token)
                .url("https://avs-alexa-na.amazon.com/v20160207/events")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                listener.onError("Time out error");
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                Log.e("onresponse","onresponse code "+ response.code());

                if (!response.isSuccessful()) {
                    if (response.code() == 403){
                        listener.onError("Auth fail, Please login again");
                    }else{
                        listener.onError("error code " + response.code());
                    }
                    throw new IOException("Unexpected code " + response.code());
                }

                if (response.code() == 204){
                    listener.onError("Reconize fail. Please speak again");
                    return;
                }

                try{
                    ByteArrayDataSource dataSource = new ByteArrayDataSource(response.body().byteStream(), response.body().contentType().toString());
                    MimeMultipart multipart = new MimeMultipart(dataSource);
                    int count = multipart.getCount();
                    Log.e("count","body "+ count);
                    for (int i = 0; i < count; i++) {
                        BodyPart bodyPart = multipart.getBodyPart(i);
                        Log.e("content type",bodyPart.getContentType());
                        if (bodyPart.isMimeType("application/octet-stream")) {

                            listener.onSuccess(IOUtils.toByteArray(bodyPart.getInputStream()));

                        }
                    }
                }catch (Exception e){
                    listener.onError("unknown error");
                    e.printStackTrace();
                }

            }
        });
    }

    private JSONObject getMetaData(){
        try {
            String header = "{\"namespace\": \"SpeechRecognizer\"," +
                             "\"name\": \"Recognize\"," +
                             "\"messageId\": \"messageId-123\"," +
                             "\"dialogRequestId\": \"dialogRequestId-321\"}";
            JSONObject headerObject = new JSONObject(header);

            String payload = "{\"profile\": \"CLOSE_TALK\"," +
                    "           \"format\": \"AUDIO_L16_RATE_16000_CHANNELS_1\"}";
            JSONObject payloadObject = new JSONObject(payload);

            JSONObject eventJson = new JSONObject();
            eventJson.put("header",headerObject);
            eventJson.put("payload",payloadObject);

            JSONObject rootJson = new JSONObject();
            rootJson.put("event",eventJson);

            return rootJson;

        }catch (Exception e){
            return null;
        }

    }
}


