package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.network.CustomHeader
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.PersistentCookieJar
import com.uzairansar.hermex.data.secure.SecretStore
import com.uzairansar.hermex.data.secure.ServerRegistry
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun normalizeServerUrlDefaultsLocalAndTailscaleHostsToHttp() {
        val cases = mapOf(
            "localhost:8787/chat?draft=1#composer" to "http://localhost:8787/",
            "127.0.0.1:3000" to "http://127.0.0.1:3000/",
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
            "192.168.1.50:9119" to "https://192.168.1.50:9119/",
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

    private fun authRepository(
        registry: ServerRegistry,
        secretStore: SecretStore,
        cookieJar: PersistentCookieJar = PersistentCookieJar(secretStore),
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
