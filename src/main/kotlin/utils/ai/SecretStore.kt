// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// API Key 的本地保护

package utils.ai

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * API Key 的本地保护。桌面端没有 Android KeyStore，因此按平台二选一：
 * 1. Windows：DPAPI（CryptProtectData，绑定当前 Windows 用户；JNA 已随 OSHI 引入，无需新增依赖）
 * 2. 其他（含 Linux）：本机 AES-256-GCM + PBKDF2（salt 存 config/.ai_key，权限 600）
 *
 * 存储格式始终带前缀，便于识别来源：dpapi: / aesgcm:
 *
 * 曾经支持过 libsecret（secret-tool）密钥环，但每次保存都会新建一条记录且从不清理，
 * 属于「修起来比删掉更亏」的负担，已移除；带 keyring: 前缀的历史值会被视为无法解密，
 * 界面会提示重新输入 API Key。
 */
object SecretStore {
    private const val PREFIX_DPAPI = "dpapi:"
    private const val PREFIX_AES = "aesgcm:"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000

    private val isWindows: Boolean by lazy { System.getProperty("os.name", "").lowercase().contains("windows") }
    private val saltPath: Path = Path.of("config", ".ai_key")

    /** 供界面展示的当前保护级别说明 */
    fun protectionDescription(): String =
        if (isWindows) "Windows DPAPI（当前用户）" else "本机 AES-256-GCM（config/.ai_key）"

    /** 加密；空串原样返回 */
    fun protect(plain: String): String {
        if (plain.isEmpty()) return ""
        if (isWindows) {
            try {
                val encrypted = Crypt32Util.cryptProtectData(plain.toByteArray(StandardCharsets.UTF_8))
                return PREFIX_DPAPI + Base64.getEncoder().encodeToString(encrypted)
            } catch (_: Throwable) {
                // 落到 AES 兜底
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
                stored.startsWith(PREFIX_AES) -> unprotectWithAes(stored.removePrefix(PREFIX_AES))
                else -> stored // 兼容历史遗留的明文
            }
        } catch (_: Throwable) {
            ""
        }
    }

    // ---- 本机 AES-256-GCM ----

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
                // 权限收紧失败不影响加解密本身
            }
            salt
        } catch (_: Throwable) {
            // 极端情况下（目录只读等）退化为固定 salt，仍比明文存储好
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
}
