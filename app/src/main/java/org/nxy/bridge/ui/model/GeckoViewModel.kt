package org.nxy.bridge.ui.model

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession


/**
 * 持有 GeckoRuntime 与 GeckoSession，支持在配置变更时复用。
 * Runtime 为应用级单例；Session 随 ViewModel 生命周期关闭。
 */
class GeckoViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "GeckoViewModel"
    }

    val runtime: GeckoRuntime by lazy { GeckoRuntimeHolder.get(app) }

    val session: GeckoSession by lazy { GeckoSession() }

    var urlLoaded: Boolean = false

    var opened = false
        private set

    private var compatibilityPreferenceResult: GeckoResult<Void>? = null

    fun open() {
        if (opened) return
        opened = true

        session.open(runtime)
        compatibilityPreferenceResult = applyGeckoWorkaroundPref()
    }

    /**
     * 兼容定制设备的网页视口行为，不写入 Gecko 用户配置。
     */
    @OptIn(ExperimentalGeckoViewApi::class)
    private fun applyGeckoWorkaroundPref(): GeckoResult<Void>? {
        val enabled = getApplication<Application>()
            .getSharedPreferences(PreferenceKeys.PREFS, Context.MODE_PRIVATE)
            .getBoolean(PreferenceKeys.KEY_LEGACY_VIEWPORT, false)
        if (!enabled) return null

        return GeckoPreferenceController.setGeckoPref(
            "dom.interactive_widget_default_resizes_visual",
            false,
            GeckoPreferenceController.PREF_BRANCH_DEFAULT
        )
    }

    fun loadUrl(url: String) {
        if (urlLoaded) return
        urlLoaded = true

        val loadUrl = { session.loadUri(url) }
        compatibilityPreferenceResult?.accept(
            { loadUrl() },
            { error ->
                Log.e(TAG, "Failed to apply legacy viewport preference", error)
                loadUrl()
            }
        ) ?: loadUrl()
    }

    override fun onCleared() {
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