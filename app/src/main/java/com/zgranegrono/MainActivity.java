package com.zgranegrono;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1234;
    private static final String TAG = "ZGRANE_FCM";
    private WebView webView;
    private View errorContainer;
    private TextView tvErrorTitle;
    private TextView tvErrorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialize notification channel immediately
        createNotificationChannel();

        configureStatusBar();
        setupOnBackPressed();

        setContentView(R.layout.activity_main);
        initViews();

        setupWebView(webView);
        checkAndRequestPermissions();
        logTokensAndLoadUrl();
        handleIntent(getIntent());
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        errorContainer = findViewById(R.id.errorContainer);
        tvErrorTitle = findViewById(R.id.tvErrorTitle);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        Button btnRefresh = findViewById(R.id.btnRefresh);

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                showErrorLayout(false, null, null);
                if (webView != null) {
                    webView.clearCache(true);
                    webView.reload();
                }
            });
        }
    }

    private void showErrorLayout(boolean show, String title, String message) {
        if (errorContainer != null) {
            errorContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (webView != null) {
            webView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (show) {
            if (tvErrorTitle != null && title != null) {
                tvErrorTitle.setText(title);
            }
            if (tvErrorMessage != null && message != null) {
                tvErrorMessage.setText(message);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getExtras() != null) {
            Log.d(TAG, "Intent extras: " + intent.getExtras().keySet());
            for (String key : intent.getExtras().keySet()) {
                Log.d(TAG, "Key: " + key + " Value: " + intent.getExtras().get(key));
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    MyFirebaseMessagingService.CHANNEL_ID,
                    "General Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for news and updates from ZGraneGrono");
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "Channel created: " + MyFirebaseMessagingService.CHANNEL_ID);
            }
        }
    }

    private void configureStatusBar() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(Color.parseColor("#FF015C2B"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            boolean isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

            if (!isDarkMode) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        }
    }

    private void setupOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (errorContainer != null && errorContainer.getVisibility() == View.VISIBLE) {
                    showErrorLayout(false, null, null);
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                } else if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(WebView wv) {
        if (wv == null) return;

        // Enable Remote Debugging via Chrome DevTools (chrome://inspect)
        if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings settings = wv.getSettings();

        // Append custom app token to default browser User Agent to maintain Chrome compatibility
        String defaultUserAgent = settings.getUserAgentString();
        if (defaultUserAgent != null && !defaultUserAgent.contains("ZGraneGronoApp")) {
            settings.setUserAgentString(defaultUserAgent + " ZGraneGronoApp");
        }

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                showErrorLayout(false, null, null);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                String sslDetails = getSslErrorDetails(error);
                Log.e(TAG, "WebView SSL Error: " + sslDetails);

                // Allow SSL bypass in DEBUG builds so developers can test while the server SSL chain is being fixed
                if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
                    Log.w(TAG, "DEBUG MODE: Bypassing SSL error for testing.");
                    handler.proceed();
                    return;
                }

                handler.cancel();
                showErrorLayout(true, "Błąd certyfikatu SSL", sslDetails);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    CharSequence description = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && error != null)
                            ? error.getDescription() : "Nieznany błąd połączenia";
                    Log.e(TAG, "WebView Error: " + description);
                    showErrorLayout(true, "Błąd połączenia z serwerem", description.toString());
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WebViewConsole", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                setContentView(view);
            }

            @Override
            public void onHideCustomView() {
                setContentView(R.layout.activity_main);
                initViews();
                setupWebView(webView);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
    }

    private String getSslErrorDetails(SslError error) {
        if (error == null) return "Nieznany błąd SSL";
        switch (error.getPrimaryError()) {
            case SslError.SSL_UNTRUSTED:
                return "Certyfikat nie jest zaufany (SSL_UNTRUSTED).\n\nCzęsta przyczyna: Na serwerze brakuje certyfikatu pośredniego (intermediate CA / fullchain.pem). Chrome pobiera go automatycznie w tle, ale Android WebView tego nie robi.";
            case SslError.SSL_IDMISMATCH:
                return "Niezgodność nazwy domeny z certyfikatem (SSL_IDMISMATCH).\n\nCertyfikat wydany dla innej domeny (np. www.zgranegrono.pl vs zgranegrono.pl).";
            case SslError.SSL_EXPIRED:
                return "Certyfikat SSL wygasł (SSL_EXPIRED).";
            case SslError.SSL_NOTYETVALID:
                return "Certyfikat nie jest jeszcze ważny lub niepoprawna data na urządzeniu (SSL_NOTYETVALID).";
            case SslError.SSL_DATE_INVALID:
                return "Niepoprawna data certyfikatu (SSL_DATE_INVALID).";
            case SslError.SSL_INVALID:
            default:
                return "Nieprawidłowy certyfikat SSL (kod " + error.getPrimaryError() + ").";
        }
    }

    private void logTokensAndLoadUrl() {
        FirebaseMessaging.getInstance().subscribeToTopic("all")
                .addOnCompleteListener(task -> Log.d(TAG, "Subscribed to 'all' topic: " + task.isSuccessful()));

        final boolean[] isUrlLoaded = {false};
        Handler handler = new Handler(Looper.getMainLooper());

        Runnable timeoutRunnable = () -> {
            if (!isUrlLoaded[0]) {
                isUrlLoaded[0] = true;
                Log.w(TAG, "FCM token retrieval timed out, loading URL without token.");
                loadFinalUrl();
            }
        };
        handler.postDelayed(timeoutRunnable, 2000);

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String token = (task.isSuccessful() && task.getResult() != null) ? task.getResult() : "";
                    Log.d(TAG, "FCM Token retrieved: " + token);

                    if (!isUrlLoaded[0]) {
                        isUrlLoaded[0] = true;
                        handler.removeCallbacks(timeoutRunnable);
                        loadFinalUrl();
                    }

                    // Call registration regardless of whether the URL was already loaded
                    if (!token.isEmpty()) {
                        RegistrationHelper.registerDevice(getApplicationContext(), token);
                    }
                });
    }

    private void loadFinalUrl() {
        if (webView != null) {
            String deviceId = RegistrationHelper.getDeviceId(this);
            String url = "https://zgranegrono.pl?mobile=true&platform=android&device_id=" + deviceId;
            Log.d(TAG, "Loading URL: " + url);
            webView.loadUrl(url);
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }
}
