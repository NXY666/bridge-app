package org.nxy.bridge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.UUID

data class KvItem(
    val id: String = UUID.randomUUID().toString(),
    var enabled: Boolean = true,
    var key: String = "",
    var value: String = "",
    var note: String = ""
)

/**
 * 键值对列表编辑器（如请求头）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KvArrayEditor(
    title: String = "Headers",
    initial: List<KvItem> = listOf(
        KvItem(key = "Accept", value = "application/json"),
        KvItem(key = "User-Agent", value = "MyApp/1.0")
    ),
    onApply: (List<KvItem>) -> Unit = {}
) {
    val items = remember { mutableStateListOf<KvItem>().apply { addAll(initial) } }
    var showBulk by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { showBulk = true }) {
                        Icon(Icons.Default.Upload, contentDescription = "Bulk Edit")
                    }
                    IconButton(onClick = { items.add(KvItem()) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    IconButton(onClick = { /* 预留更多操作 */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onApply(items.toList()) },
                content = { Text("应用") }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        KvRow(
                            item = item,
                            onDelete = { items.remove(item) },
                            onDuplicate = {
                                val copy = item.copy(id = UUID.randomUUID().toString())
                                items.add(index + 1, copy)
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    items.removeAt(index)
                                    items.add(index - 1, item)
                                }
                            }
                        ) {
                            if (index < items.lastIndex) {
                                items.removeAt(index)
                                items.add(index + 1, item)
                            }
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = DividerDefaults.Thickness,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                    item {
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            onClick = { items.add(KvItem()) }
                        ) { Text("新增一行") }
                    }
                }
            }

            AssistChipsRow(
                onPaste = { /* 外部接入剪贴板解析 */ },
                onBulk = { showBulk = true }
            )
        }
    }

    if (showBulk) {
        BulkEditSheet(
            onDismiss = { showBulk = false },
            onImport = { text ->
                // 支持 key:value / key=value / key<TAB>value
                text.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        val pair = when {
                            "\t" in line -> line.split("\t", limit = 2)
                            ":" in line -> line.split(":", limit = 2)
                            "=" in line -> line.split("=", limit = 2)
                            else -> null
                        }?.map { it.trim() }
                        if (pair != null && pair.size == 2) KvItem(
                            key = pair[0],
                            value = pair[1]
                        ) else null
                    }
                    .toList()
                    .takeIf { it.isNotEmpty() }
            }
        )
    }
}

@Composable
private fun KvRow(
    item: KvItem,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val bg = if (item.enabled) MaterialTheme.colorScheme.surface else
        MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.enabled,
            onCheckedChange = { item.enabled = it }
        )

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.key,
                    onValueChange = { item.key = it },
                    label = { Text("Key") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = item.value,
                    onValueChange = { item.value = it },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
            if (item.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    Text(
                        text = item.note,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
private fun AssistChipsRow(
    onPaste: () -> Unit,
    onBulk: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = onPaste, label = { Text("从剪贴板粘贴") })
        AssistChip(onClick = onBulk, label = { Text("批量编辑") })
    }
}

/**
 * 批量编辑键值对的底部抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkEditSheet(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var text by remember { mutableStateOf("# 一行一条：key:value / key=value / key<TAB>value") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("批量编辑", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text("Content-Type: application/json\nAuthorization: Bearer ...") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onImport(text); onDismiss() }) { Text("导入") }
            }
        }
    }
}

@Preview(
    name = "KV Editor — Light",
    showBackground = true,
    widthDp = 420,
    heightDp = 720
)
@Composable
fun KvArrayEditorPreview_Light() {
    MaterialTheme {
        KvArrayEditor(
            title = "请求头（Key-Value 列表）",
            initial = listOf(
                KvItem(
                    key = "Accept",
                    value = "application/json",
                    enabled = true,
                    note = "默认 JSON"
                ),
                KvItem(key = "Authorization", value = "Bearer •••", enabled = true),
                KvItem(key = "User-Agent", value = "MyApp/1.0 (Android 15)", enabled = true),
                KvItem(key = "X-Debug", value = "true", enabled = false, note = "禁用示例行")
            ),
            onApply = { /* 预览中可留空 */ }
        )
    }
}
