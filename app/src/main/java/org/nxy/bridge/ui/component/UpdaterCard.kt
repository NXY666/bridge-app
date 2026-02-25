package org.nxy.bridge.ui.component

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nxy.bridge.ui.model.UpdaterDiscoveryViewModel
import org.nxy.bridge.ui.model.UpdaterViewModel
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

    // 启动时自动扫描更新服务
    LaunchedEffect(Unit) {
        updaterDiscovery.startDiscovery()
    }

    // 发现更新服务后自动检查更新
    val firstService = updaterDiscovery.discoveredServices.firstOrNull()
    LaunchedEffect(firstService) {
        if (firstService != null) {
            viewModel.autoCheckForUpdates(firstService.baseUrl, firstService.path)
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "更新",
                    style = MaterialTheme.typography.headlineSmall
                )

                val headerIconState = when {
                    viewModel.checking || updaterDiscovery.isSearching -> 1
                    updaterDiscovery.hasSearched && firstService == null -> 2
                    else -> 0
                }
                AnimatedContent(
                    targetState = headerIconState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "updater_header_icon"
                ) { state ->
                    when (state) {
                        1 -> LoadingIndicator(modifier = Modifier.size(32.dp))
                        2 -> IconButton(
                            onClick = { updaterDiscovery.startDiscovery() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "重试",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> Box(modifier = Modifier.size(32.dp))
                    }
                }
            }

            if (firstService == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
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
                    modifier = Modifier.fillMaxWidth()
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = !(viewModel.checking || viewModel.downloading),
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                        onClick = { firstService.let { viewModel.checkForUpdates(it.baseUrl, it.path) } }
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
