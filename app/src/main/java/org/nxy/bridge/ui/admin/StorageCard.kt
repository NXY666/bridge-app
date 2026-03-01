package org.nxy.bridge.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mozilla.geckoview.StorageController
import org.nxy.bridge.ui.model.GeckoViewModel

private data class CleanOption(
    val label: String,
    val flag: Long,
    val depth: Int = 0,
    val parentFlags: Set<Long> = emptySet(),
    val children: List<CleanOption> = emptyList()
)

private fun buildCleanOptionTree(): List<CleanOption> {
    val allFlag = StorageController.ClearFlags.ALL
    val allCachesFlag = StorageController.ClearFlags.ALL_CACHES

    fun node(
        label: String,
        flag: Long,
        depth: Int,
        parentFlags: Set<Long>,
        children: List<CleanOption> = emptyList()
    ) = CleanOption(label, flag, depth, parentFlags, children)

    return listOf(
        node(
            "全部", allFlag, 0, emptySet(), listOf(
                node(
                    "所有缓存", allCachesFlag, 1, setOf(allFlag), listOf(
                        node(
                            "图片缓存",
                            StorageController.ClearFlags.IMAGE_CACHE,
                            2,
                            setOf(allFlag, allCachesFlag)
                        ),
                        node(
                            "网络缓存",
                            StorageController.ClearFlags.NETWORK_CACHE,
                            2,
                            setOf(allFlag, allCachesFlag)
                        ),
                    )
                ),
                node("Cookie", StorageController.ClearFlags.COOKIES, 1, setOf(allFlag)),
                node("站点数据", StorageController.ClearFlags.SITE_DATA, 1, setOf(allFlag)),
                node("本地存储", StorageController.ClearFlags.DOM_STORAGES, 1, setOf(allFlag)),
                node("认证会话", StorageController.ClearFlags.AUTH_SESSIONS, 1, setOf(allFlag)),
                node("权限设置", StorageController.ClearFlags.PERMISSIONS, 1, setOf(allFlag)),
                node("网站设置", StorageController.ClearFlags.SITE_SETTINGS, 1, setOf(allFlag)),
            )
        )
    )
}

private fun flattenCleanOptions(options: List<CleanOption>): List<CleanOption> =
    options.flatMap { listOf(it) + flattenCleanOptions(it.children) }

/**
 * 数据清理卡片，提供多类型缓存清除操作。
 */
@Composable
internal fun CleanupCard() {
    val geckoViewModel: GeckoViewModel = viewModel()
    var showCleanupDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "存储", style = MaterialTheme.typography.headlineSmall)

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showCleanupDialog = true }
            ) {
                Icon(
                    Icons.Rounded.CleaningServices,
                    contentDescription = null,
                    Modifier.size(18.dp)
                )
                Text(
                    text = "清理数据",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (showCleanupDialog) {
        val flatOptions = remember { flattenCleanOptions(buildCleanOptionTree()) }
        var selectedFlags by remember { mutableStateOf(setOf<Long>()) }

        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            confirmButton = {
                TextButton(
                    enabled = selectedFlags.isNotEmpty(),
                    onClick = {
                        val flags = selectedFlags.fold(0L) { acc, flag -> acc or flag }
                        geckoViewModel.runtime.storageController.clearData(flags)
                        showCleanupDialog = false
                    }
                ) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) { Text("取消") }
            },
            title = { Text("清理数据") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    flatOptions.forEach { opt ->
                        val coveredByAncestor = opt.parentFlags.any { it in selectedFlags }
                        val isSelected = opt.flag in selectedFlags

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (opt.depth * 24).dp)
                                .alpha(if (coveredByAncestor) 0.38f else 1f)
                                .clickable(enabled = !coveredByAncestor) {
                                    selectedFlags = if (isSelected) {
                                        selectedFlags - opt.flag
                                    } else {
                                        selectedFlags + opt.flag
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                checked = isSelected || coveredByAncestor,
                                onCheckedChange = null,
                                enabled = !coveredByAncestor
                            )
                            Text(opt.label)
                        }
                    }
                }
            }
        )
    }
}
