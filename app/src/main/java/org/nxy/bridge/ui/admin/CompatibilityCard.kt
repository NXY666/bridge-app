package org.nxy.bridge.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nxy.bridge.ui.model.MainViewModel

/**
 * 兼容性卡片：提供影响浏览器行为的兼容性开关（影响 Gecko 全局 pref）。
 */
@Composable
internal fun CompatibilityCard(mainViewModel: MainViewModel) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "兼容性", style = MaterialTheme.typography.headlineSmall)

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDialog = true }
            ) {
                Icon(
                    Icons.Rounded.Build,
                    contentDescription = null,
                    Modifier.size(18.dp)
                )
                Text(
                    text = "配置",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (showDialog) {
        CompatibilityDialog(
            mainViewModel = mainViewModel,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun CompatibilityDialog(
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var legacyViewport by rememberSaveable { mutableStateOf(mainViewModel.legacyViewport) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    mainViewModel.legacyViewport = legacyViewport
                    onDismiss()
                }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("兼容性") },
        text = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "修复定制设备视口异常扩大的问题",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = legacyViewport,
                    onCheckedChange = { legacyViewport = it }
                )
            }
        }
    )
}