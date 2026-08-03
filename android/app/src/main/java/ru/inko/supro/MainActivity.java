package ru.inko.supro;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {

    private FrameLayout loadingOverlay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(Color.parseColor("#f6f6f6"));
        loadingOverlay.setClickable(true); // блокируем клики по недогруженной странице

        ImageView loadingGif = new ImageView(this);
        int gifSizePx = (int) (120 * getResources().getDisplayMetrics().density); // 120dp
        FrameLayout.LayoutParams gifParams = new FrameLayout.LayoutParams(gifSizePx, gifSizePx);
        gifParams.gravity = Gravity.CENTER;
        loadingOverlay.addView(loadingGif, gifParams);
        Glide.with(this)
                .asGif()
                .load(R.raw.loading)
                .into(loadingGif);

        ((ViewGroup) webView.getParent()).addView(loadingOverlay,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        webView.setWebViewClient(new BridgeWebViewClient(getBridge()) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                        "(function(){ if (document.readyState === 'complete') return 'ready';" +
                                " return 'wait'; })()",
                        state -> {
                            if ("\"ready\"".equals(state)) {
                                hideLoadingOverlay();
                            } else {
                                view.evaluateJavascript(
                                        "window.addEventListener('load', function(){" +
                                                "  console.log('__SUPRO_PAGE_READY__');" +
                                                "}, { once: true });", null);
                            }
                        });
            }
        });

        webView.setWebChromeClient(new BridgeWebChromeClient(getBridge()) {

            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                if ("__SUPRO_PAGE_READY__".equals(msg.message())) {
                    hideLoadingOverlay();
                    return true;
                }
                return super.onConsoleMessage(msg);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {

                WebView popup = new WebView(MainActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);
                popup.getSettings().setDomStorageEnabled(true);
                popup.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

                String ua = popup.getSettings().getUserAgentString().replace("; wv", "");
                popup.getSettings().setUserAgentString(ua);

                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(popup, true);

                Dialog dialog = new Dialog(MainActivity.this,
                        android.R.style.Theme_Black_NoTitleBar_Fullscreen);

                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        String scheme = request.getUrl().getScheme();

                        if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) {
                            try {
                                Intent intent;
                                if (scheme.equals("intent")) {
                                    intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                                } else {
                                    intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                                }
                                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                                startActivity(intent);
                                return true;
                            } catch (Exception e) {
                                if (scheme.equals("intent")) {
                                    try {
                                        Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                                        String fallback = parsed.getStringExtra("browser_fallback_url");
                                        if (fallback != null) {
                                            v.loadUrl(fallback);
                                            return true;
                                        }
                                    } catch (Exception ignored) {}
                                }
                                return true;
                            }
                        }

                        if (request.getUrl().getHost() != null
                                && request.getUrl().getHost().endsWith("xn--n1abdck.xn--p1ai")) {
                            if (dialog.isShowing()) {
                                CookieManager.getInstance().flush();
                                getBridge().getWebView().loadUrl(request.getUrl().toString());
                                dialog.dismiss();
                            }
                            return true;
                        }
                        return false;
                    }
                });

                dialog.setContentView(popup, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                dialog.setOnDismissListener(d -> {
                    CookieManager.getInstance().flush();
                    popup.postDelayed(popup::destroy, 1500);
                });
                dialog.show();

                popup.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        CookieManager.getInstance().flush();
                        dialog.dismiss();
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        webView.postDelayed(this::hideLoadingOverlay, 15000);

        android.view.View content = findViewById(android.R.id.content);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            int ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(0, 0, 0, ime);
            return insets;
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Window window = getWindow();
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(window, window.getDecorView());
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay == null) return;
        FrameLayout overlay = loadingOverlay;
        loadingOverlay = null;
        overlay.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> {
                    ViewGroup parent = (ViewGroup) overlay.getParent();
                    if (parent != null) parent.removeView(overlay);
                });
    }
}