package com.app.youtubego.data.mapper
import com.app.youtubego.data.remote.ApiConstants
/** Menulis ulang URL relatif/instance menjadi URL same-origin proksi (setara helper prox() di web). */
object UrlRewriter {
    fun rewrite(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return when {
            url.startsWith(ApiConstants.INSTANCE_URL + "/") ->
                ApiConstants.BASE_URL.trimEnd('/') + url.removePrefix(ApiConstants.INSTANCE_URL)
            url.startsWith("/") -> ApiConstants.BASE_URL.trimEnd('/') + url
            else -> url
        }
    }
}
