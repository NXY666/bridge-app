package org.nxy.bridge.ui.admin

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import org.nxy.bridge.ui.activity.ScannerActivity
import org.nxy.bridge.ui.model.MainViewModel
import org.nxy.bridge.ui.model.ScanResultParser

/**
 * 管理页：应用更新、数据清理与关于信息，进入前需要解锁。
 */
@Composable
fun AdminTab(
    innerPadding: PaddingValues,
    mainViewModel: MainViewModel,
    onShowPasswordDialog: () -> Unit,
    onShowSettingsDialog: () -> Unit
) {
    val context = LocalContext.current

    // 扫码页启动器
    val scanResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val json = data.getStringExtra(ScannerActivity.EXTRA_SCAN_RESULT)
            if (json.isNullOrEmpty()) return@rememberLauncherForActivityResult
            val parsed = try {
                Gson().fromJson(
                    json, ScanResultParser.ParsedScan::class.java
                )
            } catch (_: Exception) {
                null
            }
            if (parsed != null && parsed.url.isNotEmpty()) {
                mainViewModel.applyScanResult(parsed)
            }
        }
    }

    if (!mainViewModel.isAdminUnlocked) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = maxHeight * 0.25f)
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Button(
                onClick = onShowPasswordDialog,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = maxHeight * 0.75f)
            ) {
                Text("解锁")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ServiceCard(
            mainViewModel = mainViewModel,
            onShowSettingsDialog = onShowSettingsDialog,
            onScan = {
                scanResultLauncher.launch(Intent(context, ScannerActivity::class.java))
            }
        )
        UpdaterCard()
        CleanupCard()
        CompatibilityCard(mainViewModel = mainViewModel)
        AboutCard()
    }
}


