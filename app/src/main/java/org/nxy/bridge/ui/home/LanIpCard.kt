package org.nxy.bridge.ui.home

import android.content.ClipData
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 局域网 IP 列表卡片，支持 IPv4/IPv6、刷新与复制。
 */
@Composable
fun LanIpCard(
    modifier: Modifier = Modifier
) {
    var includeIpv6 by remember { mutableStateOf(false) }
    var ipList by remember { mutableStateOf(listOf<IpEntry>()) }

    val reload: () -> Unit = remember(includeIpv6) {
        {
            ipList = runCatching { queryLocalIps(includeIpv6) }
                .onFailure { Log.w("LanIpCard", "queryLocalIps failed", it) }
                .getOrDefault(emptyList())
        }
    }

    LaunchedEffect(includeIpv6) { reload() }

    val clipboard = LocalClipboard.current

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "局域网 IP", style = MaterialTheme.typography.headlineSmall)

            if (ipList.isEmpty()) {
                Text(
                    text = "未发现局域网 IP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ipList.forEachIndexed { _, entry ->
                    IpRow(entry = entry, onCopy = {
                        runBlocking {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    clipData = ClipData(
                                        "IP Address",
                                        arrayOf("text/plain"),
                                        ClipData.Item(entry.address)
                                    )
                                )
                            )
                        }
                    })
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .selectable(
                            selected = includeIpv6,
                            onClick = { includeIpv6 = !includeIpv6 }
                        )
                        .padding(4.dp)
                ) {
                    Checkbox(
                        checked = includeIpv6,
                        onCheckedChange = null // 由父容器处理点击
                    )
                    Text(
                        text = "IPv6",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = reload) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IpRow(entry: IpEntry, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = entry.address, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${entry.ifaceLabel}${if (entry.isIpv6) " · IPv6" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Start,
                spacingBetweenTooltipAndAnchor = 4.dp
            ),
            state = rememberTooltipState(),
            tooltip = { Text("复制") }
        ) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制 IP")
            }
        }
    }
}

private data class IpEntry(
    val address: String,
    val iface: String,
    val ifaceLabel: String,
    val isIpv6: Boolean
)

private fun queryLocalIps(includeIpv6: Boolean): List<IpEntry> {
    val result = mutableListOf<IpEntry>()
    val interfaces = NetworkInterface.getNetworkInterfaces()
        ?: return emptyList()

    interfaces.asSequence()
        .filter { it.isUp && !it.isLoopback }
        .forEach { nif ->
            val addrs = nif.inetAddresses
            addrs.asSequence()
                .filter { addr ->
                    !addr.isLoopbackAddress && !addr.isAnyLocalAddress &&
                            // 仅筛局域网/链路本地
                            (addr.isSiteLocalAddress || addr.isLinkLocalAddress)
                }
                .filter { addr -> includeIpv6 || addr is Inet4Address }
                .forEach { addr ->
                    val host = addr.hostAddress
                        ?.substringBefore('%') // strip scope id for IPv6
                        ?: return@forEach
                    result.add(
                        IpEntry(
                            address = host,
                            iface = nif.name ?: "",
                            ifaceLabel = friendlyIfaceName(nif.name, nif.displayName),
                            isIpv6 = addr !is Inet4Address
                        )
                    )
                }
        }
    // 稳定排序：IPv4 优先、再按网卡名称
    return result.sortedWith(
        compareBy<IpEntry> { it.isIpv6 }
            .thenBy { it.iface }
            .thenBy { it.address }
    )
}

private fun friendlyIfaceName(name: String?, displayName: String?): String {
    val n = (name ?: "").lowercase()
    val d = (displayName ?: "").lowercase()
    return when {
        "wlan" in n || "wifi" in n || "wi-fi" in d -> "Wi‑Fi (${name ?: displayName})"
        "rmnet" in n || "cellular" in d || "mobile" in d -> "蜂窝网络 (${name ?: displayName})"
        "eth" in n || "ethernet" in d -> "以太网 (${name ?: displayName})"
        else -> displayName ?: name ?: "未知网卡"
    }
}
