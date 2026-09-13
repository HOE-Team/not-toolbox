// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils.ai

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * API Key 的本地保护。
 *
 * 桌面端没有 Android KeyStore，因此按可用性自动降级：
 * 1. Windows：DPAPI（CryptProtectData，绑定当前 Windows 用户；JNA 已随 OSHI 引入，无需新增依赖）
 * 2. Linux：系统密钥环（libsecret 的 secret-tool）
 * 3. 兜底：本机 AES-256-GCM + PBKDF2（salt 存 config/.ai_key，权限 600）
 *
 * 存储格式始终带前缀，便于识别来源：dpapi: / keyring: / aesgcm:
 */
object SecretStore {
    private const val PREFIX_DPAPI = "dpapi:"
    private const val PREFIX_KEYRING = "keyring:"
    private const val PREFIX_AES = "aesgcm:"
    private const val KEYRING_SERVICE = "NOT-Toolbox-AI"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000

    private val isWindows: Boolean by lazy { osName().contains("windows") }
    private val isLinux: Boolean by lazy { osName().contains("linux") }
    private val saltPath: Path = Path.of("config", ".ai_key")

    private fun osName(): String = System.getProperty("os.name", "").lowercase()

    /** 供界面展示的当前保护级别说明 */
    fun protectionDescription(): String {
        if (isWindows) return "Windows DPAPI（当前用户）"
        if (isLinux && findExecutable("secret-tool") != null) return "系统密钥环（libsecret）"
        return "本机 AES-256-GCM（未接入系统密钥环）"
    }

    /** 加密；空串原样返回 */
    fun protect(plain: String): String {
        if (plain.isEmpty()) return ""
        if (isWindows) {
            try {
                val enc = Crypt32Util.cryptProtectData(plain.toByteArray(StandardCharsets.UTF_8))
                return PREFIX_DPAPI + Base64.getEncoder().encodeToString(enc)
            } catch (_: Throwable) {
                // 落到 AES 兜底
            }
        }
        if (isLinux) {
            val toolAvailable = findExecutable("secret-tool") != null
            if (toolAvailable) {
                val entryId = "model-" + System.nanoTime().toString(16)
                if (storeInKeyring(entryId, plain)) return PREFIX_KEYRING + entryId
            }
        }
        return protectWithAes(plain)
    }

    /** 解密；失败或格式不匹配返回空串（界面据此提示重新输入） */
    fun unprotect(stored: String): String {
        if (stored.isEmpty()) return ""
        return try {
            when {
                stored.startsWith(PREFIX_DPAPI) -> {
                    val data = Base64.getDecoder().decode(stored.removePrefix(PREFIX_DPAPI))
                    String(Crypt32Util.cryptUnprotectData(data), StandardCharsets.UTF_8)
                }
                stored.startsWith(PREFIX_KEYRING) -> lookupInKeyring(stored.removePrefix(PREFIX_KEYRING)) ?: ""
                stored.startsWith(PREFIX_AES) -> unprotectWithAes(stored.removePrefix(PREFIX_AES))
                else -> stored // 兼容历史遗留的明文
            }
        } catch (_: Throwable) {
            ""
        }
    }

    /** 删除模型时清理密钥环条目 */
    fun forget(stored: String) {
        if (!stored.startsWith(PREFIX_KEYRING)) return
        val tool = findExecutable("secret-tool") ?: return
        try {
            ProcessBuilder(tool, "clear", "service", KEYRING_SERVICE, "account", stored.removePrefix(PREFIX_KEYRING))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor(10, TimeUnit.SECONDS)
        } catch (_: Throwable) {
        }
    }

    // ---- 兜底：本机 AES-256-GCM ----

    private fun loadOrCreateSalt(): ByteArray {
        return try {
            if (Files.exists(saltPath)) return Files.readAllBytes(saltPath)
            val salt = ByteArray(32)
            SecureRandom().nextBytes(salt)
            saltPath.parent?.let { Files.createDirectories(it) }
            Files.write(saltPath, salt)
            try {
                val f = saltPath.toFile()
                f.setReadable(false, false)
                f.setReadable(true, true)
                f.setWritable(false, false)
                f.setWritable(true, true)
            } catch (_: Throwable) {
            }
            salt
        } catch (_: Throwable) {
            ByteArray(32) { 7 }
        }
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val user = System.getProperty("user.name", "") + "|not-toolbox-ai"
        val spec = PBEKeySpec(user.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun protectWithAes(plain: String): String {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(loadOrCreateSalt()), GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return PREFIX_AES + Base64.getEncoder().encodeToString(iv + cipherText)
    }

    private fun unprotectWithAes(payload: String): String {
        val all = Base64.getDecoder().decode(payload)
        if (all.size <= GCM_IV_LENGTH) return ""
        val iv = all.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = all.copyOfRange(GCM_IV_LENGTH, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(loadOrCreateSalt()), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }

    // ---- Linux 密钥环 ----

    private fun findExecutable(name: String): String? {
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparatorChar)) {
            if (dir.isBlank()) continue
            val f = File(dir, name)
            if (f.isFile && f.canExecute()) return f.absolutePath
        }
        return null
    }

    private fun storeInKeyring(entryId: String, secret: String): Boolean {
        val tool = findExecutable("secret-tool") ?: return false
        return try {
            val p = ProcessBuilder(
                tool, "store", "--label=NOT Toolbox AI",
                "service", KEYRING_SERVICE, "account", entryId
            ).start()
            p.outputStream.use { it.write(secret.toByteArray(StandardCharsets.UTF_8)) }
            p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun lookupInKeyring(entryId: String): String? {
        val tool = findExecutable("secret-tool") ?: return null
        return try {
            val p = ProcessBuilder(tool, "lookup", "service", KEYRING_SERVICE, "account", entryId)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) out.trim() else null
        } catch (_: Throwable) {
            null
        }
    }
}