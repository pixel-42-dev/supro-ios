package ru.inko.supro;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.Locale;

/**
 * Нативный экран настроек. Действия, которые нужно выполнить в основном окне
 * (открыть страницу сайта, выйти из аккаунта), возвращает в MainActivity.
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String EXTRA_ACTION = "settings_action";
    public static final String EXTRA_URL = "settings_url";
    public static final String ACTION_OPEN_URL = "open_url";
    public static final String ACTION_LOGOUT = "logout";

    private static final String SITE_URL = "https://xn--n1abdck.xn--p1ai";
    private static final String SUPPORT_EMAIL = "info@contractunion.ru";
    private static final String SUPPORT_PHONE = "+79859371301";
    private static final String SUPPORT_PHONE_DISPLAY = "+7 985 937 13 01";

    private static final int COLOR_BRAND = 0xFF5D5FEF;
    private static final int COLOR_BG = 0xFFF2F2F7;
    private static final int COLOR_TEXT = 0xFF1F2033;
    private static final int COLOR_SUBTEXT = 0xFF8A8A99;
    private static final int COLOR_DIVIDER = 0xFFE5E5EA;
    private static final int COLOR_DANGER = 0xFFE5484D;

    private float density;
    private TextView notificationsStatus;
    private TextView cacheSizeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;

        // Рисуем под системными панелями сами — как на главном экране
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        View statusBarSpacer = new View(this);
        statusBarSpacer.setBackgroundColor(Color.BLACK);
        root.addView(statusBarSpacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0));

        // ---- Верхняя панель ----
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(COLOR_BRAND);
        toolbar.setElevation(px(4));

        TextView backButton = new TextView(this);
        backButton.setText("←");
        backButton.setTextSize(24);
        backButton.setTextColor(Color.WHITE);
        backButton.setGravity(Gravity.CENTER);
        backButton.setOnClickListener(v -> finish());
        setRipple(backButton, android.R.attr.selectableItemBackgroundBorderless);
        toolbar.addView(backButton, new LinearLayout.LayoutParams(px(56), px(56)));

        TextView title = new TextView(this);
        title.setText("Настройки");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        toolbar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(56)));

        // ---- Содержимое ----
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(16), 0, px(16), px(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildContent(content);
        setContentView(root);

        // Отступы под статус-бар и панель навигации
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.LayoutParams lp = statusBarSpacer.getLayoutParams();
            if (lp.height != bars.top) {
                lp.height = bars.top;
                statusBarSpacer.setLayoutParams(lp);
            }
            root.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем при каждом возврате (например, из системных настроек уведомлений)
        updateNotificationsStatus();
        updateCacheSize();
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

    // ===================== Содержимое экрана =====================

    private void buildContent(LinearLayout content) {
        // Шапка: иконка, название, версия
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(0, px(24), 0, px(8));

        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName()));
        } catch (Exception ignored) {}
        header.addView(icon, new LinearLayout.LayoutParams(px(72), px(72)));

        TextView appName = new TextView(this);
        appName.setText("Супро");
        appName.setTextSize(20);
        appName.setTypeface(Typeface.DEFAULT_BOLD);
        appName.setTextColor(COLOR_TEXT);
        appName.setPadding(0, px(8), 0, 0);
        header.addView(appName);

        TextView version = new TextView(this);
        version.setText("Версия " + getVersionText());
        version.setTextSize(14);
        version.setTextColor(COLOR_SUBTEXT);
        header.addView(version);

        content.addView(header);

        // Приложение
        LinearLayout appCard = addSection(content, "Приложение");
        notificationsStatus = addRow(appCard, "Уведомления", "", v -> openNotificationSettings(), false);
        addDivider(appCard);
        cacheSizeText = addRow(appCard, "Очистить кэш", "Подсчёт…", v -> confirmClearCache(), false);

        // Поддержка
        LinearLayout supportCard = addSection(content, "Поддержка");
        addRow(supportCard, "Служба технической поддержки", null, v -> openSitePage("/support/view/"), false);
        addDivider(supportCard);
        addRow(supportCard, "Написать на почту", SUPPORT_EMAIL, v -> writeEmail(), false);
        addDivider(supportCard);
        addRow(supportCard, "Позвонить", SUPPORT_PHONE_DISPLAY, v -> callSupport(), false);

        // Документы
        LinearLayout docsCard = addSection(content, "Документы");
        addRow(docsCard, "Политика обработки персональных данных", null,
                v -> openSitePage("/users/confid/view/"), false);
        addDivider(docsCard);
        addRow(docsCard, "Лицензионное соглашение", null, v -> openSitePage("/index/license/"), false);

        // Аккаунт
        LinearLayout accountCard = addSection(content, null);
        addRow(accountCard, "Выйти из аккаунта", null, v -> confirmLogout(), true);
    }

    /** Заголовок раздела (если есть) и белая карточка под ним */
    private LinearLayout addSection(LinearLayout content, String sectionTitle) {
        if (sectionTitle != null) {
            TextView t = new TextView(this);
            t.setText(sectionTitle);
            t.setTextSize(13);
            t.setTextColor(COLOR_SUBTEXT);
            t.setPadding(px(16), px(24), px(16), px(8));
            content.addView(t);
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(px(12));
        card.setBackground(bg);
        card.setClipToOutline(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (sectionTitle == null) {
            lp.topMargin = px(24);
        }
        content.addView(card, lp);
        return card;
    }

    /** Строка настройки. Возвращает поле подзаголовка, чтобы его можно было обновлять */
    private TextView addRow(LinearLayout card, String rowTitle, String subtitle,
                            View.OnClickListener onClick, boolean danger) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(16), px(14), px(16), px(14));
        row.setClickable(true);
        row.setFocusable(true);
        setRipple(row, android.R.attr.selectableItemBackground);
        row.setOnClickListener(onClick);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(rowTitle);
        titleView.setTextSize(16);
        titleView.setTextColor(danger ? COLOR_DANGER : COLOR_TEXT);
        texts.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(COLOR_SUBTEXT);
        subtitleView.setPadding(0, px(2), 0, 0);
        if (subtitle == null) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
        }
        texts.addView(subtitleView);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!danger) {
            TextView chevron = new TextView(this);
            chevron.setText("›");
            chevron.setTextSize(22);
            chevron.setTextColor(COLOR_SUBTEXT);
            chevron.setPadding(px(8), 0, 0, 0);
            row.addView(chevron);
        }

        card.addView(row);
        return subtitleView;
    }

    private void addDivider(LinearLayout card) {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1) / 2));
        lp.setMarginStart(px(16));
        card.addView(divider, lp);
    }

    // ===================== Действия =====================

    private void updateNotificationsStatus() {
        if (notificationsStatus == null) return;
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        notificationsStatus.setText(enabled ? "Включены" : "Выключены");
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
        }
    }

    private void updateCacheSize() {
        if (cacheSizeText == null) return;
        new Thread(() -> {
            long size = dirSize(getCacheDir());
            runOnUiThread(() -> cacheSizeText.setText("Занято: " + formatSize(size)));
        }).start();
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить кэш?")
                .setMessage("Будут удалены временные файлы: сохранённые копии страниц и картинок. "
                        + "Вы останетесь в аккаунте, скачанные файлы не пострадают.")
                .setPositiveButton("Очистить", (dialog, which) -> clearCache())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void clearCache() {
        // Кэш страниц WebView общий для всего приложения
        WebView temp = new WebView(this);
        temp.clearCache(true);
        temp.destroy();

        // Кэш картинок (иконки меню, аватар, гифка загрузки)
        Glide.get(this).clearMemory();
        new Thread(() -> {
            Glide.get(getApplicationContext()).clearDiskCache();
            runOnUiThread(() -> {
                Toast.makeText(this, "Кэш очищен", Toast.LENGTH_SHORT).show();
                // WebView удаляет файлы не мгновенно — пересчитываем чуть позже
                cacheSizeText.postDelayed(this::updateCacheSize, 1000);
            });
        }).start();
    }

    private void openSitePage(String path) {
        Intent result = new Intent();
        result.putExtra(EXTRA_ACTION, ACTION_OPEN_URL);
        result.putExtra(EXTRA_URL, SITE_URL + path);
        setResult(RESULT_OK, result);
        finish();
    }

    private void writeEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + SUPPORT_EMAIL));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Обращение из приложения Супро " + getVersionText());
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Не найдено почтовое приложение", Toast.LENGTH_SHORT).show();
        }
    }

    private void callSupport() {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + SUPPORT_PHONE)));
        } catch (Exception ignored) {}
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Выйти из аккаунта?")
                .setPositiveButton("Выйти", (dialog, which) -> {
                    Intent result = new Intent();
                    result.putExtra(EXTRA_ACTION, ACTION_LOGOUT);
                    setResult(RESULT_OK, result);
                    finish();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ===================== Вспомогательное =====================

    private String getVersionText() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName + " (" + PackageInfoCompat.getLongVersionCode(info) + ")";
        } catch (Exception e) {
            return "";
        }
    }

    private long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        long size = 0;
        for (File f : files) {
            size += f.isDirectory() ? dirSize(f) : f.length();
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.0f КБ", bytes / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    private void setRipple(View view, int attr) {
        TypedValue ripple = new TypedValue();
        if (getTheme().resolveAttribute(attr, ripple, true)) {
            view.setForeground(getDrawable(ripple.resourceId));
        }
    }

    private int px(int dp) {
        return (int) (dp * density);
    }
}