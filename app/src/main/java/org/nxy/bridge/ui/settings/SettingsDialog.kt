package org.nxy.bridge.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BroadcastOnHome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.nxy.bridge.ui.model.BridgeDiscoveryViewModel
import org.nxy.bridge.ui.model.MainViewModel

/**
 * 设置对话框：URL、服务发现与参数编辑。
 */

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    visible: Boolean,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val bridgeDiscoveryViewModel: BridgeDiscoveryViewModel = viewModel()

    var urlInput by rememberSaveable { mutableStateOf(mainViewModel.url) }
    var landscapeInput by rememberSaveable { mutableStateOf(mainViewModel.landscape) }
    var parametersInput by rememberSaveable { mutableStateOf(mainViewModel.parameters) }

    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    var editingKey by rememberSaveable { mutableStateOf("") }
    var editingValue by rememberSaveable { mutableStateOf("") }
    var originalKey by rememberSaveable { mutableStateOf("") }

    AlertDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(
            enabled = urlInput.isNotBlank(),
            onClick = {
                mainViewModel.url = urlInput
                mainViewModel.landscape = landscapeInput
                mainViewModel.parameters = parametersInput
                onDismiss()
            }
        ) { Text("保存") }
    }, dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    }, title = { Text("设置") }, text = {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            val searcherModifier = if (bridgeDiscoveryViewModel.isSearching) {
                Modifier
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.shapes.small
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.small
                    )
            } else {
                Modifier
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        MaterialTheme.shapes.small
                    )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(searcherModifier)
                    .animateContentSize()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !bridgeDiscoveryViewModel.isSearching,
                            onClick = {
                                bridgeDiscoveryViewModel.startDiscovery()
                            })
                        .padding(12.dp)
                        .clip(MaterialTheme.shapes.small)
                ) {
                    Text(
                        "服务发现",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    AnimatedContent(
                        targetState = bridgeDiscoveryViewModel.isSearching,
                        transitionSpec = {
                            val enter =
                                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                        scaleIn(
                                            initialScale = 0.80f,
                                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                                        )
                            val exit =
                                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                        scaleOut(
                                            targetScale = 0.92f,
                                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                                        )
                            enter.togetherWith(exit)
                        }
                    ) { isSearching ->
                        Box(contentAlignment = Alignment.Center) {
                            if (isSearching) {
                                LoadingIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(0.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = "扫描",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                if (bridgeDiscoveryViewModel.discoveredServices.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .fillMaxWidth()
                            .padding(12.dp, 0.dp, 12.dp, 12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        bridgeDiscoveryViewModel.discoveredServices.forEach { service ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        urlInput = service.url
                                        service.landscape?.let { landscapeInput = it }
                                        service.parameters?.let { parametersInput = it }
                                        bridgeDiscoveryViewModel.stopDiscovery()
                                        bridgeDiscoveryViewModel.clearServices()
                                    }
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        MaterialTheme.shapes.small
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(12.dp, 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.BroadcastOnHome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        service.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        service.url,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    val chipLabels = buildList {
                                        service.parameters?.entries?.forEach { add("${it.key}=${it.value}") }
                                        service.landscape?.let { add(if (it) "横屏" else "竖屏") }
                                    }
                                    if (chipLabels.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            chipLabels.forEach { label ->
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier
                                                        .background(
                                                            MaterialTheme.colorScheme.secondaryContainer,
                                                            CircleShape
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "参数",
                        style = MaterialTheme.typography.titleMedium,
                        lineHeight = 32.sp
                    )

                    IconButton(
                        onClick = {
                            editingKey = ""
                            editingValue = ""
                            originalKey = ""
                            showEditSheet = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "添加参数"
                        )
                    }
                }

                parametersInput.entries.toList().forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.shapes.small
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.small
                            )
                            .padding(12.dp, 8.dp, 8.dp, 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = entry.key.ifBlank { "<置空>" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = entry.value.ifBlank { "<置空>" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    editingKey = entry.key
                                    editingValue = entry.value
                                    originalKey = entry.key
                                    showEditSheet = true
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "编辑参数",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    parametersInput = parametersInput - entry.key
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "删除参数",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column {
                Text("屏幕方向", style = MaterialTheme.typography.titleMedium, lineHeight = 32.sp)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = landscapeInput,
                        onClick = { landscapeInput = true },
                        label = { Text("横屏") },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 32.dp)
                            .weight(1f)
                    )

                    FilterChip(
                        selected = !landscapeInput,
                        onClick = { landscapeInput = false },
                        label = { Text("竖屏") },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 32.dp)
                            .weight(1f)
                    )
                }
            }
        }
    })

    // 参数编辑 BottomSheet
    if (showEditSheet) {
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        fun dismiss() {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    showEditSheet = false
                    editingKey = ""
                    editingValue = ""
                    originalKey = ""
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { dismiss() },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (originalKey.isEmpty()) "添加参数" else "编辑参数",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = editingKey,
                    onValueChange = { editingKey = it },
                    label = { Text("键") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    label = { Text("值") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { dismiss() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (editingKey.isNotBlank()) {
                                val newParams = parametersInput.toMutableMap()
                                if (originalKey.isNotEmpty()) newParams.remove(originalKey)
                                newParams[editingKey] = editingValue
                                parametersInput = newParams
                            }
                            dismiss()
                        },
                        enabled = editingKey.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
