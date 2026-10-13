# 百度地图 SDK 自带 consumer proguard 规则，此处仅补充少量保守规则。
# MVP 阶段 release 未开启混淆，本文件作为后续开启混淆时的预留。

-keep class com.baidu.** { *; }
-keep class vi.com.gdi.bgl.android.** { *; }
-dontwarn com.baidu.**
