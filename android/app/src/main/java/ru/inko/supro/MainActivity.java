package ru.inko.supro;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.BridgeWebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BridgeActivity {

    private static final String SITE_HOST = "xn--n1abdck.xn--p1ai";
    private static final String SITE_URL = "https://xn--n1abdck.xn--p1ai";

    private static final String PREFS_NAME = "supro_app";
    private static final String PREF_DRAWER_MENU = "drawer_menu";

    private static final int COLOR_BRAND = 0xFF5D5FEF;
    private static final int COLOR_DRAWER_BG = 0xFF1F2033;
    private static final int COLOR_DRAWER_SECTION = 0xFF9A9CB8;
    private static final int COLOR_DRAWER_SELECTED = 0x33FFFFFF;

    // Прячет элементы сайта, которые дублирует нативная оболочка
    private static final String HIDE_SITE_CHROME_JS =
            "(function(){" +
                    "if (!document.getElementById('supro-app-style')) {" +
                    "  var s = document.createElement('style');" +
                    "  s.id = 'supro-app-style';" +
                    "  s.textContent = '.pageHeader .header__wrapper, .landing-header, #navbar-sub-menu," +
                    " .footer__columns, .footer__policy { display: none !important; }';" +
                    "  (document.head || document.documentElement).appendChild(s);" +
                    "}" +
                    "if (window.jQuery) { setTimeout(function(){ jQuery(window).trigger('resize'); }, 50); }" +
                    "})();";

    // Читает со страницы заголовок, пользователя и меню разделов
    private static final String READ_PAGE_INFO_JS =
            "(function(){" +
                    "var r = {title: '', user: '', avatar: '', items: []};" +
                    "var t = document.getElementById('page-sub-title');" +
                    "r.title = t ? t.textContent.trim() : '';" +
                    "var u = document.querySelector('.header__user--login');" +
                    "if (u) r.user = u.textContent.trim();" +
                    "var a = document.querySelector('.userAvatarInMainButton img');" +
                    "if (a) r.avatar = a.src;" +
                    "document.querySelectorAll('.mainDropdownMenu .nav__title, .mainDropdownMenu .nav__item a').forEach(function(el){" +
                    "  if (el.classList.contains('nav__title')) {" +
                    "    r.items.push({type: 'title', text: el.textContent.trim()});" +
                    "  } else {" +
                    "    var n = el.querySelector('.nav__item-name');" +
                    "    var i = el.querySelector('img');" +
                    "    r.items.push({type: 'item', text: n ? n.textContent.trim() : el.textContent.trim()," +
                    "      href: el.href, icon: i ? i.src : ''});" +
                    "  }" +
                    "});" +
                    "return r;" +
                    "})()";

    private FrameLayout loadingOverlay;

    // Экран ошибки загрузки
    private FrameLayout errorOverlay;
    private TextView errorTitle;
    private TextView errorText;
    private Button errorRetryButton;
    private String failedUrl;
    private boolean pageHasError = false;
    private boolean pageLoading = false;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Нативная оболочка: верхняя панель и боковое меню
    private DrawerLayout drawerLayout;
    private LinearLayout mainColumn;
    private View statusBarSpacer;
    private TextView toolbarTitle;
    private TextView toolbarMenuButton;
    private LinearLayout drawerPanel;
    private LinearLayout drawerItemsContainer;
    private ImageView drawerAvatar;
    private TextView drawerUserName;
    private String lastMenuKey = null;
    private final List<View> drawerItemRows = new ArrayList<>();
    private final List<String> drawerItemHrefs = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Верхняя панель и боковое меню вокруг WebView
        setupAppChrome();

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

        // Экран ошибки — добавляется после оверлея загрузки, поэтому лежит поверх него
        createErrorOverlay((ViewGroup) webView.getParent());

        webView.setWebViewClient(new BridgeWebViewClient(getBridge()) {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageHasError = false;
                pageLoading = true;
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                // Прячем шапку сайта как можно раньше, чтобы она не мелькала
                if (isSiteUrl(url)) {
                    view.evaluateJavascript(HIDE_SITE_CHROME_JS, null);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    pageHasError = true;
                    failedUrl = request.getUrl().toString();
                    showErrorOverlay();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                int code = response.getStatusCode();
                if (request.isForMainFrame() && (code == 502 || code == 503 || code == 504)) {
                    pageHasError = true;
                    failedUrl = request.getUrl().toString();
                    showErrorOverlay();
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                // Во время обычной загрузки страницы ничего не читаем — дождёмся onPageFinished.
                // Здесь обрабатываем только переходы внутри страницы без перезагрузки
                if (pageLoading) return;
                view.postDelayed(() -> view.evaluateJavascript(READ_PAGE_INFO_JS,
                        value -> applyPageInfo(value, url, false, false)), 300);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoading = false;

                if (pageHasError) {
                    hideLoadingOverlay();
                    return;
                }

                hideErrorOverlay();

                if (isSiteUrl(url)) {
                    view.evaluateJavascript(HIDE_SITE_CHROME_JS, null);
                    view.evaluateJavascript(READ_PAGE_INFO_JS, value -> applyPageInfo(value, url, true, true));
                }

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

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                downloadFile(url, userAgent));

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
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        String scheme = request.getUrl().getScheme();

                        // intent:// и другие не-http схемы — открываем соответствующее приложение
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

                        // Ссылки на файлы — качаем сами, попап закрываем
                        if (url.contains("/download/") || url.contains("/attachment/")) {
                            downloadFile(url, v.getSettings().getUserAgentString());
                            dialog.dismiss();
                            return true;
                        }

                        // Возврат на наш сайт после авторизации
                        if (request.getUrl().getHost() != null
                                && request.getUrl().getHost().endsWith(SITE_HOST)) {
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

                popup.setDownloadListener((dlUrl, dlUserAgent, dlDisposition, dlMime, dlLength) -> {
                    downloadFile(dlUrl, dlUserAgent);
                    dialog.dismiss();
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

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Сначала закрываем открытое боковое меню
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                WebView wv = getBridge().getWebView();
                if (wv != null && wv.canGoBack()) {
                    wv.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        // Автоповтор: как только появилась сеть, а на экране ошибка — перезагружаем страницу
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    if (errorOverlay != null && errorOverlay.getVisibility() == View.VISIBLE) {
                        retryLoad();
                    }
                });
            }
        };
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        try {
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (networkCallback != null) {
                connectivity.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception ignored) {}
        super.onDestroy();
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

    private boolean isSiteUrl(String url) {
        return url != null && url.contains(SITE_HOST);
    }

    // ===================== Нативная оболочка =====================

    /**
     * Перестраивает экран: [полоса под статус-бар] + [верхняя панель] + [WebView],
     * и добавляет боковое меню. Отступы под системные панели и клавиатуру считает сама.
     */
    private void setupAppChrome() {
        float d = getResources().getDisplayMetrics().density;

        // Достаём разметку Capacitor (в ней WebView) из корня экрана
        ViewGroup contentRoot = findViewById(android.R.id.content);
        View bridgeRoot = contentRoot.getChildAt(0);
        contentRoot.removeView(bridgeRoot);

        drawerLayout = new DrawerLayout(this);
        drawerLayout.setBackgroundColor(Color.BLACK);

        // ---- Основная колонка ----
        mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        mainColumn.setBackgroundColor(Color.BLACK);

        statusBarSpacer = new View(this);
        statusBarSpacer.setBackgroundColor(Color.BLACK);
        mainColumn.addView(statusBarSpacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(COLOR_BRAND);
        toolbar.setElevation(4 * d);

        toolbarMenuButton = new TextView(this);
        toolbarMenuButton.setText("☰");
        toolbarMenuButton.setTextSize(24);
        toolbarMenuButton.setTextColor(Color.WHITE);
        toolbarMenuButton.setGravity(Gravity.CENTER);
        toolbarMenuButton.setVisibility(View.INVISIBLE);
        toolbarMenuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        toolbar.addView(toolbarMenuButton, new LinearLayout.LayoutParams((int) (56 * d), (int) (56 * d)));

        toolbarTitle = new TextView(this);
        toolbarTitle.setText("Супро");
        toolbarTitle.setTextSize(18);
        toolbarTitle.setTypeface(Typeface.DEFAULT_BOLD);
        toolbarTitle.setTextColor(Color.WHITE);
        toolbarTitle.setSingleLine(true);
        toolbarTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginEnd((int) (16 * d));
        toolbar.addView(toolbarTitle, titleParams);

        mainColumn.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (56 * d)));

        mainColumn.addView(bridgeRoot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        drawerLayout.addView(mainColumn, new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- Боковое меню ----
        drawerPanel = new LinearLayout(this);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setBackgroundColor(COLOR_DRAWER_BG);
        drawerPanel.setClickable(true); // не пропускаем нажатия к странице под меню

        // Шапка меню: аватар и логин
        LinearLayout drawerHeader = new LinearLayout(this);
        drawerHeader.setOrientation(LinearLayout.VERTICAL);
        drawerHeader.setBackgroundColor(COLOR_BRAND);
        int headerPad = (int) (16 * d);
        drawerHeader.setPadding(headerPad, headerPad, headerPad, headerPad);

        drawerAvatar = new ImageView(this);
        drawerHeader.addView(drawerAvatar, new LinearLayout.LayoutParams((int) (56 * d), (int) (56 * d)));

        drawerUserName = new TextView(this);
        drawerUserName.setTextSize(16);
        drawerUserName.setTypeface(Typeface.DEFAULT_BOLD);
        drawerUserName.setTextColor(Color.WHITE);
        drawerUserName.setPadding(0, (int) (8 * d), 0, 0);
        drawerHeader.addView(drawerUserName);

        drawerPanel.addView(drawerHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Разделы сайта (заполняются со страницы)
        ScrollView drawerScroll = new ScrollView(this);
        drawerItemsContainer = new LinearLayout(this);
        drawerItemsContainer.setOrientation(LinearLayout.VERTICAL);
        drawerScroll.addView(drawerItemsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        drawerPanel.addView(drawerScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Нижние пункты меню
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DRAWER_SELECTED);
        drawerPanel.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) d)));

        drawerPanel.addView(createDrawerRow("Настройка профиля", null, v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            getBridge().getWebView().loadUrl(SITE_URL + "/profile/edit/");
        }));
        drawerPanel.addView(createDrawerRow("Выйти из аккаунта", null, v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            confirmLogout();
        }));

        DrawerLayout.LayoutParams panelParams = new DrawerLayout.LayoutParams(
                (int) (300 * d), ViewGroup.LayoutParams.MATCH_PARENT);
        panelParams.gravity = GravityCompat.START;
        drawerLayout.addView(drawerPanel, panelParams);

        // Пока меню неизвестно — оно закрыто и не открывается
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        contentRoot.addView(drawerLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Отступы под статус-бар, панель навигации и клавиатуру
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;

            ViewGroup.LayoutParams lp = statusBarSpacer.getLayoutParams();
            if (lp.height != bars.top) {
                lp.height = bars.top;
                statusBarSpacer.setLayoutParams(lp);
            }
            mainColumn.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime));
            drawerPanel.setPadding(0, bars.top, 0, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(drawerLayout);

        // Меню из прошлого запуска — чтобы ☰ была доступна сразу
        restoreCachedMenu();
    }

    private void setMenuAvailable(boolean available) {
        toolbarMenuButton.setVisibility(available ? View.VISIBLE : View.INVISIBLE);
        drawerLayout.setDrawerLockMode(available
                ? DrawerLayout.LOCK_MODE_UNLOCKED
                : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    private String menuKey(JSONObject info, JSONArray items) {
        return items.toString() + "|" + info.optString("user") + "|" + info.optString("avatar");
    }

    private void restoreCachedMenu() {
        try {
            String cached = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_DRAWER_MENU, null);
            if (cached == null) return;
            JSONObject info = new JSONObject(cached);
            JSONArray items = info.optJSONArray("items");
            if (items == null || items.length() == 0) return;
            lastMenuKey = menuKey(info, items);
            buildDrawerMenu(info, items);
            setMenuAvailable(true);
        } catch (Exception ignored) {}
    }

    /** Применяет данные, прочитанные со страницы: заголовок, меню, подсветку текущего раздела */
    private void applyPageInfo(String json, String currentUrl, boolean fullUpdate, boolean allowRetry) {
        if (json == null || "null".equals(json) || toolbarTitle == null) return;
        try {
            JSONObject info = new JSONObject(json);

            String title = info.optString("title", "").trim();
            if (!title.isEmpty()) {
                toolbarTitle.setText(title);
            } else if (fullUpdate) {
                toolbarTitle.setText("Супро");
            }

            if (fullUpdate) {
                JSONArray items = info.optJSONArray("items");
                int count = items != null ? items.length() : 0;
                boolean isAuthPage = currentUrl != null && currentUrl.contains("/auth/");

                if (count > 0) {
                    // Меню найдено: обновляем, если изменилось, и запоминаем
                    String key = menuKey(info, items);
                    if (!key.equals(lastMenuKey)) {
                        lastMenuKey = key;
                        buildDrawerMenu(info, items);
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                .putString(PREF_DRAWER_MENU, json).apply();
                    }
                    setMenuAvailable(true);
                } else if (isAuthPage) {
                    // Страница входа/выхода: меню недоступно, запомненное меню забываем
                    lastMenuKey = null;
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .remove(PREF_DRAWER_MENU).apply();
                    setMenuAvailable(false);
                } else if (allowRetry) {
                    // Меню на странице не нашлось — возможно, сайт дорисовывает его позже
                    WebView wv = getBridge().getWebView();
                    wv.postDelayed(() -> wv.evaluateJavascript(READ_PAGE_INFO_JS,
                            value -> applyPageInfo(value, currentUrl, true, false)), 1500);
                }
                // Иначе оставляем меню, полученное с предыдущих страниц
            }

            highlightCurrentItem(currentUrl);
        } catch (Exception ignored) {}
    }

    private void buildDrawerMenu(JSONObject info, JSONArray items) {
        float d = getResources().getDisplayMetrics().density;

        drawerUserName.setText(info.optString("user", ""));
        String avatar = info.optString("avatar", "");
        if (!avatar.isEmpty()) {
            Glide.with(this).load(avatar).circleCrop().into(drawerAvatar);
        }

        drawerItemsContainer.removeAllViews();
        drawerItemRows.clear();
        drawerItemHrefs.clear();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String text = item.optString("text", "");

            if ("title".equals(item.optString("type"))) {
                TextView section = new TextView(this);
                section.setText(text);
                section.setTextSize(13);
                section.setTextColor(COLOR_DRAWER_SECTION);
                section.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (4 * d));
                drawerItemsContainer.addView(section);
            } else {
                final String href = item.optString("href", "");
                View row = createDrawerRow(text, item.optString("icon", ""), v -> {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    if (!href.isEmpty()) {
                        getBridge().getWebView().loadUrl(href);
                    }
                });
                drawerItemsContainer.addView(row);
                drawerItemRows.add(row);
                drawerItemHrefs.add(href);
            }
        }
    }

    private View createDrawerRow(String text, String iconUrl, View.OnClickListener onClick) {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (12 * d));
        row.setClickable(true);
        row.setFocusable(true);

        // Эффект нажатия
        TypedValue ripple = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            row.setForeground(getDrawable(ripple.resourceId));
        }

        if (iconUrl != null && !iconUrl.isEmpty()) {
            ImageView icon = new ImageView(this);
            int size = (int) (24 * d);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
            iconParams.setMarginEnd((int) (16 * d));
            row.addView(icon, iconParams);
            Glide.with(this).load(iconUrl).into(icon);
        }

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTextColor(Color.WHITE);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(onClick);
        return row;
    }

    /** Подсвечивает в меню раздел, который сейчас открыт */
    private void highlightCurrentItem(String currentUrl) {
        int best = -1;
        int bestLen = 0;
        if (currentUrl != null) {
            for (int i = 0; i < drawerItemHrefs.size(); i++) {
                String href = drawerItemHrefs.get(i);
                if (href.isEmpty()) continue;
                String base = href.split("\\?")[0];
                boolean match = currentUrl.equals(href) || currentUrl.startsWith(base);
                if (match && base.length() > bestLen) {
                    best = i;
                    bestLen = base.length();
                }
            }
        }
        for (int i = 0; i < drawerItemRows.size(); i++) {
            drawerItemRows.get(i).setBackgroundColor(i == best ? COLOR_DRAWER_SELECTED : Color.TRANSPARENT);
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Выйти из аккаунта?")
                .setPositiveButton("Выйти", (dialogInterface, which) ->
                        getBridge().getWebView().loadUrl(SITE_URL + "/auth/logout/"))
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ===================== Экран ошибки загрузки =====================

    private void createErrorOverlay(ViewGroup parent) {
        float d = getResources().getDisplayMetrics().density;

        errorOverlay = new FrameLayout(this);
        errorOverlay.setBackgroundColor(Color.parseColor("#f6f6f6"));
        errorOverlay.setClickable(true); // не пропускаем нажатия к странице под ним
        errorOverlay.setVisibility(View.GONE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int sidePad = (int) (32 * d);
        box.setPadding(sidePad, 0, sidePad, 0);

        errorTitle = new TextView(this);
        errorTitle.setTextSize(20);
        errorTitle.setTypeface(Typeface.DEFAULT_BOLD);
        errorTitle.setTextColor(Color.parseColor("#222222"));
        errorTitle.setGravity(Gravity.CENTER);

        errorText = new TextView(this);
        errorText.setTextSize(15);
        errorText.setTextColor(Color.parseColor("#666666"));
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, (int) (12 * d), 0, (int) (24 * d));

        errorRetryButton = new Button(this);
        errorRetryButton.setAllCaps(false);
        errorRetryButton.setTextSize(16);
        errorRetryButton.setTextColor(Color.WHITE);
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(COLOR_BRAND);
        buttonBg.setCornerRadius(12 * d);
        errorRetryButton.setBackground(buttonBg);
        errorRetryButton.setPadding((int) (32 * d), (int) (12 * d), (int) (32 * d), (int) (12 * d));
        errorRetryButton.setOnClickListener(v -> retryLoad());

        box.addView(errorTitle);
        box.addView(errorText);
        box.addView(errorRetryButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxParams.gravity = Gravity.CENTER;
        errorOverlay.addView(box, boxParams);

        parent.addView(errorOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showErrorOverlay() {
        if (errorOverlay == null) return;
        if (isOnline()) {
            errorTitle.setText("Сервис временно недоступен");
            errorText.setText("Не удалось загрузить данные. Попробуйте ещё раз чуть позже.");
        } else {
            errorTitle.setText("Нет подключения к интернету");
            errorText.setText("Проверьте подключение. Страница загрузится автоматически, как только появится сеть.");
        }
        errorRetryButton.setEnabled(true);
        errorRetryButton.setText("Повторить");
        errorOverlay.setVisibility(View.VISIBLE);
    }

    private void hideErrorOverlay() {
        if (errorOverlay != null) {
            errorOverlay.setVisibility(View.GONE);
        }
    }

    private void retryLoad() {
        if (failedUrl == null) return;
        errorRetryButton.setEnabled(false);
        errorRetryButton.setText("Загрузка…");
        getBridge().getWebView().loadUrl(failedUrl);
    }

    private boolean isOnline() {
        try {
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = connectivity.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true; // если не смогли определить — считаем, что сеть есть
        }
    }

    // ===================== Скачивание файлов =====================

    /**
     * Скачивает файл: читает Content-Disposition из ответа сервера, берёт оттуда
     * настоящее имя, сохраняет в общую папку "Загрузки" и показывает уведомления.
     */
    private void downloadFile(String url, String userAgent) {
        final int notificationId = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        final android.app.NotificationManager nm =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final String AUTH_ERROR = "AUTH_PAGE";

        // Старый "тихий" канал больше не нужен
        nm.deleteNotificationChannel("downloads");

        // Канал с высокой важностью: значок в статус-баре, звук и всплывающее уведомление
        android.app.NotificationChannel channel = new android.app.NotificationChannel(
                "downloads_v2", "Загрузки", android.app.NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(channel);

        // Мгновенный отклик на нажатие
        android.widget.Toast.makeText(this, "Загрузка файла…", android.widget.Toast.LENGTH_SHORT).show();

        // Уведомление о начале загрузки
        nm.notify(notificationId,
                new androidx.core.app.NotificationCompat.Builder(this, "downloads_v2")
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("Загрузка файла")
                        .setProgress(0, 0, true)
                        .setOngoing(true)
                        .setSilent(true)
                        .build());

        // User-Agent и адрес страницы основного окна — именно с ними создавалась сессия
        final String mainUserAgent = getBridge().getWebView().getSettings().getUserAgentString();
        final String referer = getBridge().getWebView().getUrl();

        new Thread(() -> {
            java.io.InputStream in = null;
            java.io.OutputStream out = null;
            java.net.HttpURLConnection conn = null;
            try {
                CookieManager cookieStore = CookieManager.getInstance();
                String currentUrl = url;

                // Перенаправления обрабатываем сами, чтобы не терять куки, выставленные по дороге
                for (int hop = 0; ; hop++) {
                    conn = (java.net.HttpURLConnection) new java.net.URL(currentUrl).openConnection();
                    conn.setInstanceFollowRedirects(false);

                    String cookies = cookieStore.getCookie(currentUrl);
                    if (cookies != null) {
                        conn.setRequestProperty("Cookie", cookies);
                    }
                    if (mainUserAgent != null) {
                        conn.setRequestProperty("User-Agent", mainUserAgent);
                    }
                    if (referer != null) {
                        conn.setRequestProperty("Referer", referer);
                    }
                    conn.connect();

                    java.util.Map<String, java.util.List<String>> headers = conn.getHeaderFields();
                    for (java.util.Map.Entry<String, java.util.List<String>> header : headers.entrySet()) {
                        if (header.getKey() != null && header.getKey().equalsIgnoreCase("Set-Cookie")) {
                            for (String cookie : header.getValue()) {
                                cookieStore.setCookie(currentUrl, cookie);
                            }
                        }
                    }

                    int code = conn.getResponseCode();
                    String location = conn.getHeaderField("Location");
                    if (code >= 300 && code < 400 && location != null && hop < 10) {
                        currentUrl = new java.net.URL(new java.net.URL(currentUrl), location).toString();
                        conn.disconnect();
                        continue;
                    }
                    break;
                }
                cookieStore.flush();

                String disposition = conn.getHeaderField("Content-Disposition");
                String contentType = conn.getContentType();

                // Вместо файла пришла HTML-страница (например, страница входа) — мусор не сохраняем
                if (disposition == null && contentType != null && contentType.startsWith("text/html")) {
                    throw new java.io.IOException(AUTH_ERROR);
                }

                String fileName = null;
                if (disposition != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("filename\\*?=(?:UTF-8''|\")?([^\";]+)")
                            .matcher(disposition);
                    if (m.find()) {
                        fileName = android.net.Uri.decode(m.group(1).trim());
                    }
                }
                if (fileName == null || fileName.isEmpty()) {
                    fileName = android.webkit.URLUtil.guessFileName(currentUrl, disposition, contentType);
                }

                final String finalName = fileName;
                final String mime = contentType != null
                        ? contentType.split(";")[0]
                        : "application/octet-stream";

                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, finalName);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mime);
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);

                android.net.Uri collection = android.provider.MediaStore.Downloads
                        .getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
                final android.net.Uri itemUri = getContentResolver().insert(collection, values);

                in = conn.getInputStream();
                out = getContentResolver().openOutputStream(itemUri);

                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();

                values.clear();
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(itemUri, values, null, null);

                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(itemUri, mime);
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                        MainActivity.this, notificationId, openIntent,
                        android.app.PendingIntent.FLAG_IMMUTABLE);

                nm.notify(notificationId,
                        new androidx.core.app.NotificationCompat.Builder(MainActivity.this, "downloads_v2")
                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                .setContentTitle("Файл загружен")
                                .setContentText(finalName)
                                .setContentIntent(pi)
                                .setAutoCancel(true)
                                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                                .build());

            } catch (Exception e) {
                android.util.Log.e("SUPRO_DL", "Ошибка скачивания", e);
                nm.cancel(notificationId);
                final String message = AUTH_ERROR.equals(e.getMessage())
                        ? "Не удалось скачать файл: сессия устарела. Выйдите и войдите в аккаунт заново."
                        : "Не удалось скачать файл";
                runOnUiThread(() -> android.widget.Toast.makeText(
                        MainActivity.this, message, android.widget.Toast.LENGTH_LONG).show());
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
