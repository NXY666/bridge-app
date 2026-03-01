package org.nxy.bridge.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.nxy.bridge.ui.activity.BrowserActivity
import org.nxy.bridge.ui.model.KEY_URL
import org.nxy.bridge.ui.model.MainViewModel

/**
 * 浏览卡片，提供 URL 展示与启动入口。
 */
@Composable
fun BrowseCard(
    mainViewModel: MainViewModel
) {
    val context = LocalContext.current

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

            Button(
                enabled = mainViewModel.url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
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
        }
    }
}
