package com.example.zgranegrono;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;

import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1234;
    private WebView webView;
    private ProgressBar loader;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(android.graphics.Color.parseColor("#FF015C2B"));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                int flags = decorView.getSystemUiVisibility();
                
                // If the device is in LIGHT mode, the status bar icons should be BLACK
                // However, our background is DARK GREEN, so we want icons to stay WHITE (remove the flag)
                // If the user wants black icons for some reason, they would ADD the flag.
                // Assuming "nice" in dark theme means White icons on Green.
                // If in Light theme the header is ALSO green, we still want WHITE icons.
                
                // If you want BLACK icons in Light theme:
                boolean isDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                if (!isDarkMode) {
                    // This will make status bar icons BLACK (for light backgrounds)
                    // flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                
                // Since we force Green background, we usually want White icons regardless.
                // But you asked for "font should be as black" in normal/light theme.
                if (!isDarkMode) {
                     flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                     flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }

                decorView.setSystemUiVisibility(flags);
            }
        }

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        // loader = findViewById(R.id.loader); // Add this if you add a ProgressBar to activity_main.xml

        // webView.clearCache(false); // Do not clear cache for PWA
        webView.clearHistory();

        WebSettings settings = webView.getSettings();

        // === USER AGENT (Chrome-like) ===
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        // === JS + Web APIs ===
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // === CACHE ===
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // === MEDIA ===
        settings.setMediaPlaybackRequiresUserGesture(false);

        // === FILE ACCESS ===
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // === GEOLOCATION ===
        settings.setGeolocationEnabled(true);

        // === MIXED CONTENT (http + https) ===
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // === COOKIES ===
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // === WEBVIEW CLIENT (navigation + loading) ===
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // if (loader != null) loader.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // if (loader != null) loader.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    System.out.println("WebView error: " + error.getDescription());
                }
            }
        });

        // === CHROME CLIENT (video, fullscreen, dialogs, permissions) ===
        webView.setWebChromeClient(new WebChromeClient() {
            // Fullscreen video support
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                setContentView(view);
            }

            @Override
            public void onHideCustomView() {
                setContentView(R.layout.activity_main);
                webView = findViewById(R.id.webView); // Re-bind after layout change
            }

            // Grant permissions (Camera, Mic, etc.) to the PWA
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }

            // Geolocation permission auto-allow
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        checkAndRequestPermissions();

        // === LOAD URL ===
        webView.loadUrl("https://zgranegrono.pl?mobile=true");
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}