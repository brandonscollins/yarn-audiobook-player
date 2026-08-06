package io.github.brandonscollins.yarn.data.plex

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.brandonscollins.yarn.data.local.MIGRATION_1_2
import io.github.brandonscollins.yarn.data.local.YarnDatabase
import io.github.brandonscollins.yarn.settings.PlexPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Stand-in base URL for server calls; [PlexInterceptor] swaps it for the chosen connection. */
const val PLACEHOLDER_URL = "https://placeholder.invalid"

/** The `uri` that `POST /playQueues` expects (CLAUDE.md gotcha #1). */
fun mediaItemUri(
    machineIdentifier: String,
    bookId: Int,
): String = "server://$machineIdentifier/com.plexapp.plugins.library/library/metadata/$bookId"

/** Plex responses are huge and we map a handful of fields out of them. */
private val plexJson = Json { ignoreUnknownKeys = true }

/** Retrofit/OkHttp wiring for the two Plex services. */
class PlexApi(prefs: PlexPrefs) {
    /**
     * The connection the race picked (CLAUDE.md gotcha #3). [PlexConnectionManager] owns writes;
     * seeded from prefs so a restart reuses the last known-good connection before re-racing.
     */
    @Volatile
    var serverUrl: String = prefs.chosenServerUri.ifEmpty { PLACEHOLDER_URL }

    private val sessionIdentifier = Random.nextInt(until = 10_000).toString()

    val loginService: PlexLoginService =
        retrofit(PlexInterceptor(prefs, sessionIdentifier, serverUrl = null))
            .create(PlexLoginService::class.java)

    val mediaService: PlexMediaService =
        retrofit(PlexInterceptor(prefs, sessionIdentifier) { serverUrl })
            .create(PlexMediaService::class.java)

    private fun retrofit(interceptor: PlexInterceptor): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl("$PLACEHOLDER_URL/")
            .client(client)
            .addConverterFactory(plexJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}

/**
 * Process-wide singletons. No DI framework (CLAUDE.md) — dependencies are constructor-passed,
 * but Room and OkHttp must not be duplicated, and WorkManager hands workers only a Context.
 */
object PlexGraph {
    private var prefs: PlexPrefs? = null
    private var db: YarnDatabase? = null
    private var api: PlexApi? = null
    private var connections: PlexConnectionManager? = null

    @Synchronized
    fun prefs(context: Context): PlexPrefs =
        prefs ?: PlexPrefs(context.applicationContext).also { prefs = it }

    @Synchronized
    fun db(context: Context): YarnDatabase =
        db ?: Room.databaseBuilder(context.applicationContext, YarnDatabase::class.java, "yarn.db")
            .addMigrations(MIGRATION_1_2)
            .build()
            .also { db = it }

    @Synchronized
    fun api(context: Context): PlexApi =
        api ?: PlexApi(prefs(context)).also { api = it }

    @Synchronized
    fun connections(context: Context): PlexConnectionManager =
        connections ?: PlexConnectionManager(prefs(context), api(context)).also { connections = it }

    /**
     * Sign-out. Clearing [PlexPrefs] alone left this object holding the old session — a stale
     * `serverUrl`, a connection manager that thought it had already raced — and the previous
     * account's books and positions still in Room. Call after the prefs are cleared.
     */
    suspend fun reset(context: Context) {
        val database = db(context)
        withContext(Dispatchers.IO) { database.clearAllTables() }
        synchronized(this) {
            api?.serverUrl = PLACEHOLDER_URL
            connections = null
        }
    }
}
