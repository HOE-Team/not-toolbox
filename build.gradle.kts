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
    implementation("org.jetbrains.skiko:skiko-awt:0.144.6")
    // Skiko 原生运行时库（Windows/Linux），否则 fat JAR 缺少 skiko-*.dll/.so 会无法启动
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.144.6")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.144.6")
    // Compose 资源库（生成 Res 类，替换弃用的 painterResource(String)）
    implementation("org.jetbrains.compose.components:components-resources:1.11.1")
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

// 统一打包为 fat JAR，包含所有平台的 Skiko原生库
tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Create a unified fat JAR with all dependencies (cross-platform)"
    archiveClassifier.set("all")
    
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    
    manifest {
        attributes["Main-Class"] = "main.kotlin.MainAppKt"
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

// 原始 fat JAR（由 fatJar 生成，含全部依赖与资源）
val fatJarFile = layout.buildDirectory.file("libs/${project.name}-all.jar")

// ProGuard 输出的压缩 JAR
val proguardJarFile = layout.buildDirectory.file("libs/${project.name}-shrunk.jar")

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
        // 用 vendor=Microsoft 排除 Temurin JRE，确保解析到带 jmods 的完整 JDK。
        val jdkHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.MICROSOFT)
        }.get().metadata.installationPath.asFile
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
