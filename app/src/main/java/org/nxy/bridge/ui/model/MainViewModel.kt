package org.nxy.bridge.ui.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.nxy.bridge.App


class MainViewModel : ViewModel() {

    private var _url by mutableStateOf(loadSavedUrl())
    var url: String
        get() = _url
        set(value) {
            val value = normalizeUrl(value)
            _url = value
            saveUrlToPrefs(value)
        }

    private var _landscape by mutableStateOf(loadLandscapeEnabled())
    var landscape: Boolean
        get() = _landscape
        set(value) {
            _landscape = value
            saveLandscapeToPrefs(value)
        }

    private var _parameters by mutableStateOf(loadSavedParameters())
    var parameters: Map<String, String>
        get() = _parameters
        set(value) {
            _parameters = value.toMap()
            saveParametersToPrefs(value)
        }

    private var _apis by mutableStateOf(loadSavedApis())
    var apis: ApiConfig
        get() = _apis
        set(value) {
            _apis = value
            saveApisToPrefs(value)
        }

    private fun loadSavedUrl(): String {
        val prefs = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_URL, "") ?: ""
    }

    private fun loadLandscapeEnabled(): Boolean {
        val prefs = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LANDSCAPE, true)
    }

    private fun loadSavedParameters(): Map<String, String> {
        val prefs = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PARAMETERS, "{}") ?: "{}"
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val result: Map<String, String>? = Gson().fromJson(json, type)
            result ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveUrlToPrefs(url: String?) {
        val prefs = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_URL, url) }
    }

    private fun saveLandscapeToPrefs(enabled: Boolean) {
        val prefs = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_LANDSCAPE, enabled) }
    }

    private fun saveParametersToPrefs(parameters: Map<String, String>) {
        val prefs = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = Gson().toJson(parameters)
        prefs.edit { putString(KEY_PARAMETERS, json) }
    }

    private fun loadSavedApis(): ApiConfig {
        val prefs = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_APIS, "{}" ) ?: "{}"
        return try {
            Gson().fromJson(json, ApiConfig::class.java) ?: ApiConfig()
        } catch (_: Exception) {
            ApiConfig()
        }
    }

    private fun saveApisToPrefs(apis: ApiConfig) {
        val prefs = App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = Gson().toJson(apis)
        prefs.edit { putString(KEY_APIS, json) }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed

        return try {
            var urlString = trimmed
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "http://$urlString"
            }

            val uri = urlString.toUri()
            val builder = uri.buildUpon()

            val path = uri.path
            if (!path.isNullOrEmpty() && path.endsWith("/") && path.length > 1) {
                builder.path(path.removeSuffix("/"))
            }

            builder.build().toString()
        } catch (_: Exception) {
            trimmed.removeSuffix("/")
        }
    }

    fun getUrlWithParameters(): String {
        val baseUrl = url
        if (baseUrl.isEmpty()) return baseUrl

        return try {
            val uri = baseUrl.toUri()
            val builder = uri.buildUpon()

            parameters.filter { it.key.isNotBlank() }.forEach { (key, value) ->
                builder.appendQueryParameter(key, value)
            }

            builder.build().toString()
        } catch (_: Exception) {
            baseUrl
        }
    }

    fun addParameter(key: String, value: String) {
        if (key.isBlank()) return
        parameters = parameters + (key to value)
    }

    fun removeParameter(key: String) {
        parameters = parameters - key
    }

    fun updateParameter(oldKey: String, newKey: String, value: String) {
        if (newKey.isBlank()) {
            removeParameter(oldKey)
            return
        }

        val newParams = parameters.toMutableMap()
        newParams.remove(oldKey)
        newParams[newKey] = value
        parameters = newParams
    }
}