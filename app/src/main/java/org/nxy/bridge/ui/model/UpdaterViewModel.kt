package org.nxy.bridge.ui.model

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.nxy.bridge.App
import org.nxy.bridge.ui.admin.ServerVersion
import org.nxy.bridge.ui.admin.getCacheApkFile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 远程版本检查与 APK 下载缓存、进度与安装状态管理。
 */
class UpdaterViewModel : ViewModel() {

    val currentName = getAppVersionName(App.context)
    val currentCode = getAppVersionCode(App.context)

    var latest by mutableStateOf<ServerVersion?>(null)
        private set

    var checking by mutableStateOf(false)
        private set

    var downloading by mutableStateOf(false)
        private set

    var downloadProgress by mutableFloatStateOf(0f)
        private set

    var downloadHasTotal by mutableStateOf(true)
        private set

    private val cachedApk = getCacheApkFile(App.context)
    var hasCache by mutableStateOf(cachedApk.exists())
        private set

    // 是否有更新
    val hasUpdate: Boolean
        get() = (latest?.versionCode ?: 0L) > currentCode

    fun checkForUpdates(baseUrl: String, path: String) {
        if (baseUrl.isBlank() || checking) return

        checking = true
        viewModelScope.launch {
            val sv = fetchServerVersion(App.context, baseUrl, path)
            if (sv != null) {
                latest = sv
                if (cachedApk.exists() && cachedApk.length() <= 0) {
                    runCatching { cachedApk.delete() }
                }
                hasCache = cachedApk.exists()
            }
            checking = false
        }
    }

    fun downloadUpdate() {
        val sv = latest ?: return
        if (!hasUpdate || hasCache || downloading) return

        downloading = true
        downloadProgress = 0f
        downloadHasTotal = true

        viewModelScope.launch {
            val success = downloadToCache(
                App.context,
                sv.downloadUrl,
                cachedApk,
                sv.sha256
            ) { downloaded, total ->
                if (total > 0) {
                    downloadHasTotal = true
                    downloadProgress = downloaded.toFloat() / total.toFloat()
                } else {
                    downloadHasTotal = false
                }
            }

            if (success) {
                hasCache = true
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.context, "下载完成", Toast.LENGTH_SHORT).show()
                }
            }
            downloading = false
        }
    }

    fun deleteCache() {
        if (!hasCache || downloading) return

        runCatching { cachedApk.delete() }
        hasCache = cachedApk.exists()
    }

    fun refreshCacheStatus() {
        hasCache = cachedApk.exists()
    }

    fun autoCheckForUpdates(baseUrl: String, path: String) {
        if (baseUrl.isNotBlank() && !checking) {
            checkForUpdates(baseUrl, path)
        }
    }

    private suspend fun fetchServerVersion(
        context: Context, baseUrl: String, path: String
    ): ServerVersion? {
        val versionUrl = joinUrl(baseUrl, "$path/version")
        val (code, body) = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
                val request = Request.Builder().url(versionUrl).get().build()
                client.newCall(request).execute().use { resp ->
                    val sc = resp.code
                    val text = resp.body.string()
                    sc to text
                }
            } catch (e: Exception) {
                -1 to (e.message ?: "")
            }
        }

        return try {
            val json = JSONObject(body)
            if (!json.optBoolean("state", false)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, json.optString("message", "未知错误"), Toast.LENGTH_SHORT
                    ).show()
                }
                null
            } else {
                val data = json.optJSONObject("data")
                val vName = data?.optString("versionName").orEmpty()
                val vCode = data?.optLong("versionCode", -1L) ?: -1L
                val downUrl = data?.optJSONObject("downUrl")
                val localUrl = downUrl?.takeIf { !it.isNull("local") }?.optString("local")
                val downloadUrl =
                    if (!localUrl.isNullOrBlank()) localUrl else downUrl?.optString("github")
                        .orEmpty()
                val sha256 = data?.optString("sha256").orEmpty()
                if (vCode <= 0 || downloadUrl.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "版本信息不完整", Toast.LENGTH_SHORT).show()
                    }
                    null
                } else {
                    ServerVersion(vName, vCode, downloadUrl, sha256)
                }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "$body ($code)", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    private fun getAppVersionCode(context: Context): Long {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val pi = pm.getPackageInfo(pkg, 0)
            @Suppress("DEPRECATION") if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    private fun getAppVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            pi.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun downloadToCache(
        context: Context,
        url: String,
        target: File,
        expectedSha256: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            target.parentFile?.mkdirs()
            val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "下载失败: ${resp.code}", Toast.LENGTH_SHORT).show()
                    }
                    return@use false
                }
                val body = resp.body
                val total = body.contentLength()
                onProgress?.let { cb ->
                    withContext(Dispatchers.Main) { cb(0L, total) }
                }
                FileOutputStream(target).use { fos ->
                    body.byteStream().use { ins ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = ins.read(buf)
                            if (read <= 0) break
                            fos.write(buf, 0, read)
                            downloaded += read
                            onProgress?.let { cb ->
                                withContext(Dispatchers.Main) { cb(downloaded, total) }
                            }
                        }
                        fos.flush()
                    }
                }
                // SHA256验证
                if (expectedSha256.isNotBlank()) {
                    val actual = computeSha256(target)
                    if (actual != expectedSha256) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "安装包验证失败，请重新下载", Toast.LENGTH_SHORT)
                                .show()
                        }
                        target.delete()
                        return@use false
                    }
                }
                true
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "下载异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private fun joinUrl(base: String, path: String): String {
        if (path.isEmpty()) return base

        return try {
            val baseUri = base.toUri()
            val pathSegments = path.split("/").filter { it.isNotEmpty() }

            val builder = baseUri.buildUpon()
            pathSegments.forEach { segment ->
                builder.appendPath(segment)
            }

            builder.build().toString()
        } catch (_: Exception) {
            val b = if (base.endsWith("/")) base.dropLast(1) else base
            val p = if (path.startsWith("/")) path else "/$path"
            b + p
        }
    }
}