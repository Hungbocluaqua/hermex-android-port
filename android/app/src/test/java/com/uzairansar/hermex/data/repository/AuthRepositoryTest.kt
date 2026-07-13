package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.network.CustomHeader
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.PersistentCookieJar
import com.uzairansar.hermex.data.secure.SecretStore
import com.uzairansar.hermex.data.secure.ServerRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun normalizeServerUrlDefaultsPrivateAndTailscaleHostsToHttp() {
        val cases = mapOf(
            "localhost:8787/chat?draft=1#composer" to "http://localhost:8787/",
            "127.0.0.1:3000" to "http://127.0.0.1:3000/",
            "10.0.2.2:3000" to "http://10.0.2.2:3000/",
            "172.16.4.20:3000" to "http://172.16.4.20:3000/",
            "192.168.1.50:9119" to "http://192.168.1.50:9119/",
            "100.64.0.0:9119" to "http://100.64.0.0:9119/",
            "100.96.12.34:9119" to "http://100.96.12.34:9119/",
            "100.127.255.255:9119" to "http://100.127.255.255:9119/",
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, AuthRepository.normalizeServerUrl(input).toString())
        }
    }

    @Test
    fun normalizeServerUrlDefaultsOtherSchemelessHostsToHttps() {
        val cases = mapOf(
            "example.test:9119" to "https://example.test:9119/",
            "100.63.255.255:9119" to "https://100.63.255.255:9119/",
            "100.128.0.0:9119" to "https://100.128.0.0:9119/",
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, AuthRepository.normalizeServerUrl(input).toString())
        }
    }

    @Test
    fun normalizeServerUrlPreservesExplicitSchemeAndDropsAccidentalWebuiWwwPrefix() {
        val cases = mapOf(
            "https://www.webui.example.test/chat?q=1#result" to "https://webui.example.test/",
            "www.webui.example.test" to "https://webui.example.test/",
            "HTTP://WWW.WEBUI.EXAMPLE.TEST:8080/path" to "http://webui.example.test:8080/",
            "http://example.test/path" to "http://example.test/",
            "https://localhost:8787/path" to "https://localhost:8787/",
            "https://100.96.12.34:9119/path" to "https://100.96.12.34:9119/",
            "https://www.example.com/path" to "https://www.example.com/",
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, AuthRepository.normalizeServerUrl(input).toString())
        }
    }

    @Test
    fun testConnectionUsesProbeHeadersWithoutRegisteringServer() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"status":"ok"}"""))
            server.enqueue(json("""{"auth_enabled":false,"password_auth_enabled":true}"""))
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            val repository = authRepository(registry, secretStore)

            repository.testConnection(
                server.url("/").toString(),
                listOf(CustomHeader("CF-Access-Client-Id", "id")),
            )

            assertTrue(registry.snapshot.value.servers.isEmpty())
            assertEquals("id", server.takeRequest().headers["CF-Access-Client-Id"])
            assertEquals("id", server.takeRequest().headers["CF-Access-Client-Id"])
        } finally {
            server.close()
        }
    }

    @Test
    fun addServerNeedsPasswordDoesNotRegisterUntilPasswordSucceeds() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"status":"ok"}"""))
            server.enqueue(json("""{"auth_enabled":true,"password_auth_enabled":true}"""))
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            val repository = authRepository(registry, secretStore)

            val result = repository.addServer(server.url("/").toString(), password = "")

            assertEquals(AddServerResult.NeedsPassword, result)
            assertTrue(registry.snapshot.value.servers.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun updateServerIdentityPersistsAndRefreshesLoggedInAccount() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"status":"ok"}"""))
            server.enqueue(json("""{"auth_enabled":false,"password_auth_enabled":true}"""))
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            val repository = authRepository(registry, secretStore)

            repository.configure(server.url("/").toString(), password = "")
            val account = registry.activeServer()
            assertNotNull(account)

            repository.updateServerIdentity(
                account = requireNotNull(account),
                displayName = "Mobile Lab",
                initials = "ML",
                headerLogoColorHex = "#34C759",
            )

            val updated = registry.activeServer()
            assertNotNull(updated)
            requireNotNull(updated)
            assertEquals("Mobile Lab", updated.displayName)
            assertEquals("ML", updated.initials)
            assertEquals("#34C759", updated.headerLogoColorHex)
            val state = repository.state.value as AuthState.LoggedIn
            assertEquals("Mobile Lab", state.account.displayName)
        } finally {
            server.close()
        }
    }

    @Test
    fun forgettingServerClearsItsCookiesAndRestoresUnconfiguredState() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"status":"ok"}"""))
            server.enqueue(json("""{"auth_enabled":false,"password_auth_enabled":true}"""))
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            val cookieJar = PersistentCookieJar(secretStore)
            val repository = authRepository(registry, secretStore, cookieJar)

            repository.configure(server.url("/").toString(), password = "")
            val account = requireNotNull(registry.activeServer())
            val url = server.url("/")
            cookieJar.saveFromResponse(
                url,
                listOf(
                    Cookie.Builder()
                        .name("session")
                        .value("token")
                        .hostOnlyDomain(url.host)
                        .path("/")
                        .build(),
                ),
            )
            assertTrue(cookieJar.loadForRequest(url).isNotEmpty())

            repository.forget(account.id)

            assertTrue(cookieJar.loadForRequest(url).isEmpty())
            assertTrue(registry.snapshot.value.servers.isEmpty())
            assertTrue(repository.state.value is AuthState.Unconfigured)
        } finally {
            server.close()
        }
    }

    @Test
    fun normalizeServerUrlRejectsPublicPlainHttp() {
        val error = runCatching { AuthRepository.normalizeServerUrl("http://example.com") }.exceptionOrNull()
        assertTrue(error is com.uzairansar.hermex.core.network.ApiError.InsecureTransport)
    }

    @Test
    fun restoredExpiredSessionLogsOutAndClearsServerCache() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"auth_enabled":true,"logged_in":false,"password_auth_enabled":true}"""))
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            registry.activate(server.url("/"))
            val cleared = mutableListOf<String>()
            val repository = authRepository(registry, secretStore, clearCachedServer = cleared::add)

            assertFalse(repository.validateRestoredSession())
            assertTrue(repository.state.value is AuthState.LoggedOut)
            assertEquals(listOf(server.url("/").toString()), cleared)
        } finally {
            server.close()
        }
    }

    @Test
    fun logoutClearsCookiesAndServerCache() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"ok":true}"""))
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            registry.activate(server.url("/"))
            val cleared = mutableListOf<String>()
            val repository = authRepository(registry, secretStore, clearCachedServer = cleared::add)

            repository.logout()

            assertTrue(repository.state.value is AuthState.LoggedOut)
            assertEquals(listOf(server.url("/").toString()), cleared)
        } finally {
            server.close()
        }
    }

    @Test
    fun runtimeUnauthorizedLogsOutOnlyMatchingActiveServer() = runBlocking {
        val activeServer = MockWebServer()
        val otherServer = MockWebServer()
        try {
            activeServer.start()
            otherServer.start()
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            registry.activate(activeServer.url("/"))
            val cleared = mutableListOf<String>()
            val repository = authRepository(registry, secretStore, clearCachedServer = { cleared += it })

            val generation = repository.currentAuthGeneration(activeServer.url("/"))
            repository.handleUnauthorized(otherServer.url("/"), generation)
            assertTrue(repository.state.value is AuthState.LoggedIn)

            repository.handleUnauthorized(activeServer.url("/"), generation)
            assertTrue(repository.state.value is AuthState.LoggedOut)
            assertEquals(listOf(activeServer.url("/").toString()), cleared)
        } finally {
            activeServer.close()
            otherServer.close()
        }
    }

    @Test
    fun logoutRemainsLoggedOutAfterRepositoryRecreation() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"ok":true}"""))
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            registry.activate(server.url("/"))
            val repository = authRepository(registry, secretStore)

            repository.logout()
            val restored = authRepository(ServerRegistry(secretStore), secretStore)

            assertTrue(restored.state.value is AuthState.LoggedOut)
        } finally {
            server.close()
        }
    }

    @Test
    fun staleUnauthorizedGenerationCannotLogOutReactivatedSession() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            val account = registry.activate(server.url("/"))
            val repository = authRepository(registry, secretStore)
            val staleGeneration = repository.currentAuthGeneration(server.url("/"))

            repository.activate(account.id)
            repository.handleUnauthorized(server.url("/"), staleGeneration)

            assertTrue(repository.state.value is AuthState.LoggedIn)
        } finally {
            server.close()
        }
    }

    @Test
    fun supersededLoginCannotLeaveItsCookieBehind() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"status":"ok"}"""))
            server.enqueue(json("""{"auth_enabled":true,"password_auth_enabled":true}"""))
            server.enqueue(
                json("""{"ok":true}""").newBuilder()
                    .addHeader("Set-Cookie", "session=obsolete; Path=/")
                    .bodyDelay(2, java.util.concurrent.TimeUnit.SECONDS)
                    .build(),
            )
            server.enqueue(json("""{"status":"ok"}"""))
            server.enqueue(json("""{"auth_enabled":true,"password_auth_enabled":true}"""))
            server.enqueue(
                json("""{"ok":true}""").newBuilder()
                    .addHeader("Set-Cookie", "session=current; Path=/")
                    .build(),
            )
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            val cookieJar = PersistentCookieJar(secretStore)
            val repository = authRepository(registry, secretStore, cookieJar)

            val obsolete = async(Dispatchers.IO) {
                runCatching { repository.configure(server.url("/").toString(), password = "old") }
            }
            repeat(3) { assertNotNull(server.takeRequest()) }
            val current = async(Dispatchers.IO) {
                repository.configure(server.url("/").toString(), password = "new")
            }

            assertTrue(obsolete.await().exceptionOrNull() is IllegalStateException)
            current.await()

            val sessionCookie = cookieJar.loadForRequest(server.url("/"))
                .single { it.name == "session" }
            assertEquals("current", sessionCookie.value)
            assertTrue(repository.state.value is AuthState.LoggedIn)
        } finally {
            server.close()
        }
    }

    @Test
    fun forgetSurfacesCacheDeletionFailureAndKeepsServerConfigured() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            val account = registry.activate(server.url("/"))
            val repository = authRepository(
                registry,
                secretStore,
                clearCachedServer = { throw IllegalStateException("cache unavailable") },
            )

            val error = runCatching { repository.forget(account.id) }.exceptionOrNull()

            assertEquals("cache unavailable", error?.message)
            assertNotNull(registry.snapshot.value.servers.firstOrNull { it.id == account.id })
            assertTrue(repository.state.value is AuthState.LoggedIn)
        } finally {
            server.close()
        }
    }

    @Test
    fun forgetFinishesCacheDeletionAfterCallerCancellation() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val secretStore = InMemorySecretStore()
            val registry = ServerRegistry(secretStore)
            val account = registry.activate(server.url("/"))
            val deletionStarted = CompletableDeferred<Unit>()
            val allowDeletion = CompletableDeferred<Unit>()
            var deletionFinished = false
            val repository = authRepository(
                registry,
                secretStore,
                clearCachedServer = {
                    deletionStarted.complete(Unit)
                    allowDeletion.await()
                    deletionFinished = true
                },
            )

            val forget = launch(Dispatchers.Default) { repository.forget(account.id) }
            deletionStarted.await()
            forget.cancel()
            allowDeletion.complete(Unit)
            forget.join()

            assertTrue(forget.isCancelled)
            assertTrue(deletionFinished)
            assertTrue(registry.snapshot.value.servers.isEmpty())
            assertTrue(repository.state.value is AuthState.Unconfigured)
        } finally {
            server.close()
        }
    }

    private fun authRepository(
        registry: ServerRegistry,
        secretStore: SecretStore,
        cookieJar: PersistentCookieJar = PersistentCookieJar(secretStore),
        clearCachedServer: suspend (String) -> Unit = {},
    ): AuthRepository {
        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
        return AuthRepository(
            registry = registry,
            clientFactory = { url ->
                HermesApiClient(
                    baseUrl = url,
                    client = okHttpClient,
                    customHeaders = { registry.customHeaders(ServerRegistry.normalizedId(url)) },
                )
            },
            probeClientFactory = { url, headers ->
                HermesApiClient(
                    baseUrl = url,
                    client = okHttpClient,
                    customHeaders = { headers },
                )
            },
            cookieJar = cookieJar,
            clearCachedServer = clearCachedServer,
        )
    }

    private fun json(body: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}

private class InMemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun clearPrefix(prefix: String) {
        values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
    }
}
