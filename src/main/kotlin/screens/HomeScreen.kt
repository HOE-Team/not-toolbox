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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
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
import components.ScreenStatCard
import components.ServicesStatCard
import utils.SystemInfoProvider
import utils.SystemInfoSnapshot
import utils.SystemOverview
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var systemInfo by remember { mutableStateOf(SystemInfoProvider.getSystemInfo()) }
    var systemOverview by remember { mutableStateOf(SystemInfoProvider.getSystemOverview()) }
    var networkIO by remember { mutableStateOf(SystemInfoProvider.getNetworkIO()) }
    var services by remember { mutableStateOf(SystemInfoProvider.getServices()) }
    var battery by remember { mutableStateOf(SystemInfoProvider.getBattery()) }
    var screen by remember { mutableStateOf(SystemInfoProvider.getScreen()) }

    // Update system info every 1 second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            systemInfo = SystemInfoProvider.getSystemInfo()
            systemOverview = SystemInfoProvider.getSystemOverview()
            networkIO = SystemInfoProvider.getNetworkIO()
            services = SystemInfoProvider.getServices()
            battery = SystemInfoProvider.getBattery()
            screen = SystemInfoProvider.getScreen()
        }
    }

    // Masonry-style adaptive layout ("补位原则" / true shortest-column packing):
    // column count derives from the available width. Each card is measured with
    // its real rendered height, then placed into the column that currently has
    // the least accumulated height (the largest gap), producing a waterfall
    // arrangement without any estimated-height guesswork.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
