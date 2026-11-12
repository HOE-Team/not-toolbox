// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class BackgroundTaskStatus { RUNNING, SUCCESS, FAILED, CANCELLED }

data class BackgroundTask(
    val id: Long,
    val title: String,
    val actionLabel: String,
    val command: String,
    val sessionId: Long,
    val status: BackgroundTaskStatus = BackgroundTaskStatus.RUNNING,
    val output: String = "",
    val exitCode: Int? = null
)

/**
 * 把已经开始的包管理命令放到后台继续执行。
 *
 * 每个任务拥有独立的终端会话，因此可以同时存在多个后台安装/卸载/更新任务。
 */
object BackgroundTaskManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _tasks = MutableStateFlow<List<BackgroundTask>>(emptyList())
    val tasks: StateFlow<List<BackgroundTask>> = _tasks.asStateFlow()

    private val monitorJobs = ConcurrentHashMap<Long, Job>()
    private var nextId = 0L

    fun start(
        title: String,
        actionLabel: String,
        command: String,
        sessionId: Long,
        onSuccess: suspend () -> Unit
    ): Long {
        val id = ++nextId
        _tasks.update {
            it + BackgroundTask(
                id = id,
                title = title,
                actionLabel = actionLabel,
                command = command,
                sessionId = sessionId
            )
        }
        val job = scope.launch {
            monitor(id, sessionId, onSuccess)
        }
        monitorJobs[id] = job
        return id
    }

    private suspend fun monitor(id: Long, sessionId: Long, onSuccess: suspend () -> Unit) {
        val session = TerminalSessionManager.getSession(sessionId)
        if (session == null) {
            updateTask(id) { it.copy(status = BackgroundTaskStatus.FAILED, exitCode = -1) }
            monitorJobs.remove(id)
            return
        }

        val outputJob = scope.launch {
            session.output.collect { text ->
                updateTask(id) { it.copy(output = text) }
            }
        }

        try {
            if (session.isRunning.value) {
                session.isRunning.first { !it }
            }
            val cancelled = session.wasCancelled.value
            val exitCode = session.lastExitCode.value
            val success = exitCode == 0 && !cancelled
            updateTask(id) {
                it.copy(
                    status = when {
                        cancelled -> BackgroundTaskStatus.CANCELLED
                        success -> BackgroundTaskStatus.SUCCESS
                        else -> BackgroundTaskStatus.FAILED
                    },
                    exitCode = exitCode,
                    output = session.output.value
                )
            }
            if (success) {
                runCatching { onSuccess() }
            }
        } finally {
            outputJob.cancel()
            monitorJobs.remove(id)
        }
    }

    private fun updateTask(id: Long, transform: (BackgroundTask) -> BackgroundTask) {
        _tasks.update { list ->
            list.map { task -> if (task.id == id) transform(task) else task }
        }
    }

    /** 停止指定后台任务。 */
    fun cancel(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.status == BackgroundTaskStatus.RUNNING) {
            TerminalSessionManager.stopSessionProcess(task.sessionId)
        }
    }

    /** 移除一个已经结束的任务。 */
    fun remove(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.status == BackgroundTaskStatus.RUNNING) return
        monitorJobs.remove(id)?.cancel()
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    /** 关闭任务弹窗时清理所有已结束的任务，仍在运行的任务保留。 */
    fun clearFinished() {
        val finishedIds = _tasks.value
            .filter { it.status != BackgroundTaskStatus.RUNNING }
            .map { it.id }
            .toSet()
        finishedIds.forEach { monitorJobs.remove(it)?.cancel() }
        _tasks.update { list -> list.filterNot { it.id in finishedIds } }
    }
}
