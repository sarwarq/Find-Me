package com.example.findme.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseInfoTest {

    private val sha = "3f".repeat(32)

    private fun json(
        versionCode: Any? = 10203,
        versionName: String? = "1.2.3",
        apkUrl: String? = "https://github.com/owner/repo/releases/download/v1.2.3/findme.apk",
        sha256: String? = sha,
        size: Any? = 52_428_800,
    ): String = buildList {
        versionCode?.let { add("\"versionCode\": $it") }
        versionName?.let { add("\"versionName\": \"$it\"") }
        apkUrl?.let { add("\"apkUrl\": \"$it\"") }
        sha256?.let { add("\"sha256\": \"$it\"") }
        size?.let { add("\"size\": $it") }
    }.joinToString(", ", "{", "}")

    @Test
    fun readsWhatTheWorkflowWrites() {
        val r = ReleaseInfo.parse(json())
        assertEquals(10203L, r.versionCode)
        assertEquals("1.2.3", r.versionName)
        assertEquals("https://github.com/owner/repo/releases/download/v1.2.3/findme.apk", r.apkUrl)
        assertEquals(sha, r.sha256)
        assertEquals(52_428_800L, r.size)
    }

    @Test
    fun shaIsCompared_inLowercase() {
        assertEquals(sha, ReleaseInfo.parse(json(sha256 = sha.uppercase())).sha256)
    }

    @Test
    fun sizeIsOptional() {
        assertEquals(0L, ReleaseInfo.parse(json(size = null)).size)
        assertEquals(0L, ReleaseInfo.parse(json(size = -5)).size)
    }

    @Test
    fun rejectsBrokenFiles() {
        listOf(
            "not json",
            "<html>Not Found</html>",
            json(versionCode = null),
            json(versionCode = 0),
            json(versionName = null),
            json(versionName = ""),
            json(apkUrl = null),
            json(apkUrl = "http://github.com/owner/repo/findme.apk"),
            json(sha256 = null),
            json(sha256 = "abc"),
            json(sha256 = "z".repeat(64)),
        ).forEach { text ->
            assertThrows(text, IllegalArgumentException::class.java) { ReleaseInfo.parse(text) }
        }
    }
}
