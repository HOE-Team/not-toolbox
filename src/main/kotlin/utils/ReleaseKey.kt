// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

/**
 * 发布签名公钥信息。
 *
 * 公钥随程序分发（资源路径 [PUBLIC_KEY_RESOURCE]），用于用户手动验证
 * binary_sha256.sha256.asc；程序本身不做运行期 GPG 验证。
 *
 * 公钥指纹必须从包外渠道（官网、Release 页面、Git 仓库 README）核对，
 * 因为包内公钥和签名可以被一起替换。
 */
object ReleaseKey {
    const val FINGERPRINT = "4900 8F79 0CDB B34E C1D2 6A82 CE50 A883 DE74 5329"
    const val FINGERPRINT_COMPACT = "49008F790CDBB34EC1D26A82CE50A883DE745329"
    const val PUBLIC_KEY_RESOURCE = "gpg/GoldenHoe_pub.asc"
    const val PUBLIC_KEY_PACKAGE_PATH = "/res/GoldenHoe_pub.asc"
    const val PUBLIC_KEY_URL =
        "https://github.com/GoldenHoe/GoldenHoe/blob/main/gpg-pubkey/GoldenHoe_pub.asc"
}
