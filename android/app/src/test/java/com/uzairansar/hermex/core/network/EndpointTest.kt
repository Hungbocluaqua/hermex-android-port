package com.uzairansar.hermex.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointTest {
    @Test
    fun preservesConfiguredServerSubpaths() {
        val url = Endpoint.ChatStream("stream-1").url("https://example.com/hermes/".toHttpUrl())

        assertEquals("/hermes/api/chat/stream", url.encodedPath)
        assertEquals("stream-1", url.queryParameter("stream_id"))
    }

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
        assertEquals("1", url.queryParameter("expand_renderable"))
    }

    @Test
    fun sessionUsageEndpointMatchesIosContract() {
        val url = Endpoint.SessionUsage("abc").url(base)

        assertEquals("/api/session/usage", url.encodedPath)
        assertEquals("abc", url.queryParameter("session_id"))
    }

    @Test
    fun sessionClearEndpointMatchesIosContract() {
        val url = Endpoint.ClearSession.url(base)

        assertEquals("/api/session/clear", url.encodedPath)
        assertEquals(null, url.query)
    }

    @Test
    fun mediaEndpointIncludesSessionScopedAuthorizationParameters() {
        val url = Endpoint.Media("session-123", "/tmp/example.png").url(base)

        assertEquals("/api/media", url.encodedPath)
        assertEquals("session-123", url.queryParameter("session_id"))
        assertEquals("/tmp/example.png", url.queryParameter("path"))
    }

    @Test
    fun workspaceRegistryEndpointsMatchIosContract() {
        assertEquals("/api/workspaces/add", Endpoint.WorkspaceAdd.url(base).encodedPath)
        assertEquals("/api/workspaces/remove", Endpoint.WorkspaceRemove.url(base).encodedPath)
        assertEquals("/api/workspaces/rename", Endpoint.WorkspaceRename.url(base).encodedPath)
        assertEquals("/api/workspaces/reorder", Endpoint.WorkspaceReorder.url(base).encodedPath)
    }

    @Test
    fun chatStreamEndpointMatchesIosContract() {
        val url = Endpoint.ChatStream("stream-1").url(base)

        assertEquals("/api/chat/stream", url.encodedPath)
        assertEquals("stream-1", url.queryParameter("stream_id"))
        assertEquals(null, url.queryParameter("replay"))
        assertEquals(null, url.queryParameter("after_seq"))
    }

    @Test
    fun chatStreamReplayEndpointMatchesIosContract() {
        val url = Endpoint.ChatStream("stream-1", replayAfterSeq = 42).url(base)

        assertEquals("/api/chat/stream", url.encodedPath)
        assertEquals("stream-1", url.queryParameter("stream_id"))
        assertEquals("1", url.queryParameter("replay"))
        assertEquals("42", url.queryParameter("after_seq"))
    }

    @Test
    fun chatStreamReplayClampsNegativeSequence() {
        val url = Endpoint.ChatStream("stream-1", replayAfterSeq = -7).url(base)

        assertEquals("0", url.queryParameter("after_seq"))
    }

    @Test
    fun sessionsIncludeArchivedEndpointMatchesIosContract() {
        val url = Endpoint.Sessions(includeArchived = true, archivedLimit = 25).url(base)

        assertEquals("/api/sessions", url.encodedPath)
        assertEquals("1", url.queryParameter("include_archived"))
        assertEquals("25", url.queryParameter("archived_limit"))
    }

    @Test
    fun sessionsEndpointOmitsArchivedLimitUnlessArchivedIsIncluded() {
        val url = Endpoint.Sessions(includeArchived = false, archivedLimit = 25).url(base)

        assertEquals("/api/sessions", url.encodedPath)
        assertEquals(null, url.queryParameter("include_archived"))
        assertEquals(null, url.queryParameter("archived_limit"))
    }

    @Test
    fun sessionsSearchEndpointMatchesIosContract() {
        val url = Endpoint.SessionsSearch("android port", content = true, depth = 5).url(base)

        assertEquals("/api/sessions/search", url.encodedPath)
        assertEquals("android port", url.queryParameter("q"))
        assertEquals("1", url.queryParameter("content"))
        assertEquals("5", url.queryParameter("depth"))
    }

    @Test
    fun sessionsSearchEndpointUsesZeroForContentFalse() {
        val url = Endpoint.SessionsSearch("android port", content = false, depth = 2).url(base)

        assertEquals("0", url.queryParameter("content"))
    }

    @Test
    fun sessionExportEndpointMatchesIosContract() {
        val url = Endpoint.ExportSession("s1", "html").url(base)

        assertEquals("/api/session/export", url.encodedPath)
        assertEquals("s1", url.queryParameter("session_id"))
        assertEquals("html", url.queryParameter("format"))
    }

    @Test
    fun gitDiffEndpointMatchesIosContract() {
        val url = Endpoint.GitDiff("s1", "src/Main.kt", "staged").url(base)

        assertEquals("/api/git/diff", url.encodedPath)
        assertEquals("s1", url.queryParameter("session_id"))
        assertEquals("src/Main.kt", url.queryParameter("path"))
        assertEquals("staged", url.queryParameter("kind"))
    }

    @Test
    fun cronHistoryEndpointMatchesIosContract() {
        val url = Endpoint.CronHistory("job-1", offset = 10, limit = 50).url(base)

        assertEquals("/api/crons/history", url.encodedPath)
        assertEquals("job-1", url.queryParameter("job_id"))
        assertEquals("10", url.queryParameter("offset"))
        assertEquals("50", url.queryParameter("limit"))
    }

    @Test
    fun cronHistoryEndpointOmitsOptionalPaging() {
        val url = Endpoint.CronHistory("job-1").url(base)

        assertEquals("/api/crons/history", url.encodedPath)
        assertEquals("job-1", url.queryParameter("job_id"))
        assertEquals(null, url.queryParameter("offset"))
        assertEquals(null, url.queryParameter("limit"))
    }

    @Test
    fun cronDeliveryOptionsEndpointMatchesIosContract() {
        val url = Endpoint.CronDeliveryOptions.url(base)

        assertEquals("/api/crons/delivery-options", url.encodedPath)
        assertEquals(null, url.query)
    }

    @Test
    fun kanbanReadEndpointsClampPagingAndPreserveFilters() {
        val board = Endpoint.KanbanBoard(
            board = "main",
            tenant = "mobile",
            assignee = "reviewer",
            includeArchived = true,
            onlyMine = true,
            since = 42,
        ).url(base)
        val events = Endpoint.KanbanEvents("main", since = -4, limit = 900).url(base)
        val stream = Endpoint.KanbanEventsStream("main", since = -4).url(base)
        val log = Endpoint.KanbanCardLog("card/../unsafe", "main", tailBytes = 9_000_000).url(base)

        assertEquals("/api/kanban/board", board.encodedPath)
        assertEquals("true", board.queryParameter("include_archived"))
        assertEquals("true", board.queryParameter("only_mine"))
        assertEquals("42", board.queryParameter("since"))
        assertEquals("0", events.queryParameter("since"))
        assertEquals("200", events.queryParameter("limit"))
        assertEquals("/api/kanban/events/stream", stream.encodedPath)
        assertEquals("main", stream.queryParameter("board"))
        assertEquals("0", stream.queryParameter("since"))
        assertEquals("/api/kanban/tasks/card%2F..%2Funsafe/log", log.encodedPath)
        assertEquals("2000000", log.queryParameter("tail"))
    }

    @Test
    fun kanbanDynamicBoardAndCardPathsEncodeSingleSegments() {
        val board = Endpoint.KanbanBoardBySlug("team/../board").url(base)
        val active = Endpoint.KanbanBoardSwitch("team/../board").url(base)
        val card = Endpoint.KanbanCard("card/../one", "main").url(base)
        val comment = Endpoint.KanbanCardComments("card/../one", "main").url(base)

        assertEquals("/api/kanban/boards/team%2F..%2Fboard", board.encodedPath)
        assertEquals("/api/kanban/boards/team%2F..%2Fboard/switch", active.encodedPath)
        assertEquals("/api/kanban/tasks/card%2F..%2Fone", card.encodedPath)
        assertEquals("/api/kanban/tasks/card%2F..%2Fone/comments", comment.encodedPath)
    }

    @Test
    fun kanbanDispatcherIsBoundedToEightWorkers() {
        val url = Endpoint.KanbanDispatch("main", dryRun = false, maximum = 99).url(base)

        assertEquals("/api/kanban/dispatch", url.encodedPath)
        assertEquals("main", url.queryParameter("board"))
        assertEquals("false", url.queryParameter("dry_run"))
        assertEquals("8", url.queryParameter("max"))
    }
}
