import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import java.io.File

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.11.1"
}

// 专用于 ProGuard（构建期）的依赖配置，不会打进应用运行时 classpath
val proguardConfiguration = configurations.create("proguard")

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

// ===========================================================================
// 目标平台 build variant
//
// - windows：只带 Windows x64 + ARM64 的 Skiko 原生库
// - linux  ：只带 Linux x64 + ARM64 的 Skiko 原生库
// - all    ：同时带 Windows / Linux 两边的原生库，生成跨平台 JAR
//
// 用法：
//   ./gradlew fatJar -PntbPlatform=windows
//   ./gradlew fatJar -PntbPlatform=linux
//   ./gradlew fatJar -PntbPlatform=all
// ===========================================================================
val ntbPlatform = providers.gradleProperty("ntbPlatform")
    .orElse("all")
    .get()
    .lowercase()
require(ntbPlatform in setOf("windows", "linux", "all")) {
    "ntbPlatform 必须是 windows、linux 或 all，当前为：$ntbPlatform"
}

val skikoVersion = "0.144.6"
val skikoRuntimes = when (ntbPlatform) {
    "windows" -> listOf("windows-x64", "windows-arm64")
    "linux" -> listOf("linux-x64", "linux-arm64")
    else -> listOf("windows-x64", "windows-arm64", "linux-x64", "linux-arm64")
}

dependencies {
    // ProGuard 压缩器（仅构建期使用）
    proguardConfiguration("com.guardsquare:proguard-base:7.9.1")

    implementation("org.jetbrains.compose.desktop:desktop:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")
    implementation("org.jetbrains.compose.ui:ui-tooling:1.11.1")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
    implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
    implementation("org.jetbrains.compose.animation:animation:1.11.1")
    // Compose Multiplatform 1.11.1 对应的 skiko 版本（0.144.x）。
    // 注意：不能显式固定为旧版（如 0.8.x），否则与 Compose/Skiko 原生运行时不匹配。
    implementation("org.jetbrains.skiko:skiko-awt:$skikoVersion")
    // Skiko 原生运行时库：按 -PntbPlatform 选择，避免每个平台包都塞入全部原生库
    skikoRuntimes.forEach { runtime ->
        implementation("org.jetbrains.skiko:skiko-awt-runtime-$runtime:$skikoVersion")
    }
    // Compose 资源库（生成 Res 类，替换弃用的 painterResource(String)）
    implementation("org.jetbrains.compose.components:components-resources:1.11.1")
    // Markdown 渲染（Compose Multiplatform 版：Android 端即 Markwon，桌面端用 JetBrains markdown 解析）
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.45.0")
    // 代码块语法高亮（可选模块，给 AI 回答里的代码块上色并带语言 + 复制按钮）
    implementation("com.mikepenz:multiplatform-markdown-renderer-code:0.45.0")
    implementation("com.github.oshi:oshi-core:7.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // Ktor 网络客户端
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-cio:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
}

kotlin {
    jvmToolchain(21)
}

// 平台构建的产物后缀：all 保持旧的 NTB-all.jar 名称
val targetClassifier = if (ntbPlatform == "all") "all" else ntbPlatform

// 打包为 fat JAR；实际包含的 Skiko 原生库由 -PntbPlatform 决定
tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Create a fat JAR for ntbPlatform=$ntbPlatform (dependencies + Skiko for that platform)"
    archiveClassifier.set(targetClassifier)

    inputs.property("ntbPlatform", ntbPlatform)

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    
    manifest {
        attributes["Main-Class"] = "main.kotlin.MainAppKt"
        attributes["Ntb-Platform"] = ntbPlatform
    }
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

// 确保 fatJar 包含 resources 目录中的文件
tasks.named("fatJar") {
    dependsOn("processResources")
}

// 同时保留 nativeDistributions 用于生成各平台原生安装包
compose.desktop {
    application {
        mainClass = "main.kotlin.MainAppKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "NOT Toolbox"
            packageVersion = "1.0.0"
            
            linux {
                iconFile.set(project.file("images/logo.png"))
            }
            windows {
                iconFile.set(project.file("images/logo.png"))
            }
            macOS {
                iconFile.set(project.file("images/logo.png"))
            }
            
            appResourcesRootDir.set(project.layout.projectDirectory.dir("images"))
        }
    }
}

