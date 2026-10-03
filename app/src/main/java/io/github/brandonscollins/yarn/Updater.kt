// Copied from the Brasco App Store's client/Updater.kt. Per-app changes: this package line, and UpdateBanner,
// which here wears Yarn's paper-and-ink theme (ADR-006) instead of the store copy's dark overlay.
package io.github.brandonscollins.yarn

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/*
 * Self-update from a latest.json on Brandon's server. Self-contained so it can be copied into another app:
 * it uses only context.packageName, and the caller passes the URL. Safe from API 29 up.
 *
 * latest.json: {"appId","name","versionCode","versionName","apk","sha256","size","signer","notes","published"};
 * apk is relative to latest.json's own URL. Android itself refuses an APK signed by a different key, so the
 * signer field is informational; the sha256 and size guard against a broken or swapped download.
 */

/** One published build. [apkUrl] is already resolved against latest.json's URL. */
data class Release(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String, val size: Long, val notes: String)

/** Parses latest.json, fetched from [jsonUrl], for app [appId]. Throws IllegalArgumentException saying what is wrong. */
fun parseRelease(json: String, jsonUrl: String, appId: String): Release {
  val o = runCatching { JSONObject(json) }.getOrElse { throw IllegalArgumentException("latest.json isn't valid JSON") }
  fun str(k: String): String {
    val v = o.opt(k)
    require(v is String && v.isNotBlank()) { "latest.json: \"$k\" must be a non-empty string" }
    return v
  }
  fun num(k: String): Long {
    val v = o.opt(k)
    require(v is Int || v is Long) { "latest.json: \"$k\" must be a whole number" }
    return (v as Number).toLong()
  }
  if (o.has("appId")) require(o.opt("appId") == appId) { "latest.json is for ${o.opt("appId")}, not $appId" }
  val code = num("versionCode")
  require(code > 0) { "latest.json: versionCode must be above 0" }
  val sha = str("sha256").lowercase()
  require(Regex("[0-9a-f]{64}").matches(sha)) { "latest.json: sha256 must be 64 hex characters" }
  val size = num("size")
  require(size > 0) { "latest.json: size must be above 0" }
  val path = str("apk").trim()
  val apk = runCatching { URI(jsonUrl).resolve(path) }.getOrElse { throw IllegalArgumentException("latest.json: apk \"$path\" isn't a valid address") }
  require(apk.scheme == "https" || apk.scheme == "http") { "latest.json: apk must be an http(s) address or a path next to latest.json" }
  return Release(code, str("versionName"), apk.toString(), sha, size, o.optString("notes", ""))
}

fun isNewer(installed: Long, release: Release) = release.versionCode > installed

