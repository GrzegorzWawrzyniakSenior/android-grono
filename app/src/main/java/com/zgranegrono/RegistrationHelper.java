package com.zgranegrono;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RegistrationHelper {
    private static final String TAG = "ZGRANE_REG";
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_LAST_TOKEN = "last_registered_token";

    @SuppressLint("HardwareIds")
    public static String getDeviceId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static void registerDevice(Context context, String fcmToken) {
        if (fcmToken == null || fcmToken.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastToken = prefs.getString(KEY_LAST_TOKEN, "");

        // Only register if the token has changed
        if (fcmToken.equals(lastToken)) {
            Log.d(TAG, "Registration skipped: token unchanged.");
            return;
        }

        String deviceId = getDeviceId(context);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://zgranegrono.pl/api/mobile/register");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject jsonParam = new JSONObject();
                jsonParam.put("device_id", deviceId);
                jsonParam.put("fsm", fcmToken);
                jsonParam.put("platform", "android");

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonParam.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d(TAG, "Device registered successfully on server.");
                    // Save the token only after successful registration
                    prefs.edit().putString(KEY_LAST_TOKEN, fcmToken).apply();
                } else {
                    Log.e(TAG, "Failed to register device. Response Code: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during device registration: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
