# 保证 Main.main 方法不被混淆
-keepclassmembers class yangfentuozi.batteryrecorder.server.Main {
    public static void main(java.lang.String[]);
}

# 保留 server 包下所有类及其成员
-keep class yangfentuozi.batteryrecorder.server.** { *; }

# 忽略 hidden api 不存在
-dontwarn android.**
