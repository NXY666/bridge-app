package org.nxy.bridge.ui.component

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GetApp
import androidx.compose.material.icons.rounded.Hardware
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nxy.bridge.ui.model.UpdaterDiscoveryViewModel
import org.nxy.bridge.ui.model.UpdaterViewModel
import org.nxy.bridge.ui.theme.onSuccessContainerDark
import org.nxy.bridge.ui.theme.onSuccessContainerLight
import org.nxy.bridge.ui.theme.successContainerDark
import org.nxy.bridge.ui.theme.successContainerLight
import java.io.File

data class ServerVersion(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val sha256: String
)

/**
 * 应用更新卡片：检查、下载、安装与缓存管理。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdaterCard() {
    val context = LocalContext.current
    val viewModel: UpdaterViewModel = viewModel()
    val updaterDiscovery: UpdaterDiscoveryViewModel = viewModel()

    // 发现更新服务后自动检查更新
    val discoveredService = updaterDiscovery.discoveredServices.firstOrNull()
    LaunchedEffect(discoveredService) {
        if (discoveredService != null) {
            viewModel.autoCheckForUpdates(discoveredService.baseUrl, discoveredService.path)
        }
    }

    // 启动时自动扫描更新服务
    LaunchedEffect(Unit) {
        if (!updaterDiscovery.isSearching && discoveredService == null) {
            updaterDiscovery.startDiscovery()
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "更新",
                    style = MaterialTheme.typography.headlineSmall
                )

                val isDark = isSystemInDarkTheme()
                val isServiceDiscovered = discoveredService != null

                // Chip 区域：已连接 / 未连接 / 空
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 12.dp, end = 4.dp)
                        .weight(1f)
                ) {
                    val chipAlpha by animateFloatAsState(
                        targetValue = if (updaterDiscovery.isSearching) 0f else 1f,
                        label = "updater-chip-alpha"
                    )
                    Box(modifier = Modifier.graphicsLayer { alpha = chipAlpha }) {
                        if (isServiceDiscovered) {
                            StatusChip(
                                label = {
                                    Text(
                                        "${discoveredService.host}:${discoveredService.port}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                containerColor = if (isDark) successContainerDark else successContainerLight,
                                labelColor = if (isDark) onSuccessContainerDark else onSuccessContainerLight,
                                border = BorderStroke(0.dp, Color.Transparent)
                            )
                        } else {
                            StatusChip(
                                label = { Text("未连接") },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                border = BorderStroke(0.dp, Color.Transparent)
                            )
                        }
                    }
                }

                // 操作区域：加载中 / 断开 / 重试 / 空
                AnimatedContent(
                    targetState = updaterDiscovery.isSearching,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "updater-action"
                ) { isLoading ->
                    if (isLoading) {
                        LoadingIndicator(modifier = Modifier.size(32.dp))
                        return@AnimatedContent
                    }

                    if (isServiceDiscovered) {
                        val isBusy = viewModel.checking || viewModel.downloading

                        IconButton(
                            enabled = !isBusy,
                            onClick = { updaterDiscovery.clearService() },
                            modifier = Modifier
                                .size(32.dp)
                                .alpha(if (isBusy) 0.38f else 1f)
                        ) {
                            Icon(
                                Icons.Rounded.LinkOff,
                                contentDescription = "断开连接",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { updaterDiscovery.startDiscovery() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "重新扫描",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (discoveredService == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.small
                        )
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "当前版本",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${viewModel.currentName} (${viewModel.currentCode})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                val f1 by animateFloatAsState(if (viewModel.downloading) 0.4f else 0.5f)
                val f2 = 1f - f1

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = MaterialTheme.shapes.small
                            )
                            .weight(f1)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "当前版本",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${viewModel.currentName} (${viewModel.currentCode})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = if (viewModel.hasUpdate) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = MaterialTheme.shapes.small
                            )
                            .weight(f2)
                            .padding(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "最新版本",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (viewModel.hasUpdate) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = viewModel.latest?.let { "${it.versionName} (${it.versionCode})" }
                                    ?: "-",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (viewModel.hasUpdate) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        AnimatedVisibility(
                            visible = viewModel.downloading,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            if (viewModel.downloadHasTotal) {
                                CircularWavyProgressIndicator(
                                    progress = { viewModel.downloadProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.size(36.dp),
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            } else {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }

            }

            AnimatedVisibility(
                visible = discoveredService != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Button(
                        enabled = !(viewModel.checking || viewModel.downloading),
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                        onClick = {
                            discoveredService?.let {
                                viewModel.checkForUpdates(
                                    it.baseUrl,
                                    it.path
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "检查更新")
                    }

                    Button(
                        enabled = viewModel.hasUpdate && viewModel.latest != null && !viewModel.hasCache && !(viewModel.checking || viewModel.downloading),
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                        onClick = { viewModel.downloadUpdate() }
                    ) {
                        Icon(Icons.Rounded.GetApp, contentDescription = "下载更新")
                    }

                    Button(
                        enabled = viewModel.hasCache && !(viewModel.checking || viewModel.downloading),
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                        onClick = {
                            installApk(context, getCacheApkFile(context))
                            viewModel.refreshCacheStatus()
                        }
                    ) {
                        Icon(Icons.Rounded.Hardware, contentDescription = "安装更新")
                    }

                    Button(
                        enabled = viewModel.hasCache && !(viewModel.checking || viewModel.downloading),
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                        onClick = { viewModel.deleteCache() }
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除安装包")
                    }
                }
            }
        }
    }
}

fun getCacheApkFile(context: Context): File = File(context.cacheDir, "update.apk")

internal fun installApk(context: Context, file: File) {
    try {
        if (!file.exists()) {
            Toast.makeText(context, "安装包不存在", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pm = context.packageManager
            if (!pm.canRequestPackageInstalls()) {
                Toast.makeText(context, "请授予安装权限", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = ("package:" + context.packageName).toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开安装包: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
