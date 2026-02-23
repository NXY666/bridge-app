package org.nxy.bridge.ui.activity

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.nxy.bridge.ui.model.GeckoViewModel
import org.nxy.bridge.ui.model.KEY_LANDSCAPE
import org.nxy.bridge.ui.model.KEY_URL
import org.nxy.bridge.ui.model.PREFS
import org.nxy.bridge.ui.theme.BridgeTheme

/**
 * 全屏承载 GeckoView，按偏好锁定方向并隐藏系统栏。
 */
class BrowserActivity : ComponentActivity() {

    private var geckoView: GeckoView? = null
    private val geckoVM: GeckoViewModel by viewModels()

    interface AndroidPermissionRequester {
        fun request(permissions: Array<String>, onResult: (allGranted: Boolean) -> Unit)
        fun hasAll(permissions: Array<String>): Boolean
    }

    private val requester: AndroidPermissionRequester by lazy {
        object : AndroidPermissionRequester {
            private var pending: ((Boolean) -> Unit)? = null

            private val launcher =
                registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
                    val ok = res.values.all { it }
                    pending?.invoke(ok)
                    pending = null
                }

            override fun request(
                permissions: Array<String>,
                onResult: (allGranted: Boolean) -> Unit
            ) {
                pending = onResult
                launcher.launch(permissions)
            }

            override fun hasAll(permissions: Array<String>): Boolean {
                return permissions.all { p ->
                    checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
        }
    }

    class AutoGrantPermissionDelegate(
        private val requester: AndroidPermissionRequester
    ) : GeckoSession.PermissionDelegate {

        // 处理 Android runtime permission（系统层）
        override fun onAndroidPermissionsRequest(
            session: GeckoSession,
            permissions: Array<String>?,
            callback: GeckoSession.PermissionDelegate.Callback
        ) {
            val perms = permissions ?: emptyArray()
            if (perms.isEmpty() || requester.hasAll(perms)) {
                callback.grant()
                return
            }
            requester.request(perms) { ok ->
                if (ok) callback.grant() else callback.reject()
            }
        }

        // 处理 autoplay（站点内容权限层，新签名：返回 GeckoResult<Int>）
        override fun onContentPermissionRequest(
            session: GeckoSession,
            perm: GeckoSession.PermissionDelegate.ContentPermission
        ): GeckoResult<Int>? {
            return when (perm.permission) {
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ->
                    GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)

                else -> null // 其它权限不加壳层 UI，走默认
            }
        }

        // 处理 getUserMedia（摄像头/麦克风）
        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback
        ) {
            val chosenVideo = video?.firstOrNull()
            val chosenAudio = audio?.firstOrNull()

            val need = buildList {
                if (chosenVideo != null) add(Manifest.permission.CAMERA)
                if (chosenAudio != null) add(Manifest.permission.RECORD_AUDIO)
            }.toTypedArray()

            if (need.isEmpty()) {
                callback.reject()
                return
            }

            if (requester.hasAll(need)) {
                callback.grant(chosenVideo, chosenAudio)
                return
            }

            requester.request(need) { ok ->
                if (ok) callback.grant(chosenVideo, chosenAudio) else callback.reject()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        geckoVM.session.permissionDelegate = AutoGrantPermissionDelegate(requester)
        geckoVM.open()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val landscape = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_LANDSCAPE, true)
        requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        onBackPressedDispatcher.addCallback(this, true) {
            // 特殊处理：禁用返回键
        }

        setContent {
            BridgeTheme(dynamicColor = false) {
                AndroidView(
                    factory = { ctx ->
                        geckoView ?: GeckoView(ctx).also { view ->
                            geckoView = view
                            view.setSession(geckoVM.session)
                                val url = intent.getStringExtra(KEY_URL)
                                    ?: getSavedUrl(this@BrowserActivity)
                                if (!url.isNullOrEmpty()) {
                                    geckoVM.loadUrl(url)
                                }
                        }
                    }
                )
            }
        }
    }

    private fun getSavedUrl(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE)
        return prefs.getString(KEY_URL, null)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}