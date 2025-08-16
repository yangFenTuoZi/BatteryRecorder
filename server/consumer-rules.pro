# 保证 ServerMain.main 方法不被混淆
-keepclassmembers class yangfentuozi.batteryrecorder.server.ServerMain {
    public static void main(java.lang.String[]);
}

# 保证 server 包不被删除（但可以被混淆）
-keep class yangfentuozi.batteryrecorder.server.**

# 忽略 hidden api 不存在
-dontwarn android.**
