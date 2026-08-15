# =============================================================================
# NOT Toolbox — R8 规则
# RULE: 只压缩（shrink），不混淆（obfuscate）。
# =============================================================================

-dontobfuscate

# 保留应用入口类（R8 不会自动读取 JAR 的 Main-Class 清单）
-keep public class main.kotlin.MainAppKt {
    public static void main(java.lang.String[]);
}

# ---------------------------------------------------------------------------
# kotlinx-serialization：保留生成的序列化器与注解，避免运行时序列化查找失败
# ---------------------------------------------------------------------------
-keep class kotlinx.serialization.** { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature

# 数据模型（序列化框架间接引用）
-keep class config.OfflineItem { *; }
-keep class config.OfflineListWrapper { *; }
-keep class utils.JsonPackageInfo { *; }

# ---------------------------------------------------------------------------
# Skiko / Skia 原生库
# ---------------------------------------------------------------------------
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }

# ---------------------------------------------------------------------------
# JNA / oshi：系统信息（SystemInfoProvider）依赖。
# ---------------------------------------------------------------------------
-keep class com.sun.jna.** { *; }
-keep class oshi.** { *; }

# ---------------------------------------------------------------------------
# 基于 ServiceLoader / 反射的网络与日志库（okhttp、ktor、slf4j），
# 防止 R8 移除 META-INF/services 中引用的实现类或反射入口。
# ---------------------------------------------------------------------------
-keep class okhttp3.** { *; }
-keep class io.ktor.** { *; }
-keep class org.slf4j.** { *; }

# 保留 META-INF/services 目录条目（ServiceLoader 用）
-keepdirectories **/META-INF/services/**

# ---------------------------------------------------------------------------
# Compose / Skiko 及 JVM 库依赖较多反射，宽泛忽略缺失类的告警，
# 防止因个别可选依赖缺失导致构建中断。
# ---------------------------------------------------------------------------
-dontwarn **module-info**
-dontwarn sun.misc.**
-dontwarn java.awt.**
-dontwarn javax.management.**
-dontwarn com.sun.management.**
-dontwarn org.jetbrains.skia.**
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
