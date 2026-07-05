package com.uzairansar.hermex.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointTest {
    private val base = "https://hermes.example.com/".toHttpUrl()

    @Test
    fun sessionEndpointMatchesIosContract() {
        val url = Endpoint.Session(
            id = "abc",
            includeMessages = true,
            messageLimit = 50,
            messageBefore = 10,
            expandRenderable = true,
        ).url(base)

        assertEquals("/api/session", url.encodedPath)
        assertEquals("abc", url.queryParameter("session_id"))
        assertEquals("1", url.queryParameter("messages"))
        assertEquals("50", url.queryParameter("msg_limit"))
        assertEquals("10", url.queryParameter("msg_before"))
        assertEquals("true", url.queryParameter("expand_renderable"))
    }

    @Test
    fun chatStreamEndpointMatchesIosContract() {
        val url = Endpoint.ChatStream("stream-1").url(base)

        assertEquals("/api/chat/stream", url.encodedPath)
        assertEquals("stream-1", url.queryParameter("stream_id"))
    }

    @Test
    fun sessionsIncludeArchivedEndpointMatchesIosContract() {
        val url = Endpoint.Sessions(includeArchived = true, archivedLimit = 25).url(base)

        assertEquals("/api/sessions", url.encodedPath)
        assertEquals("true", url.queryParameter("include_archived"))
        assertEquals("25", url.queryParameter("archived_limit"))
    }

    @Test
    fun sessionsSearchEndpointMatchesIosContract() {
        val url = Endpoint.SessionsSearch("android port", content = true, depth = 5).url(base)

        assertEquals("/api/sessions/search", url.encodedPath)
        assertEquals("android port", url.queryParameter("q"))
        assertEquals("true", url.queryParameter("content"))
        assertEquals("5", url.queryParameter("depth"))
    }

    @Test
    fun gitDiffEndpointMatchesIosContract() {
        val url = Endpoint.GitDiff("s1", "src/Main.kt", "staged").url(base)

        assertEquals("/api/git/diff", url.encodedPath)
        assertEquals("s1", url.queryParameter("session_id"))
        assertEquals("src/Main.kt", url.queryParameter("path"))
        assertEquals("staged", url.queryParameter("kind"))
    }
}
