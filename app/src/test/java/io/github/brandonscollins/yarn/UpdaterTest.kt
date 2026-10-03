package io.github.brandonscollins.yarn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure half of the store updater: latest.json parsing, version comparison, checksums. */
class UpdaterTest {
  private val app = "io.github.brandonscollins.yarn"
  private val jsonUrl = "https://example.ts.net:8443/apps/io.github.brandonscollins.yarn/latest.json"
  private val sha = "a".repeat(64)

  private fun json(vararg override: Pair<String, Any?>): String {
    val m = linkedMapOf<String, Any?>("appId" to app, "versionCode" to 10400, "versionName" to "1.4", "apk" to "io.github.brandonscollins.yarn-10400.apk",
      "sha256" to sha, "size" to 1234567, "notes" to "text")
    override.forEach { (k, v) -> if (v == null) m.remove(k) else m[k] = v }
    return org.json.JSONObject(m as Map<*, *>).toString()
  }

  private fun error(text: String) = runCatching { parseRelease(text, jsonUrl, app) }.exceptionOrNull()?.message.orEmpty()

  @Test fun parsesAGoodRelease_andResolvesARelativeApk() =
    assertEquals(
      Release(10400, "1.4", "https://example.ts.net:8443/apps/io.github.brandonscollins.yarn/io.github.brandonscollins.yarn-10400.apk", sha, 1234567, "text"),
      parseRelease(json(), jsonUrl, app),
    )

  @Test fun keepsAnAbsoluteApkUrl() =
    assertEquals("https://cdn.example.com/x.apk", parseRelease(json("apk" to "https://cdn.example.com/x.apk"), jsonUrl, app).apkUrl)

  @Test fun notesAreOptional_andShaIsLowercased() {
    val r = parseRelease(json("notes" to null, "sha256" to "AB".repeat(32)), jsonUrl, app)
    assertEquals("", r.notes)
    assertEquals("ab".repeat(32), r.sha256)
  }

  @Test fun rejectsMissingKeys() {
    for (k in listOf("versionCode", "versionName", "apk", "sha256", "size")) {
      val e = error(json(k to null))
      assertTrue("$k: $e", e.contains("\"$k\""))
    }
  }

  @Test fun rejectsBadTypes() {
    assertTrue(error(json("versionCode" to "42")).contains("whole number"))
    assertTrue(error(json("size" to 1.5)).contains("whole number"))
    assertTrue(error(json("versionName" to 3)).contains("non-empty string"))
    assertTrue(error(json("sha256" to "nothex")).contains("64 hex"))
    assertTrue(error("not json").contains("isn't valid JSON"))
  }

  @Test fun rejectsAnotherAppsRelease() = assertTrue(error(json("appId" to "com.other")).contains("com.other"))

  @Test fun rejectsANonHttpApk() = assertTrue(error(json("apk" to "file:///sdcard/x.apk")).contains("http(s)"))

  @Test fun isNewerComparesVersionCodes() {
    val r = parseRelease(json(), jsonUrl, app)
    assertTrue(isNewer(10300, r))
    assertFalse(isNewer(10400, r))
    assertFalse(isNewer(10401, r))
  }

  @Test fun sha256OfKnownBytes() {
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex("".byteInputStream()))
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc".byteInputStream()))
  }
}
