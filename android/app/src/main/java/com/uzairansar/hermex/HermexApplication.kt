package com.uzairansar.hermex

import android.app.Application
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.PersistentCookieJar
import com.uzairansar.hermex.core.network.SseStreamClient
import com.uzairansar.hermex.data.db.HermexDatabase
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.repository.GitRepository
import com.uzairansar.hermex.data.repository.PanelsRepository
import com.uzairansar.hermex.data.repository.SessionRepository
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import com.uzairansar.hermex.data.secure.AndroidSecretStore
import com.uzairansar.hermex.data.secure.ServerRegistry
import com.uzairansar.hermex.data.share.SharedDraftStore
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class HermexApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val secretStore = AndroidSecretStore(application)
    val registry = ServerRegistry(secretStore)
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

    val authRepository = AuthRepository(
        registry = registry,
        clientFactory = ::apiClient,
        cookieJar = cookieJar,
    )

    fun apiClient(baseUrl: HttpUrl): HermesApiClient = HermesApiClient(
        baseUrl = baseUrl,
        client = okHttpClient,
        customHeaders = {
            val serverId = ServerRegistry.normalizedId(baseUrl)
            registry.customHeaders(serverId)
        },
    )

    fun sessionRepository(baseUrl: HttpUrl): SessionRepository =
        SessionRepository(apiClient(baseUrl), database.cacheDao())

    fun chatRepository(baseUrl: HttpUrl): ChatRepository {
        val client = apiClient(baseUrl)
        return ChatRepository(
            client = client,
            cacheDao = database.cacheDao(),
            sse = SseStreamClient(okHttpClient) {
                registry.customHeaders(ServerRegistry.normalizedId(baseUrl))
            },
        )
    }

    fun panelsRepository(baseUrl: HttpUrl): PanelsRepository = PanelsRepository(apiClient(baseUrl))
    fun workspaceRepository(baseUrl: HttpUrl): WorkspaceRepository = WorkspaceRepository(apiClient(baseUrl))
    fun gitRepository(baseUrl: HttpUrl): GitRepository = GitRepository(apiClient(baseUrl))
}
