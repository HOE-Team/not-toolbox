// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 发送给模型的「当前环境」摘要

package utils.ai

import utils.PackageManagerType
import utils.SystemInfoProvider
import utils.systemSnapshotFlow

/**
 * 环境摘要：只保留最基础的几条（操作系统、包管理器名），其余实时数据一律由工具按需获取。
 *
 * **这里绝对不能碰 [utils.PackageDetector]**：Windows 下首次检测要跑 `winget export`，
 * 实测可达 109 秒，而它曾经被放在「发消息」的关键路径上，于是首字延迟被拉长到 90 秒以上
 * （界面还显示"等待模型首字"，其实请求根本没发出去）。已安装数量、可升级列表都属于
 * 「按需数据」，由 list_installed / check_updates 等工具提供，用户在工具卡片上能看到进度。
 *
 * 任何情况下都不包含密钥、代理凭据、MAC 地址、背景图路径或终端内容。
 */
object EnvironmentSummary {
    suspend fun build(manager: PackageManagerType): String {
        return try {
            // 纯内存读取：首页已经在预热快照，这里不会触发新的采集
            val snap = systemSnapshotFlow.value ?: SystemInfoProvider.collectSnapshot(waitForInitialData = false)
            val ov = snap.overview
            val sb = StringBuilder()
            sb.append("- 操作系统：").append(ov.platform).append(' ').append(ov.osVersion)
            sb.append("（").append(ov.architecture).append("）\n")
            sb.append("- 当前包管理器：").append(manager).append('\n')
            sb.append("- 需要更详细的本机状态 / 已安装列表时，请调用 get_system_info、list_installed 等工具，不要凭记忆猜测。")
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }
}

