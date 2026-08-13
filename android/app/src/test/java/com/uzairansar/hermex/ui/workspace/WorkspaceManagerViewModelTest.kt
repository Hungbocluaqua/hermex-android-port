package com.uzairansar.hermex.ui.workspace

import com.uzairansar.hermex.MainDispatcherRule
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceManagerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun addTrimsInputAndAdoptsCanonicalServerRegistry() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"workspaces":[{"path":"/work/a","name":"A"}]}"""))
            server.enqueue(
                json(
                    """{"ok":true,"workspaces":[{"path":"/work/a","name":"A"},{"path":"/work/b","name":"B"}]}""",
                ),
            )
            val viewModel = WorkspaceManagerViewModel(
                WorkspaceRepository(HermesApiClient(server.url("/"), OkHttpClient())),
            )
            awaitState { !viewModel.state.value.isLoading }

            viewModel.add("  /work/b  ", "   ", create = false)
            awaitState { viewModel.state.value.mutationVersion == 1 }

            assertEquals(listOf("/work/a", "/work/b"), viewModel.state.value.workspaces.map { it.path })
            assertEquals(1, viewModel.state.value.mutationVersion)
            assertFalse(viewModel.state.value.isMutating)
            assertEquals("/api/workspaces", server.takeRequest().url.encodedPath)
            val add = server.takeRequest()
            assertEquals("/api/workspaces/add", add.url.encodedPath)
            assertEquals("""{"path":"/work/b"}""", add.body?.utf8())
        } finally {
            server.close()
        }
    }

    private fun json(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private suspend fun awaitState(condition: () -> Boolean) {
        withTimeout(2_000) {
            while (!condition()) delay(10)
        }
    }
}
