package ru.inko.supro;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

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
        loadingOverlay.setBackgroundColor(Color.parseColor("#5d5fef"));
        loadingOverlay.setClickable(true); // блокируем клики по недогруженной странице

        ProgressBar spinner = new ProgressBar(this);
        spinner.getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerParams.gravity = Gravity.CENTER;
        loadingOverlay.addView(spinner, spinnerParams);

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

                // Убираем маркер WebView из User-Agent, чтобы ВК видел "обычный браузер"
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
                        // Возврат на наш сайт после авторизации
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

        // Если сигнал о загрузке так и не пришёл (плохая сеть и т.п.), убираем оверлей через 15 секунд, чтобы он не висел вечно
        webView.postDelayed(this::hideLoadingOverlay, 15000);
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