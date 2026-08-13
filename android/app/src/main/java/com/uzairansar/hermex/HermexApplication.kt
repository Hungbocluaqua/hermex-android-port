package com.uzairansar.hermex

import android.app.Application
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.HttpKanbanEventStreamingClient
import com.uzairansar.hermex.core.network.PersistentCookieJar
import com.uzairansar.hermex.core.network.SseStreamClient
import com.uzairansar.hermex.data.db.HermexDatabase
import com.uzairansar.hermex.data.db.ServerCacheOwnership
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.CacheMaintenanceRepository
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.repository.GitRepository
import com.uzairansar.hermex.data.repository.KanbanRepository
import com.uzairansar.hermex.data.repository.PanelsRepository
import com.uzairansar.hermex.data.repository.SessionRepository
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import com.uzairansar.hermex.data.secure.AndroidSecretStore
import com.uzairansar.hermex.data.secure.ServerRegistry
import com.uzairansar.hermex.data.secure.UnavailableSecretStore
import com.uzairansar.hermex.data.share.SharedDraftStore
import com.uzairansar.hermex.ui.chat.StreamRecoveryService
import com.uzairansar.hermex.ui.createExportDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class HermexApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppVisibilityTracker.register(this)
        container = AppContainer(this)
    }

    suspend fun resetSecureStorage(): Result<Unit> {
        val replacement = withContext(Dispatchers.IO) {
            runCatching {
                AndroidSecretStore.reset(this@HermexApplication)
                AppContainer(this@HermexApplication)
            }
        }
        replacement.onSuccess { container = it }
        return replacement.map { }
    }
}

class AppContainer(private val application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secretStoreResult = runCatching { AndroidSecretStore(application) }
    private val secretStore = secretStoreResult.getOrElse(::UnavailableSecretStore)
    val registry = ServerRegistry(secretStore)
    val secureStorageFailure: Throwable? = secretStoreResult.exceptionOrNull() ?: registry.loadFailure
    val localSettingsRepository = LocalSettingsRepository(application)
    val sharedDraftStore = SharedDraftStore(application)
    private val cookieJar = PersistentCookieJar(secretStore)
    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val database = HermexDatabase.create(application)
    private val cacheOwnership = ServerCacheOwnership()
    val cacheMaintenanceRepository = CacheMaintenanceRepository(database.cacheDao(), cacheOwnership)

    val authRepository = AuthRepository(
        registry = registry,
        clientFactory = ::apiClient,
        probeClientFactory = { baseUrl, headers ->
            HermesApiClient(
                baseUrl = baseUrl,
                client = okHttpClient,
                customHeaders = { headers },
            )
        },
        cookieJar = cookieJar,
        clearCachedServer = { serverUrl ->
            StreamRecoveryService.clearServer(application, serverUrl)
            cacheOwnership.invalidateAndClear(serverUrl) { database.cacheDao().clearServer(serverUrl) }
        },
    )

    fun apiClient(baseUrl: HttpUrl): HermesApiClient {
        val authGeneration = authRepository.currentAuthGeneration(baseUrl)
        return HermesApiClient(
            baseUrl = baseUrl,
            client = okHttpClient,
            customHeaders = {
                val serverId = ServerRegistry.normalizedId(baseUrl)
                registry.customHeaders(serverId)
            },
            onUnauthorized = { server ->
                applicationScope.launch { authRepository.handleUnauthorized(server, authGeneration) }
            },
            onProfileChanged = { server, _ ->
                val serverUrl = server.toString()
                cacheOwnership.invalidateAndClear(serverUrl) { database.cacheDao().clearServer(serverUrl) }
            },
        )
    }

    fun sessionRepository(baseUrl: HttpUrl): SessionRepository =
        SessionRepository(
            apiClient(baseUrl),
            database.cacheDao(),
            cacheOwnership,
            exportDirectoryProvider = { application.createExportDirectory("session") },
        )

    fun chatRepository(baseUrl: HttpUrl): ChatRepository {
        val client = apiClient(baseUrl)
        val authGeneration = authRepository.currentAuthGeneration(baseUrl)
        return ChatRepository(
            client = client,
            cacheDao = database.cacheDao(),
            cacheOwnership = cacheOwnership,
            sse = SseStreamClient(
                baseUrl = baseUrl,
                client = okHttpClient,
                onUnauthorized = { server ->
                    applicationScope.launch { authRepository.handleUnauthorized(server, authGeneration) }
                },
                customHeaders = {
                    registry.customHeaders(ServerRegistry.normalizedId(baseUrl))
                },
            ),
        )
    }

    fun panelsRepository(baseUrl: HttpUrl): PanelsRepository = PanelsRepository(apiClient(baseUrl))
    fun workspaceRepository(baseUrl: HttpUrl): WorkspaceRepository = WorkspaceRepository(apiClient(baseUrl))
    fun kanbanRepository(baseUrl: HttpUrl): KanbanRepository {
        val authGeneration = authRepository.currentAuthGeneration(baseUrl)
        return KanbanRepository(
            client = apiClient(baseUrl),
            streamingClient = HttpKanbanEventStreamingClient(
                baseUrl = baseUrl,
                client = okHttpClient,
                onUnauthorized = { server ->
                    applicationScope.launch { authRepository.handleUnauthorized(server, authGeneration) }
                },
                customHeaders = {
                    registry.customHeaders(ServerRegistry.normalizedId(baseUrl))
                },
            ),
        )
    }
    fun gitRepository(baseUrl: HttpUrl): GitRepository = GitRepository(apiClient(baseUrl))
}
