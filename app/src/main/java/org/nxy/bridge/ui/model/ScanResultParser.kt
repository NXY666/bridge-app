package org.nxy.bridge.ui.model

import androidx.core.net.toUri
import org.json.JSONObject

/**
 * 扫码结果解析：接受 http/https URL 文本，或包含 url 与可选开关的 JSON 对象。
 */
object ScanResultParser {

    /** 解析成功的扫码配置 */
    data class ParsedScan(
        val url: String,
        val parameters: Map<String, String>,
        val landscape: Boolean?,
        val keepScreenOn: Boolean?,
        val disableBack: Boolean?
    )

    // JSON 模式下的字段键
    private const val KEY_URL = "url"
    private const val KEY_LANDSCAPE = "landscape"
    private const val KEY_KEEP_SCREEN_ON = "keepScreenOn"
    private const val KEY_DISABLE_BACK = "disableBack"
    private const val KEY_PARAMETERS = "parameters"

    /**
     * 解析扫码文本，无效时返回 null。
     * - JSON 对象：必填字符串 url；三个开关可选但存在时必须为布尔值；
     *   显式含 parameters 字段视为无效，其余额外字段忽略。
     * - http/https URL：移除用户信息与 fragment，保留路径与端口；
     *   查询参数解码为 Map，同名取末值（包括空值）。
     */
    fun parse(text: String): ParsedScan? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith('{')) {
            return parseJson(trimmed)
        }
        return parseUrl(trimmed, null, null, null)
    }

    /**
     * 解析 JSON 对象形式的扫码配置。
     */
    private fun parseJson(json: String): ParsedScan? {
        val obj = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }

        // 显式携带 parameters 字段的 JSON 无效
        if (obj.has(KEY_PARAMETERS)) return null

        // url 必须是字符串
        val urlValue = obj.opt(KEY_URL)
        if (urlValue !is String || urlValue.isEmpty()) return null

        // 开关字段缺省合法；存在时必须是布尔值，显式 null 或其他类型均无效
        fun hasValidOptionalBoolean(key: String): Boolean =
            !obj.has(key) || obj.get(key) is Boolean

        if (!hasValidOptionalBoolean(KEY_LANDSCAPE) ||
            !hasValidOptionalBoolean(KEY_KEEP_SCREEN_ON) ||
            !hasValidOptionalBoolean(KEY_DISABLE_BACK)
        ) return null

        val landscape = obj.opt(KEY_LANDSCAPE) as? Boolean
        val keepScreenOn = obj.opt(KEY_KEEP_SCREEN_ON) as? Boolean
        val disableBack = obj.opt(KEY_DISABLE_BACK) as? Boolean

        return parseUrl(urlValue, landscape, keepScreenOn, disableBack)
    }

    /**
     * 校验并规范化 URL：scheme 限 http/https，必须有 host，显式端口必须有效；
     * 移除用户信息与 fragment，保留路径；无端口遵循 http 80 / https 443 默认语义。
     */
    private fun parseUrl(
        input: String,
        landscape: Boolean?,
        keepScreenOn: Boolean?,
        disableBack: Boolean?
    ): ParsedScan? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val uri = try {
            trimmed.toUri()
        } catch (_: Exception) {
            return null
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null

        // 保留编码形式的 host:port，剔除用户信息
        val authority = uri.encodedAuthority ?: return null
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isEmpty()) return null

        // 显式端口必须是 1-65535 的数字；IPv6 字面量仅认 ] 之后的端口
        val portStart = if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null
            hostPort.indexOf(':', close)
        } else {
            hostPort.lastIndexOf(':')
        }
        if (portStart >= 0) {
            val portText = hostPort.substring(portStart + 1)
            val port = portText.toIntOrNull()
            if (port == null || port !in 1..65535) return null
        }

        // 查询参数解码为 Map，同名取末值（包括空值）
        val parameters = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { name ->
            parameters[name] = uri.getQueryParameters(name).lastOrNull().orEmpty()
        }

        // 重建无用户信息、无 fragment 的 URL，端口保持原样不强制补写
        val clean = uri.buildUpon()
            .encodedAuthority(hostPort)
            .clearQuery()
            .fragment(null)
            .build()

        return ParsedScan(
            url = clean.toString(),
            parameters = parameters,
            landscape = landscape,
            keepScreenOn = keepScreenOn,
            disableBack = disableBack
        )
    }
}
