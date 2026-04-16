package org.nxy.bridge.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession


/**
 * 持有 GeckoRuntime 与 GeckoSession，支持在配置变更时复用。
 * Runtime 为应用级单例；Session 随 ViewModel 生命周期关闭。
 */
class GeckoViewModel(app: Application) : AndroidViewModel(app) {

    val runtime: GeckoRuntime by lazy { GeckoRuntimeHolder.get(app) }

    val session: GeckoSession by lazy { GeckoSession() }

    var urlLoaded: Boolean = false

    var opened = false
        private set

    fun open() {
        if (opened) return
        opened = true

        session.open(runtime)
    }

    fun loadUrl(url: String) {
        if (urlLoaded) return
        urlLoaded = true

        session.loadUri(url)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (session.isOpen) session.close()
        } catch (_: Exception) {
        }
    }
}

private object GeckoRuntimeHolder {
    @Volatile
    private var instance: GeckoRuntime? = null

    fun get(app: Application): GeckoRuntime {
        val cached = instance
        if (cached != null) return cached

        return synchronized(this) {
            instance ?: GeckoRuntime.create(app.applicationContext).also { instance = it }
        }
    }
}

const val PREFS = "org.nxy.bridge.prefs"
const val KEY_URL = "key_url"
const val KEY_LANDSCAPE = "key_landscape"
const val KEY_KEEP_SCREEN_ON = "key_keep_screen_on"
const val KEY_PARAMETERS = "key_parameters"
const val SETTINGS_PASSWORD = "1145"