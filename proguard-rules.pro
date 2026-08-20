# =============================================================================
# NOT Toolbox — ProGuard 规则
# 启用优化（optimize），关闭混淆（obfuscate）。
# =============================================================================

# 关闭混淆：skiko/skia 原生 JNI 依赖类名绑定，混淆会导致原生崩溃
-dontobfuscate

# 保留应用入口类（Main-Class 引用，不能混淆/优化掉）
-keep public class main.kotlin.MainAppKt {
    public static void main(java.lang.String[]);
}
# 保留所有 @Composable 组合函数：Compose 编译器按名称引用它们，
# 混淆会破坏生成的组合代码，导致 UI 崩溃。
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# 保留 Compose 编译器生成的单例类（ComposableSingletons$X）
-keep class **ComposableSingletons** { *; }

# 保留 @Serializable 数据类（kotlinx.serialization 依赖类名与字段）
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留序列化生成的 Companion（通过反射查找 serializer()）
-keepclassmembers class * {
    static *** Companion;
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
# 基于 ServiceLoader / 反射的网络与日志库（ktor、slf4j），
# 防止 ProGuard 移除 META-INF/services 中引用的实现类或反射入口。
# ---------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-keep class org.slf4j.** { *; }

# 保留 META-INF/services 目录条目（ServiceLoader 用）
-keepdirectories **/META-INF/services/**

# ---------------------------------------------------------------------------
# Compose UI / 运行时 — 保留全部 Compose 类，确保界面正常（避免 R8/压缩器
# 改动 Compose 导致字节码/运行时问题）。
# ---------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }

-keep @androidx.compose.runtime.Composable class * { *; }
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }
-keep @androidx.compose.runtime.ComposableTarget class * { *; }

# 保留 Compose 内部使用的 Kotlin 反射
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }

# 保留 Compose 运行时需要的内部类
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# 保留必要属性（Kotlin 元数据 / 注解 / 泛型签名 / 行号）
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ---------------------------------------------------------------------------
# 忽略缺失类 / module-info 等无害告警
# ---------------------------------------------------------------------------
-dontwarn **module-info**
-dontwarn sun.misc.**
-dontwarn java.awt.**
-dontwarn javax.management.**
-dontwarn com.sun.management.**
-dontwarn org.jetbrains.skia.**
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn android.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
# 抑制 kotlin.* 中引用方（如 ExperimentalStdlibApi 等注解）对缺失类的告警
-dontwarn kotlin.**
# JBR（可选 JetBrains Runtime）与 JNA 对 MethodHandle.invokeExact 的告警
-dontwarn com.jetbrains.**
-dontwarn com.sun.jna.**

# 保留 Kotlin RequiresOptIn（含其 Level 枚举），供注解引用
-keep class kotlin.RequiresOptIn** { *; }

# 保留 kotlinx.coroutines 全部类，避免 ProGuard 压缩改变其接口层级，
# 否则会破坏 JobSupport.cancel() 等对接口默认方法的 invokespecial（VerifyError）
-keep class kotlinx.coroutines.** { *; }
