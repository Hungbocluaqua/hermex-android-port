package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.ChatStartRequest
import com.uzairansar.hermex.core.model.NewSessionRequest
import com.uzairansar.hermex.core.model.SessionExportFormat
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HermesApiClientSessionTest {
    @Test
    fun newSessionSendsProfileWithoutBrowserHeaders() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"session":{"session_id":"s1","profile":"review"}}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.newSession(NewSessionRequest(profile = "review"))

            val request = server.takeRequest()
            assertEquals("s1", response.session?.sessionId)
            assertEquals("/api/session/new", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"profile":"review"}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun chatStartSendsProfileAndExplicitModelPickLikeIos() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"stream_id":"stream-1"}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.chatStart(
                ChatStartRequest(
                    sessionId = "s1",
                    message = "hello",
                    workspace = "/workspace",
                    model = "gpt-5",
                    modelProvider = "openai",
                    profile = "work",
                    explicitModelPick = true,
                ),
            )

            val request = server.takeRequest()
            assertEquals("stream-1", response.streamId)
            assertEquals("/api/chat/start", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals(
                """{"session_id":"s1","message":"hello","workspace":"/workspace","model":"gpt-5","model_provider":"openai","profile":"work","explicit_model_pick":true}""",
                request.body?.utf8(),
            )
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun updateSessionPostsWorkspaceModelAndProviderLikeIos() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"session":{"session_id":"s1","workspace":"/workspace","model":"gpt-5","model_provider":"openai"}}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.updateSession(
                sessionId = "s1",
                workspace = "/workspace",
                model = "gpt-5",
                modelProvider = "openai",
            )

            val request = server.takeRequest()
            assertEquals("gpt-5", response.session?.model)
            assertEquals("/api/session/update", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals(
                """{"session_id":"s1","workspace":"/workspace","model":"gpt-5","model_provider":"openai"}""",
                request.body?.utf8(),
            )
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun sessionYoloGetsSessionScopedBypassState() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"yolo_enabled":true}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.sessionYolo("s1")

            val request = server.takeRequest()
            assertEquals(true, response.isEnabled)
            assertEquals("/api/session/yolo", request.url.encodedPath)
            assertEquals("s1", request.url.queryParameter("session_id"))
            assertEquals("GET", request.method)
        } finally {
            server.close()
        }
    }

    @Test
    fun setSessionYoloPostsEnabledBodyLikeIos() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"yoloEnabled":true}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.setSessionYolo("s1", enabled = true)

            val request = server.takeRequest()
            assertEquals(true, response.isEnabled)
            assertEquals("/api/session/yolo", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"session_id":"s1","enabled":true}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun commandsDecodesServerSlashCommandMetadata() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"commands":[{"name":"review","description":"Review code","args_hint":"path","cli_only":false,"gateway_only":false}]}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.commands()

            val request = server.takeRequest()
            val command = response.commands?.single()
            assertEquals("/api/commands", request.url.encodedPath)
            assertEquals("GET", request.method)
            assertEquals("review", command?.displayName)
            assertEquals("path", command?.displayArgsHint)
            assertEquals(true, command?.isMobileVisible)
        } finally {
            server.close()
        }
    }

    @Test
    fun exportSessionDownloadsBytesAndUsesContentDispositionFilename() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/html")
                    .addHeader("Content-Disposition", "attachment; filename=\"../hermes-s1.html\"")
                    .body("<html>export</html>")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val file = client.exportSession("s1", SessionExportFormat.Html, fallbackTitle = "Android Port")

            val request = server.takeRequest()
            assertEquals("/api/session/export", request.url.encodedPath)
            assertEquals("s1", request.url.queryParameter("session_id"))
            assertEquals("html", request.url.queryParameter("format"))
            assertEquals("<html>export</html>", file.data.decodeToString())
            assertEquals("hermes-s1.html", file.filename)
            assertEquals("text/html", file.mimeType)
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun updateSettingsPostsOnlyShowCliSessionsWithoutBrowserHeaders() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"show_cli_sessions":false,"bot_name":"Hermes"}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.updateSettings(showCliSessions = false)

            val request = server.takeRequest()
            assertEquals(false, response.showCliSessions)
            assertEquals("/api/settings", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"show_cli_sessions":false}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun switchProfilePostsIosCompatibleNameBody() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"active":"work","default_model":"gpt-5.5","default_workspace":"/workspace","profiles":[{"name":"work","is_active":true}]}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.switchProfile("work")

            val request = server.takeRequest()
            assertEquals("work", response.active)
            assertEquals("gpt-5.5", response.defaultModel)
            assertEquals("/workspace", response.defaultWorkspace)
            assertEquals(true, response.profiles?.single()?.isActive)
            assertEquals("/api/profile/switch", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"name":"work"}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun createProfileOmitsNullOptionsAndDecodesProfile() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"profile":{"name":"research","path":"/profiles/research","is_default":false}}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.createProfile(name = "research")

            val request = server.takeRequest()
            assertEquals(true, response.ok)
            assertEquals("research", response.profile?.name)
            assertEquals("/profiles/research", response.profile?.path)
            assertEquals(false, response.profile?.isDefault)
            assertEquals("/api/profile/create", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"name":"research","clone_config":false}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun createProfileSendsOptionalSnakeCaseFields() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.createProfile(
                name = "research",
                cloneConfig = true,
                defaultModel = "claude-sonnet-4-5",
                modelProvider = "anthropic",
                baseUrl = "http://localhost:11434",
                apiKey = "sk-test",
            )

            val request = server.takeRequest()
            assertEquals(true, response.ok)
            assertEquals("/api/profile/create", request.url.encodedPath)
            assertEquals(
                """{"name":"research","clone_config":true,"default_model":"claude-sonnet-4-5","model_provider":"anthropic","base_url":"http://localhost:11434","api_key":"sk-test"}""",
                request.body?.utf8(),
            )
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun forcedUpdateCheckPostsForceBodyAndDecodesState() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"webui":{"behind":2,"current_sha":"abc","latest_sha":"def","stale_check":false},"checked_at":1710000000}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.updatesCheckForced()

            val request = server.takeRequest()
            assertEquals(2, response.webui?.behind)
            assertEquals("abc", response.webui?.currentSha)
            assertEquals("def", response.webui?.latestSha)
            assertEquals(false, response.webui?.staleCheck)
            assertEquals("/api/updates/check", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"force":true}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun applyUpdatePostsTargetBodyAndDecodesRestartBlocked() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":false,"target":"webui","restart_blocked":true,"active_streams":1,"active_runs":2,"message":"Active work is running"}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.applyUpdate()

            val request = server.takeRequest()
            assertEquals(false, response.ok)
            assertEquals(true, response.restartBlocked)
            assertEquals(1, response.activeStreams)
            assertEquals(2, response.activeRuns)
            assertEquals("/api/updates/apply", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"target":"webui"}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun mediaEndpointDownloadsBytesWithoutBrowserHeaders() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "image/png")
                    .body("PNG")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val bytes = client.media("/tmp/chart.png")

            val request = server.takeRequest()
            assertEquals("PNG", bytes.decodeToString())
            assertEquals("/api/media", request.url.encodedPath)
            assertEquals("/tmp/chart.png", request.url.queryParameter("path"))
            assertEquals("GET", request.method)
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun fileEndpointLoadsTextPreviewWithoutBrowserHeaders() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"path":"README.md","content":"# Hello\nPreview","encoding":"utf-8","language":"markdown","size":15}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val file = client.file("s1", "README.md")

            val request = server.takeRequest()
            assertEquals("# Hello\nPreview", file.content)
            assertEquals("markdown", file.language)
            assertEquals("/api/file", request.url.encodedPath)
            assertEquals("s1", request.url.queryParameter("session_id"))
            assertEquals("README.md", request.url.queryParameter("path"))
            assertEquals("GET", request.method)
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun gitCommitSelectedPostsPathsAndMessageLikeIos() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"sha":"abcdef123456","short_sha":"abcdef1","status":{"files":[]}}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.gitCommitSelected(
                sessionId = "s1",
                message = "Update selected files",
                paths = listOf("src/A.kt", "README.md"),
            )

            val request = server.takeRequest()
            assertEquals("abcdef1", response.shortSha)
            assertEquals("/api/git/commit-selected", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals(
                """{"session_id":"s1","message":"Update selected files","paths":["src/A.kt","README.md"]}""",
                request.body?.utf8(),
            )
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun gitCommitMessageSelectedPostsPathsAndDecodesTruncatedFlag() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"message":"Update selected files","truncated":true}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.gitCommitMessageSelected("s1", listOf("src/A.kt", "README.md"))

            val request = server.takeRequest()
            assertEquals("Update selected files", response.message)
            assertEquals(true, response.truncated)
            assertEquals("/api/git/commit-message-selected", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"session_id":"s1","paths":["src/A.kt","README.md"]}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun gitDiscardPostsDeleteUntrackedFlag() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"message":"Discarded"}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.gitDiscard("s1", listOf("new-file.txt"), deleteUntracked = true)

            val request = server.takeRequest()
            assertEquals("Discarded", response.message)
            assertEquals("/api/git/discard", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"session_id":"s1","paths":["new-file.txt"],"delete_untracked":true}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun remoteTranscriptMediaScopesCustomHeadersToSameOrigin() = runBlocking {
        val server = MockWebServer()
        val external = MockWebServer()
        try {
            server.start()
            external.start()
            server.enqueue(MockResponse.Builder().code(200).body("same-origin").build())
            external.enqueue(MockResponse.Builder().code(200).body("external").build())

            val client = HermesApiClient(
                baseUrl = server.url("/"),
                client = OkHttpClient(),
                customHeaders = { listOf(CustomHeader("X-Hermex-Test", "secret")) },
            )

            val sameOriginBytes = client.remoteTranscriptMediaData(server.url("/media/image.png"))
            val externalBytes = client.remoteTranscriptMediaData(external.url("/image.png"))

            val sameOriginRequest = server.takeRequest()
            val externalRequest = external.takeRequest()
            assertEquals("same-origin", sameOriginBytes.decodeToString())
            assertEquals("external", externalBytes.decodeToString())
            assertEquals("secret", sameOriginRequest.headers["X-Hermex-Test"])
            assertNull(externalRequest.headers["X-Hermex-Test"])
            assertNull(externalRequest.headers["Origin"])
            assertNull(externalRequest.headers["Referer"])
        } finally {
            server.close()
            external.close()
        }
    }
}
