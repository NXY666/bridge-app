package org.nxy.bridge.util

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

object ApkInstaller {

    /**
     * 返回值说明：
     * - true  : 已开始安装流程（静默提交 or 已拉起系统安装界面）
     * - false : 当前没有开始安装流程（例如未授权，已跳到设置页）
     */
    fun installApk(context: Context, file: File): Boolean {
        require(file.exists() && file.isFile) { "安装包不存在" }

        if (!hasInstallPermission(context)) {
            requestInstallPermission(context)
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            installSilentlyIfPossible(context, file)
        } else {
            // Android 7~11
            installWithSystemUi(context, file)
        }

        return true
    }

    fun hasInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Android 12+：尝试纯静默自更新
     * 失败时自动 fallback 到系统安装界面
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    private fun installSilentlyIfPossible(context: Context, file: File) {
        return try {
            val packageInstaller = context.packageManager.packageInstaller

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                // 只允许更新当前 app，禁止装成新包
//                setAppPackageName(context.packageName)

                // Android 12+ 请求免用户确认
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(
                        PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    )
                }
            }

            val sessionId = packageInstaller.createSession(params)

            packageInstaller.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val callbackIntent = Intent(context, ApkInstallResultReceiver::class.java).apply {
                    `package` = context.packageName
                }

                // 新 targetSdk 要求这里不能 immutable
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                session.commit(pendingIntent.intentSender)
            }
        } catch (_: Throwable) {
            // 某些机型 / 某些系统条件下静默不成立，直接退回系统安装 UI
            installWithSystemUi(context, file)
        }
    }

    /**
     * API 24~30 的 fallback
     * 以及 31+ 静默提交失败后的兜底
     */
    private fun installWithSystemUi(context: Context, file: File) {
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
    }
}

class ApkInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )

        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.e("ApkInstall", "安装结果回调: status=$status message=$message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }

                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmIntent != null) {
                    context.startActivity(confirmIntent)
                } else {
                    Log.e("ApkInstall", "缺少用户确认 Intent")
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // 安装成功
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // 用户取消安装
            }

            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                Toast.makeText(context, "安装被系统阻止", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                Toast.makeText(context, "签名有冲突", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Toast.makeText(context, "与设备不兼容", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_INVALID -> {
                val text = if (message?.startsWith("INSTALL_FAILED_VERSION_DOWNGRADE") == true) {
                    "存在更高版本的应用"
                } else {
                    "安装包无效"
                }
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Toast.makeText(context, "空间不足", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_TIMEOUT -> {
                Toast.makeText(context, message ?: "安装超时", Toast.LENGTH_LONG).show()
            }

            else -> {
                Toast.makeText(context, "安装失败", Toast.LENGTH_LONG).show()
            }
        }
    }
}