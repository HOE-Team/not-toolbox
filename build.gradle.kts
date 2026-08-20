import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.11.1"
}

// 专用于 R8（构建期）的依赖配置，不会打进应用运行时 classpath
val r8Configuration = configurations.create("r8")

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // R8 代码压缩器（仅构建期使用）
    r8Configuration("com.android.tools:r8:8.13.19")

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
// R8：只压缩（shrink），不混淆（不混淆 = 不重命名类/方法/字段）
// 通过 --classfile 输出 Java 字节码（而非 Android DEX），
// 因此适用于 JVM / Compose Desktop 应用。
// 用法：./gradlew shrinkJar
// ===========================================================================

// R8 的 ProGuard 规则文件（含 -dontobfuscate 及必要 keep 规则）
val r8RulesFile = file("r8-rules.pro")

// R8 输出的压缩后 class 目录
val shrinkClassesDir = layout.buildDirectory.dir("r8/classes")

// 原始 fat JAR（由 fatJar 生成，含全部依赖与资源）
val fatJarFile = layout.buildDirectory.file("libs/${project.name}-all.jar")

// 1) 调用 R8 压缩 class（只压缩、不混淆）
tasks.register<JavaExec>("shrinkClasses") {
    group = "distribution"
    description = "Run R8 to shrink fat JAR class files (minify only, no obfuscation)"
    dependsOn("fatJar")

    // 使用上面定义的 r8 依赖配置（不污染应用运行时 classpath）
    classpath = r8Configuration
    mainClass.set("com.android.tools.r8.R8")
    workingDir(projectDir)

    inputs.file(fatJarFile)
    inputs.file(r8RulesFile)
    outputs.dir(shrinkClassesDir)

    doFirst {
        shrinkClassesDir.get().asFile.mkdirs()
    }

    args(
        "--classfile",
        "--output", shrinkClassesDir.get().asFile.absolutePath,
        "--lib", System.getProperty("java.home"),
        "--pg-conf", r8RulesFile.absolutePath,
        fatJarFile.get().asFile.absolutePath
    )
}

// 2) 把压缩后的 class 打包成 JAR，并把原始 fat JAR 中的资源（img/、packages/、
//    原生库等）合并回来 —— R8 的 --classfile 输出不会自动携带资源文件。
tasks.register<Jar>("shrinkJar") {
    group = "distribution"
    description = "Create shrunk fat JAR via R8 (minify only, no obfuscation)"
    dependsOn("shrinkClasses")

    archiveClassifier.set("shrunk")

    // R8 输出的压缩后 class
    from(shrinkClassesDir)
    // 原始 fat JAR 中的非 class 资源
    from(zipTree(fatJarFile)) {
        exclude("**/*.class")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "main.kotlin.MainAppKt"
    }
}