// 为当前操作系统打包
tasks.register("packageApp") {
    group = "distribution"
    description = "Build native distribution for current OS"
    dependsOn("packageDistributionForCurrentOS")
}

// ===========================================================================
// ProGuard：只压缩（shrink），不混淆（obfuscate）、不优化（optimize）。
// 相比 R8，ProGuard 在仅压缩（-dontoptimize -dontobfuscate）时不会改写
// Compose 接口默认方法，可避免 R8 生成的 access$...$jd 非法字节码
// （VerifyError: Bad invokespecial，见 JetBrains CMP-8339 / CMP-10256）。
// 用法：./gradlew shrinkJar
// ===========================================================================

// ProGuard 规则文件（含 -dontobfuscate、-dontoptimize 及必要 keep 规则）
val proguardRulesFile = file("proguard-rules.pro")

// 原始 fat JAR（由 fatJar 生成，含该平台所需依赖与 Skiko 原生库）
val fatJarFile = layout.buildDirectory.file("libs/" + project.name + "-" + targetClassifier + ".jar")

// ProGuard 输出的压缩 JAR；all 继续使用旧的 NTB-shrunk.jar 名称
val shrunkJarName = if (ntbPlatform == "all") {
    project.name + "-shrunk.jar"
} else {
    project.name + "-" + ntbPlatform + "-shrunk.jar"
}
val proguardJarFile = layout.buildDirectory.file("libs/" + shrunkJarName)

// 调用 ProGuard 压缩 fat JAR（只压缩、不混淆、不优化）
tasks.register<JavaExec>("shrinkJar") {
    group = "distribution"
    description = "Create shrunk fat JAR via ProGuard (shrink only, no obfuscation)"
    dependsOn("fatJar")

    // 使用上面定义的 proguard 依赖配置（不污染应用运行时 classpath）
    classpath = proguardConfiguration
    mainClass.set("proguard.ProGuard")
    workingDir(projectDir)

    inputs.file(fatJarFile)
    inputs.file(proguardRulesFile)
    outputs.file(proguardJarFile)

    doFirst {
        // 动态生成 ProGuard 配置：把 fat JAR 作为 injars，JDK 各 jmod 作为 libraryjars。
        // Gradle daemon 可能运行在 JRE（无 jmods），因此解析工具链 JDK（JDK 21）。
        // 优先使用带 jmods 的 Microsoft JDK；ARM64 Linux 上没有该 vendor 时
        // 回退到任意已安装的 JDK 21（CI 的 setup-java 会提供完整 JDK）。
        val jdkHome = runCatching {
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.MICROSOFT)
            }.get().metadata.installationPath.asFile
        }.getOrElse {
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            }.get().metadata.installationPath.asFile
        }
        val cfg = layout.buildDirectory.file("proguard/config.pro").get().asFile
        cfg.parentFile.mkdirs()
        val sb = StringBuilder()
        sb.appendLine("-injars '${fatJarFile.get().asFile.absolutePath}'")
        sb.appendLine("-outjars '${proguardJarFile.get().asFile.absolutePath}'")
        val jmodsDir = File(jdkHome, "jmods")
        if (jmodsDir.isDirectory) {
            jmodsDir.listFiles { f -> f.extension == "jmod" }
                ?.sortedBy { it.name }
                ?.forEach { sb.appendLine("-libraryjars '${it.absolutePath}'") }
        }
        sb.appendLine("-include '${proguardRulesFile.absolutePath}'")
        cfg.writeText(sb.toString())
        args("@${cfg.absolutePath}")
    }
}
