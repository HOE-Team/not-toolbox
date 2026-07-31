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
import androidx.compose.ui.unit.dp
import components.CPUStatCard
import components.RAMStatCard
import components.GPUStatCard
import components.SystemOverviewCard
import components.NetworkIOCard
import components.NetworkAdapterCard
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

    // Update system info every 1 second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            systemInfo = SystemInfoProvider.getSystemInfo()
            systemOverview = SystemInfoProvider.getSystemOverview()
            networkIO = SystemInfoProvider.getNetworkIO()
        }
    }

    // Masonry-style adaptive layout: column count derives from the available
    // width. Each column is an independent, tightly-packed stack (no cross-row
    // height alignment), so cards auto-align/snap without vertical gaps.
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

        // Cards to distribute (round-robin across columns for near-even packing).
        val items: List<@Composable () -> Unit> = listOf(
            {
                CPUStatCard(
                    model = systemInfo.cpu.model,
                    usage = systemInfo.cpu.usage,
                    stepping = systemInfo.cpu.stepping,
                    currentFreq = systemInfo.cpu.currentFreq,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            {
                RAMStatCard(
                    frequency = systemInfo.ram.frequency,
                    used = systemInfo.ram.used,
                    total = systemInfo.ram.total,
                    usage = systemInfo.ram.usage,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            {
                GPUStatCard(
                    gpus = systemInfo.gpus,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            {
                components.DiskStatCard(
                    disks = systemInfo.disks,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            {
                NetworkAdapterCard(
                    network = networkIO,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            {
                NetworkIOCard(
                    network = networkIO,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                for (col in 0 until columns) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        items.forEachIndexed { index, card ->
                            if (index % columns == col) {
                                card()
                            }
                        }
                    }
                }
            }
        }
    }
}