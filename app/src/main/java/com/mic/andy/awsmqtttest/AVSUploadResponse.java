package com.mic.andy.awsmqtttest;

import java.io.InputStream;

/**
 * Created by andy on 2017/5/2.
 */

public interface AVSUploadResponse {
    void onSuccess(byte[] audioInput);
    void onProgress();
    void onError(String errorMessage);
}
