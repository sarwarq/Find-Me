package com.example.findme.update

import org.json.JSONException
import org.json.JSONObject

/**
 * A published version: the `update.json` asset that the release workflow
 * (`.github/workflows/release.yml`) attaches to every GitHub Release.
 */
data class ReleaseInfo(
    val versionCode: Long,
    val versionName: String,
    /** Download link of this exact version's APK (not "latest", so it can't change mid-download). */
    val apkUrl: String,
    /** Lowercase hex SHA-256 of the APK; a broken download is never installed. */
    val sha256: String,
    /** APK size in bytes, 0 if unknown. */
    val size: Long,
) {
    companion object {
        /** Reads `update.json`. Throws [IllegalArgumentException] if it is not a valid release. */
        fun parse(json: String): ReleaseInfo {
            val o = try {
                JSONObject(json)
            } catch (e: JSONException) {
                throw IllegalArgumentException("update.json is not JSON", e)
            }
            fun string(key: String): String =
                o.optString(key).takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("update.json has no $key")

            val versionCode = o.optLong("versionCode", 0)
            require(versionCode > 0) { "update.json has no versionCode" }
            val apkUrl = string("apkUrl")
            require(apkUrl.startsWith("https://")) { "apkUrl must be https" }
            val sha256 = string("sha256").lowercase()
            require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) { "sha256 is not a SHA-256" }
            return ReleaseInfo(
                versionCode = versionCode,
                versionName = string("versionName"),
                apkUrl = apkUrl,
                sha256 = sha256,
                size = o.optLong("size", 0).coerceAtLeast(0),
            )
        }
    }
}
