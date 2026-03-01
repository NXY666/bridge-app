package org.nxy.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nxy.bridge.ui.admin.AdminTab
import org.nxy.bridge.ui.home.HomeTab
import org.nxy.bridge.ui.model.MainViewModel
import org.nxy.bridge.ui.settings.PasswordDialog
import org.nxy.bridge.ui.settings.SettingsDialog
import org.nxy.bridge.ui.theme.BridgeTheme

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
