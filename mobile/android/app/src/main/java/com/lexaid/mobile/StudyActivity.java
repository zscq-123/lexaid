package com.lexaid.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class StudyActivity extends Activity {
    private static final Uri SITE = Uri.parse("https://154.36.185.110:18443/");
    private static final int SELECT_IMAGES = 10;
    private static final int PAPER = Color.rgb(245, 247, 250);

    private WebView web;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private TextView errorText;
    private Uri retryPage = SITE;
    private ValueCallback<Uri[]> pendingImages;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                var padding = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
                return WindowInsets.CONSUMED;
            });
        }

        web = new WebView(this);
        web.setBackgroundColor(PAPER);
        root.addView(web);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));
        buildErrorPanel(root);
        configureWebView();

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        }

        if (state == null || web.restoreState(state) == null) {
            web.loadUrl(SITE.toString());
        }
    }

    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isSite(uri)) return false;
                if (request.isForMainFrame() && request.hasGesture()) openInBrowser(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap icon) {
                Uri uri = Uri.parse(url);
                if (isSite(uri)) retryPage = uri;
                errorPanel.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showFailure("无法连接 LexAid。请检查网络后重试。", request.getUrl());
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) showFailure("页面暂不可用（HTTP " + response.getStatusCode() + "）。", request.getUrl());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Uri uri = Uri.parse(error.getUrl());
                if (isSite(uri)) showFailure("无法验证 LexAid 服务器的安全证书。", retryPage);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value < 100 && errorPanel.getVisibility() != View.VISIBLE
                        ? View.VISIBLE : View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                finishPicker(null);
                if (view.getUrl() == null || !isSite(Uri.parse(view.getUrl()))) {
                    callback.onReceiveValue(null);
                    return true;
                }
                pendingImages = callback;
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("image/*");
                picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"});
                picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(picker, SELECT_IMAGES);
                } catch (ActivityNotFoundException e) {
                    finishPicker(null);
                    Toast.makeText(StudyActivity.this, "设备没有可用的图片选择器", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) -> openInBrowser(Uri.parse(url)));
    }

    private boolean isSite(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && SITE.getHost().equalsIgnoreCase(uri.getHost())
                && uri.getPort() == SITE.getPort() && uri.getUserInfo() == null;
    }

    private void openInBrowser(Uri uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "设备没有可打开链接的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void buildErrorPanel(FrameLayout root) {
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(24), 0, dp(24), 0);
        errorPanel.setBackgroundColor(PAPER);
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel);
        errorText = new TextView(this);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTextSize(18);
        errorText.setTextColor(Color.rgb(33, 42, 55));
        errorText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        errorPanel.addView(errorText);
        Button retry = new Button(this);
        retry.setText("重新连接");
        retry.setOnClickListener(v -> web.loadUrl(retryPage.toString()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(16);
        errorPanel.addView(retry, lp);
    }

    private void showFailure(String message, Uri uri) {
        if (isSite(uri)) retryPage = uri;
        errorText.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SELECT_IMAGES || pendingImages == null) return;
        List<Uri> selected = new ArrayList<>();
        if (result == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) addImage(selected, clip.getItemAt(i).getUri());
            } else {
                addImage(selected, data.getData());
            }
        }
        finishPicker(selected.isEmpty() ? null : selected.toArray(new Uri[0]));
    }

    private void addImage(List<Uri> result, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme()) || checkUriPermission(uri, Process.myPid(),
                Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            String type = getContentResolver().getType(uri);
            if ("image/jpeg".equals(type) || "image/png".equals(type)) result.add(uri);
        } catch (SecurityException ignored) {
            // The provider can revoke the read grant before the picker returns.
        }
    }

    private void finishPicker(Uri[] selected) {
        if (pendingImages != null) {
            pendingImages.onReceiveValue(selected);
            pendingImages = null;
        }
    }

    private void navigateBack() {
        if (web.canGoBack()) web.goBack();
        else finish();
    }

    @Override public void onBackPressed() { navigateBack(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        web.saveState(state);
    }

    @Override protected void onPause() {
        super.onPause();
        web.onPause();
        CookieManager.getInstance().flush();
    }

    @Override protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override protected void onDestroy() {
        finishPicker(null);
        ((android.view.ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
