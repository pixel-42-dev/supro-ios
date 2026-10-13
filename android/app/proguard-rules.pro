# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Capacitor: плагины и их методы вызываются через рефлексию
-keep @com.getcapacitor.annotation.CapacitorPlugin public class * {
    @com.getcapacitor.annotation.PermissionCallback <methods>;
    @com.getcapacitor.annotation.ActivityCallback <methods>;
    @com.getcapacitor.annotation.Permission <methods>;
    @com.getcapacitor.PluginMethod public <methods>;
}
-keep public class * extends com.getcapacitor.Plugin { *; }

# Cordova-совместимость Capacitor
-keep public class * extends org.apache.cordova.* {
    public <methods>;
    public <fields>;
}

# Сохраняем номера строк, чтобы отчёты о сбоях в Play Console были читаемыми
-keepattributes SourceFile,LineNumberTable

# Скрываем настоящие имена исходных файлов в отчётах о сбоях
-renamesourcefileattribute SourceFile
