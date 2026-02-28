package org.nxy.bridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mozilla.geckoview.StorageController
import org.nxy.bridge.ui.activity.BrowserActivity
import org.nxy.bridge.ui.component.LanIpCard
import org.nxy.bridge.ui.component.PasswordDialog
import org.nxy.bridge.ui.component.SettingsDialog
import org.nxy.bridge.ui.component.UpdaterCard
import org.nxy.bridge.ui.model.GeckoViewModel
import org.nxy.bridge.ui.model.KEY_URL
import org.nxy.bridge.ui.model.MainViewModel
import org.nxy.bridge.ui.theme.BridgeTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            BridgeTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    UrlEntryScreen()
                }
            }
        }
    }
}

/**
 * 应用主入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlEntryScreen() {
    val mainViewModel: MainViewModel = viewModel()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Bridge") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null) },
                    label = { Text("管理") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HomeTab(
                innerPadding = innerPadding,
                mainViewModel = mainViewModel,
                onShowPasswordDialog = { showPasswordDialog = true }
            )

            1 -> AdminTab(innerPadding = innerPadding)
        }
    }

    PasswordDialog(
        visible = showPasswordDialog,
        onDismiss = { showPasswordDialog = false },
        onSuccess = {
            showPasswordDialog = false
            showSettings = true
        }
    )

    SettingsDialog(
        visible = showSettings,
        mainViewModel = mainViewModel,
        onDismiss = { showSettings = false }
    )
}

/**
 * 首页：URL 连接配置与局域网 IP。
 */
@Composable
private fun HomeTab(
    innerPadding: PaddingValues,
    mainViewModel: MainViewModel,
    onShowPasswordDialog: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "浏览", style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = mainViewModel.getUrlWithParameters(),
                    onValueChange = {},
                    label = { Text("URL") },
                    singleLine = true,
                    readOnly = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = mainViewModel.url.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val final = mainViewModel.getUrlWithParameters()
                            if (final.isBlank()) return@Button
                            val intent = Intent(context, BrowserActivity::class.java)
                            intent.putExtra(KEY_URL, final)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            Modifier.size(18.dp)
                        )
                        Text(
                            text = "启动", modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    OutlinedButton(
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onShowPasswordDialog
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            Modifier.size(18.dp)
                        )
                        Text(
                            text = "设置", modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        LanIpCard()
    }
}

/**
 * 管理页：应用更新、数据清理与关于信息。
 */
@Composable
private fun AdminTab(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UpdaterCard()
        CleanupCard()
        AboutCard()
    }
}

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
private fun CleanupCard() {
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

/**
 * 关于卡片，展示项目信息与仓库链接。
 */
@Composable
private fun AboutCard() {
    val uriHandler = LocalUriHandler.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "关于", style = MaterialTheme.typography.headlineSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Bridge App",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "NXY",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { uriHandler.openUri("https://github.com/NXY666/bridge-app") }
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "打开仓库",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

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
