// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import components.CPUStatCard
import components.RAMStatCard
import components.GPUStatCard
import components.SystemOverviewCard
import components.NetworkIOCard
import components.NetworkAdapterCard
import components.NetworkAdaptersCard
import components.BatteryStatCard
import components.BluetoothStatCard
import components.ScreenStatCard
import components.ServicesStatCard
import utils.SystemSnapshot
import utils.runSystemInfoRefreshLoop
import utils.systemInfoLoadingStage
import utils.systemSnapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    // 采集完全在后台线程进行，UI 线程只订阅结果。
    // 注意：这里绝不能直接引用 SystemInfoProvider 的成员，否则会在 UI 线程触发 OSHI 初始化。
    LaunchedEffect(Unit) { runSystemInfoRefreshLoop() }
    val snapshot by systemSnapshotFlow.collectAsState()

    val current = snapshot
    if (current == null) {
        // 首次数据未就绪：转圈占位，UI 线程保持空闲
        HomeLoadingHint()
    } else {
        HomeContent(current)
    }
}

/** 首次采集未完成时的占位：居中转圈（数据由后台线程采集，不会阻塞这里） */
@Composable
private fun HomeLoadingHint() {
    // 副文本：当前正在获取的系统信息项目（由采集侧在后台线程上报）
    val stage by systemInfoLoadingStage.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
            Text("正在读取系统信息", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stage ?: "正在初始化…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(snapshot: SystemSnapshot) {
    val systemInfo = snapshot.systemInfo
    val systemOverview = snapshot.overview
    val networkIO = snapshot.networkIO
    val services = snapshot.services
    val battery = snapshot.battery
    val screen = snapshot.screen
    val bluetooth = snapshot.bluetooth

    // Masonry-style adaptive layout ("补位原则" / true shortest-column packing):
    // column count derives from the available width. Each card is measured with
    // its real rendered height, then placed into the column that currently has
    // the least accumulated height (the largest gap), producing a waterfall
    // arrangement without any estimated-height guesswork.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val spacing = 8.dp
        val cardMinWidth = 290.dp
        val columns = (((maxWidth - spacing) + spacing) / (cardMinWidth + spacing))
            .toInt()
            .coerceAtLeast(1)

        // The actual cards to render. buildList preserves the @Composable context
        // for every lambda, and the battery card is appended only when present.
        val items: List<@Composable () -> Unit> = buildList {
            add {
                CPUStatCard(
                    model = systemInfo.cpu.model,
                    usage = systemInfo.cpu.usage,
                    stepping = systemInfo.cpu.stepping,
                    currentFreq = systemInfo.cpu.currentFreq,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            add {
                RAMStatCard(
                    frequency = systemInfo.ram.frequency,
                    used = systemInfo.ram.used,
                    total = systemInfo.ram.total,
                    usage = systemInfo.ram.usage,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            add {
                GPUStatCard(
                    gpus = systemInfo.gpus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            add {
                components.DiskStatCard(
                    disks = systemInfo.disks,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            add {
                NetworkAdapterCard(
                    network = networkIO,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            add {
                NetworkIOCard(
                    network = networkIO,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            add {
                NetworkAdaptersCard(
                    network = networkIO,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            add {
                ServicesStatCard(
                    services = services,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (battery.hasBattery) {
                add {
                    BatteryStatCard(
                        battery = battery,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            add {
                ScreenStatCard(
                    screen = screen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Show the bluetooth card only if an adapter is detected.
            if (bluetooth.hasAdapter) {
                add {
                    BluetoothStatCard(
                        bluetooth = bluetooth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // System overview spans the full width.
            SystemOverviewCard(
                overview = systemOverview,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(spacing))

            // True masonry: measure each card, then fill the current shortest column.
            // spacingPx uses a fixed pixel value (matches 8.dp at density 1.0).
            ColumnMasonry(
                columns = columns,
                spacingPx = 8,
                items = items,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A masonry (waterfall) layout that strictly follows the "fill the largest gap"
 * rule. Every card is measured with its real height and then placed into the
 * column that currently has the smallest accumulated height (the tallest gap
 * is filled first → cards always snap into the shortest column).
 */
@Composable
private fun ColumnMasonry(
    columns: Int,
    spacingPx: Int,
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val colWidth = (constraints.maxWidth - spacingPx * (columns - 1)) / columns

        // Measure every card's real height under a fixed column width.
        val placeables: List<Placeable> = items.mapIndexed { index, content ->
            subcompose(index, content).first().measure(
                Constraints.fixedWidth(colWidth)
            )
        }

        // Greedy shortest-column packing based on measured heights.
        val colHeights = IntArray(columns)
        val colBuckets: List<MutableList<Placeable>> = List(columns) { mutableListOf() }

        placeables.forEach { placeable ->
            val target = (0 until columns).minByOrNull { colHeights[it] } ?: 0
            colBuckets[target].add(placeable)
            colHeights[target] += placeable.height + spacingPx
        }

        // Layout at max width; height = tallest column (minus trailing spacing).
        val totalHeight = (colHeights.maxOrNull() ?: 0) - spacingPx
        layout(constraints.maxWidth, totalHeight.coerceAtLeast(0)) {
            val xOffsets = IntArray(columns) { it * (colWidth + spacingPx) }
            val yOffsets = IntArray(columns)

            for (col in 0 until columns) {
                for (placeable in colBuckets[col]) {
                    placeable.placeRelative(xOffsets[col], yOffsets[col])
                    yOffsets[col] += placeable.height + spacingPx
                }
            }
        }
    }
}