/** Lowercase hex SHA-256 of everything in [input]. Closes it. */
fun sha256Hex(input: InputStream): String {
  val md = MessageDigest.getInstance("SHA-256")
  val buf = ByteArray(64 * 1024)
  input.use { while (true) { val n = it.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
  return hex(md.digest())
}

fun sha256Hex(file: File) = sha256Hex(file.inputStream())

private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

/** The result of one check. [latest] is what the server publishes; [available] says it is newer than what's installed. */
data class Checked(val installedCode: Long, val installedName: String, val latest: Release?, val available: Boolean, val error: String?) {
  fun toJson(): JSONObject = JSONObject()
    .put("ok", error == null)
    .put("installed", JSONObject().put("versionCode", installedCode).put("versionName", installedName))
    .put("latest", latest?.let {
      JSONObject().put("versionCode", it.versionCode).put("versionName", it.versionName).put("apk", it.apkUrl)
        .put("sha256", it.sha256).put("size", it.size).put("notes", it.notes)
    } ?: JSONObject.NULL)
    .put("available", available)
    .put("error", error ?: JSONObject.NULL)
}

object Updater {
  /** The release the banner offers, or null. "Later" clears it until a newer versionCode appears. */
  var offer by mutableStateOf<Release?>(null)
    private set
  /** A short line under the banner: progress, or what went wrong, in plain words. */
  var status by mutableStateOf<String?>(null)
  var busy by mutableStateOf(false)
    private set

  private val scope = MainScope()
  private val checking = Mutex()
  private const val EVERY = 6 * 3_600_000L

  private fun prefs(context: Context) = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

  fun installed(context: Context): Pair<Long, String> {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return info.longVersionCode to (info.versionName ?: "")
  }

  /** On resume: checks if [enabled] and the last good check was over 6 hours ago (or never). */
  fun maybeCheck(context: Context, url: String, enabled: Boolean) {
    if (!enabled || System.currentTimeMillis() - prefs(context).getLong("lastCheck", 0) < EVERY) return
    if (!checking.tryLock()) return
    scope.launch {
      try { check(context, url) } finally { checking.unlock() }
    }
  }

  /** Fetches latest.json now. Never throws (except cancellation): problems come back in [Checked.error]. */
  suspend fun check(context: Context, url: String): Checked {
    val (code, name) = installed(context)
    return try {
      val release = withTimeout(25_000) { withContext(Dispatchers.IO) { parseRelease(get(url), url, context.packageName) } }
      val newer = isNewer(code, release)
      val p = prefs(context)
      p.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
      offer = release.takeIf { newer && it.versionCode > p.getLong("dismissed", 0) }
      Checked(code, name, release, newer, null)
    } catch (e: CancellationException) {
      if (e is kotlinx.coroutines.TimeoutCancellationException) Checked(code, name, null, false, "The update server didn't answer in time") else throw e
    } catch (e: Exception) {
      Checked(code, name, null, false, plain(e))
    }
  }

  /** "Later": hide this release (and any older one) until a newer versionCode appears. */
  fun later(context: Context, release: Release) {
    prefs(context).edit().putLong("dismissed", release.versionCode).apply()
    offer = null
    status = null
  }

  /** "Install": runs [install] and shows its progress in [status]. */
  fun startInstall(context: Context, release: Release) {
    if (busy) return
    busy = true
    scope.launch {
      try {
        status = install(context, release)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        status = plain(e)
      } finally {
        busy = false
      }
    }
  }

  /**
   * Downloads, verifies and hands the APK to Android's installer, which shows its own "Update this app?" dialog.
   * Returns the line to show; the final result arrives in [UpdateReceiver].
   */
  suspend fun install(context: Context, release: Release): String {
    if (!context.packageManager.canRequestPackageInstalls()) {
      context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      return "Allow installs from ${label(context)}, then tap Install again"
    }
    status = "Downloading…"
    val file = File(context.cacheDir, "update.apk")
    withContext(Dispatchers.IO) {
      download(release, file)
      status = "Installing…"
      val installer = context.packageManager.packageInstaller
      val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
        setSize(file.length())
      }
      val id = installer.createSession(params)
      installer.openSession(id).use { s ->
        s.openWrite("base.apk", 0, file.length()).use { out ->
          file.inputStream().use { it.copyTo(out) }
          s.fsync(out)
        }
        // Mutable: the installer fills in the status extras. Explicit intent to our own non-exported receiver.
        // FLAG_MUTABLE exists from API 31; below that every PendingIntent is already mutable.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val done = PendingIntent.getBroadcast(context, id, Intent(context, UpdateReceiver::class.java).setPackage(context.packageName),
          PendingIntent.FLAG_UPDATE_CURRENT or mutable)
        s.commit(done.intentSender)
      }
      file.delete()
    }
    return "Waiting for Android to confirm…"
  }

  /** Streams the APK to [file], checking size and SHA-256. A bad download is deleted, never installed. */
  private fun download(release: Release, file: File) {
    val c = URL(release.apkUrl).openConnection() as HttpURLConnection
    try {
      c.connectTimeout = 10_000
      c.readTimeout = 30_000
      if (c.responseCode != 200) throw IOException("The update server said ${c.responseCode} for the app file")
      val md = MessageDigest.getInstance("SHA-256")
      val got = DigestInputStream(c.inputStream, md).use { input -> file.outputStream().use { input.copyTo(it) } }
      val sha = hex(md.digest())
      if (got != release.size || sha != release.sha256) {
        file.delete()
        throw IOException(if (got != release.size) "The download was the wrong size ($got bytes, expected ${release.size}); try again" else "The download didn't match its checksum; try again")
      }
    } catch (e: Exception) {
      file.delete()
      throw e
    } finally {
      c.disconnect()
    }
  }

  private fun get(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
      c.connectTimeout = 10_000
      c.readTimeout = 10_000
      c.setRequestProperty("Accept", "application/json")
      c.useCaches = false
      if (c.responseCode != 200) throw IOException("The update server said ${c.responseCode}")
      return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
      c.disconnect()
    }
  }

  private fun plain(e: Exception): String = when (e) {
    is java.net.UnknownHostException -> "Can't find the update server. Is Tailscale on?"
    is java.net.SocketTimeoutException -> "The update server didn't answer in time"
    is java.net.ConnectException -> "Can't reach the update server"
    is javax.net.ssl.SSLException -> "Couldn't make a secure connection to the update server"
    is IllegalArgumentException, is IOException -> e.message ?: "Update failed"
    else -> "Update failed: ${e.message ?: e.javaClass.simpleName}"
  }

  fun label(context: Context) = context.applicationInfo.loadLabel(context.packageManager).toString()
}

/** PackageInstaller reports here: asks the user to confirm, or says how it ended. */
class UpdateReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
      PackageInstaller.STATUS_PENDING_USER_ACTION ->
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
      PackageInstaller.STATUS_SUCCESS -> Updater.status = "Updated" // usually unseen: Android restarts the app
      PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.status = "Update cancelled. Tap Install to try again"
      PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
        Updater.status = "This update is signed with a different key, so Android won't install it over this copy"
      PackageInstaller.STATUS_FAILURE_STORAGE -> Updater.status = "Not enough space to install the update"
      else -> Updater.status = "Install failed (${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $code"})"
    }
  }
}

/**
 * A card at the top of Home while an update is on offer, styled like Home's own cards. Gold is a fill only
 * (the Install button), small text stays ink/cream, per ADR-006.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
  val release = Updater.offer ?: return
  val context = LocalContext.current
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("${Updater.label(context)} ${release.versionName} is ready", style = MaterialTheme.typography.titleMedium)
        Updater.status?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      TextButton(
        onClick = { Updater.later(context, release) },
        enabled = !Updater.busy,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
      ) { Text("Later") }
      Button(onClick = { Updater.startInstall(context, release) }, enabled = !Updater.busy) { Text("Install") }
    }
  }
}
