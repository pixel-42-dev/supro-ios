package ru.inko.supro;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Message;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new BridgeWebChromeClient(getBridge()) {

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
    }
}