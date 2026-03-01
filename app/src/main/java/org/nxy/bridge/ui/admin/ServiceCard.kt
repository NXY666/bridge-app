package org.nxy.bridge.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.nxy.bridge.ui.model.MainViewModel

/**
 * 服务卡片：展示当前 URL 并提供配置入口。
 */
@Composable
internal fun ServiceCard(
    mainViewModel: MainViewModel,
    onShowSettingsDialog: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "服务", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = mainViewModel.getUrlWithParameters(),
                onValueChange = {},
                label = { Text("URL") },
                singleLine = true,
                readOnly = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onShowSettingsDialog
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = null,
                    Modifier.size(18.dp)
                )
                Text(text = "配置", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
