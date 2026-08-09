// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavRail(
    selectedIndex: Int = 0,
    onSelection: (Int) -> Unit = {}
) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            NavigationRailItem(selected = selectedIndex == 0, onClick = { onSelection(0) }, icon = { Icon(MaterialSymbols.Home, contentDescription = "Home") }, label = { Text("概览") })
            NavigationRailItem(selected = selectedIndex == 1, onClick = { onSelection(1) }, icon = { Icon(MaterialSymbols.Build, contentDescription = "Tools") }, label = { Text("工具") })
            NavigationRailItem(selected = selectedIndex == 2, onClick = { onSelection(2) }, icon = { Icon(MaterialSymbols.Terminal, contentDescription = "Terminal") }, label = { Text("终端") })
            NavigationRailItem(selected = selectedIndex == 3, onClick = { onSelection(3) }, icon = { Icon(MaterialSymbols.Settings, contentDescription = "Settings") }, label = { Text("设置") })
            NavigationRailItem(selected = selectedIndex == 4, onClick = { onSelection(4) }, icon = { Icon(MaterialSymbols.Info, contentDescription = "About") }, label = { Text("关于") })
        }
    }
}
