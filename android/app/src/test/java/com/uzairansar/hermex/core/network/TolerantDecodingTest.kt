package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.SessionsResponse
import com.uzairansar.hermex.core.model.GitBranchesResponse
import com.uzairansar.hermex.core.model.CronsResponse
import com.uzairansar.hermex.core.model.CronDeliveryOptionsResponse
import com.uzairansar.hermex.core.model.CronHistoryResponse
import com.uzairansar.hermex.core.model.CronStatusResponse
import com.uzairansar.hermex.core.model.GoalSubmissionResponse
import com.uzairansar.hermex.core.model.MemoryResponse
import com.uzairansar.hermex.core.model.SessionResponse
import com.uzairansar.hermex.core.model.SessionStatusResponse
import com.uzairansar.hermex.core.model.SettingsResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class TolerantDecodingTest {
    @Test
    fun sessionsDecodeWithUnknownFieldsAndMissingOptionals() {
        val json = """
            {
              "sessions": [
                {
                  "session_id": "s1",
                  "title": "Port Android",
                  "brand_new_upstream_field": {"nested": true}
                }
              ],
              "another_future_field": 42
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<SessionsResponse>(json)

        assertEquals("s1", decoded.sessions?.single()?.sessionId)
        assertEquals("Port Android", decoded.sessions?.single()?.title)
    }

    @Test
    fun gitBranchesDecodeRichServerShape() {
        val json = """
            {
              "branches": {
                "is_git": true,
                "current": "main",
                "local": [
                  {"name": "main", "subject": "Keep Android branch picker aligned"}
                ],
                "remote": [
                  {"name": "origin/main", "ahead": 0, "behind": 1}
                ]
              }
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<GitBranchesResponse>(json)

        assertEquals("main", decoded.branches?.current)
        assertEquals("main", decoded.branches?.local?.single()?.name)
        assertEquals("origin/main", decoded.branches?.remote?.single()?.name)
        assertEquals(1, decoded.branches?.remote?.single()?.behind)
    }

    @Test
    fun cronScheduleDecodesWhenServerReturnsObject() {
        val json = """
            {
              "jobs": [
                {
                  "id": "job-1",
                  "name": "Nightly summary",
                  "schedule": {"kind": "cron", "expression": "0 7 * * *"},
                  "toast_notifications": true
                }
              ]
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<CronsResponse>(json)

        assertEquals("job-1", decoded.jobs?.single()?.id)
        assertEquals("Nightly summary", decoded.jobs?.single()?.name)
        assertEquals("0 7 * * *", decoded.jobs?.single()?.schedule?.displayText)
        assertEquals(true, decoded.jobs?.single()?.toastNotifications)
    }

    @Test
    fun cronDatesDecodeTimestampStringAndIsoValues() {
        val json = """
            {
              "jobs": [
                {
                  "id": "job-1",
                  "next_run_at": "1710000000.5",
                  "last_run_at": "2024-03-09T16:00:01Z"
                }
              ]
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<CronsResponse>(json)
        val job = decoded.jobs?.single()

        assertEquals(1710000000.5, job?.nextRunAt?.epochSeconds ?: 0.0, 0.0)
        assertEquals(1710000001.0, job?.lastRunAt?.epochSeconds ?: 0.0, 0.0)
    }

    @Test
    fun cronStatusDecodesRunningAsMapOrRunningJobs() {
        val runningMapDecoded = HermesJson.decodeFromString<CronStatusResponse>(
            """
            {
              "running": {
                "job-1": "12.5",
                "job-2": 60
              }
            }
            """.trimIndent(),
        )
        val runningJobsDecoded = HermesJson.decodeFromString<CronStatusResponse>(
            """
            {
              "running_jobs": {
                "job-3": 3.25
              }
            }
            """.trimIndent(),
        )

        assertEquals(12.5, runningMapDecoded.runningJobDurations["job-1"] ?: 0.0, 0.0)
        assertEquals(60.0, runningMapDecoded.runningJobDurations["job-2"] ?: 0.0, 0.0)
        assertEquals(3.25, runningJobsDecoded.runningJobDurations["job-3"] ?: 0.0, 0.0)
    }

    @Test
    fun cronHistoryDecodesRunsAndUsage() {
        val decoded = HermesJson.decodeFromString<CronHistoryResponse>(
            """
            {
              "job_id": "job-1",
              "runs": [
                {
                  "filename": "2026-05-04_10-00-00.md",
                  "size": 2048,
                  "modified": 1777892400.5,
                  "usage": {
                    "model": "@openai:gpt-5.5",
                    "provider": "openai",
                    "estimated_cost_usd": 0.041,
                    "duration_seconds": 12.5,
                    "input_tokens": 1200,
                    "output_tokens": 300,
                    "total_tokens": 1500
                  },
                  "future": true
                }
              ],
              "total": 12,
              "offset": 10,
              "another_future_field": {"nested": true}
            }
            """.trimIndent(),
        )

        val run = decoded.runs?.single()
        assertEquals("job-1", decoded.jobId)
        assertEquals(12, decoded.total)
        assertEquals(10, decoded.offset)
        assertEquals("2026-05-04_10-00-00.md", run?.filename)
        assertEquals(2048, run?.size)
        assertEquals(1777892400.5, run?.modified ?: 0.0, 0.0)
        assertEquals("@openai:gpt-5.5", run?.usage?.model)
        assertEquals("openai", run?.usage?.provider)
        assertEquals(0.041, run?.usage?.estimatedCostUsd ?: 0.0, 0.0)
        assertEquals(12.5, run?.usage?.durationSeconds ?: 0.0, 0.0)
        assertEquals(1200, run?.usage?.inputTokens)
        assertEquals(300, run?.usage?.outputTokens)
        assertEquals(1500, run?.usage?.totalTokens)
    }

    @Test
    fun cronDeliveryOptionsDecodePlatforms() {
        val decoded = HermesJson.decodeFromString<CronDeliveryOptionsResponse>(
            """
            {
              "platforms": [
                {"value": "local", "label": "Local (save output only)", "future": true},
                {"value": "origin", "label": "Origin (reply to creator)"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, decoded.platforms?.size)
        assertEquals("local", decoded.platforms?.first()?.value)
        assertEquals("Local (save output only)", decoded.platforms?.first()?.label)
        assertEquals("origin", decoded.platforms?.last()?.value)
    }

    @Test
    fun goalResponseDecodesKickoffPrompt() {
        val json = """
            {
              "ok": true,
              "message": "Goal started.",
              "goal": {"goal": "Port Android", "turns_used": 1, "max_turns": 8},
              "kickoff_prompt": "Continue the port."
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<GoalSubmissionResponse>(json)

        assertEquals("Goal started.", decoded.message)
        assertEquals("Port Android", decoded.goal?.goal)
        assertEquals(1, decoded.goal?.turnsUsed)
        assertEquals("Continue the port.", decoded.kickoffPrompt)
    }

    @Test
    fun settingsDecodeShowCliSessionsWhenServerReportsIt() {
        val decoded = HermesJson.decodeFromString<SettingsResponse>(
            """
            {
              "webui_version": "v0.50.253",
              "bot_name": "Hermes",
              "show_cli_sessions": false,
              "future": "ignored"
            }
            """.trimIndent(),
        )

        assertEquals("v0.50.253", decoded.webuiVersion)
        assertEquals("Hermes", decoded.botName)
        assertEquals(false, decoded.showCliSessions)
    }

    @Test
    fun sessionDecodesCompressionAnchorMetadata() {
        val decoded = HermesJson.decodeFromString<SessionResponse>(
            """
            {
              "session": {
                "session_id": "s1",
                "_messages_offset": 12,
                "_messages_truncated": true,
                "active_stream_id": "stream-1",
                "is_streaming": true,
                "messages": [],
                "tool_calls": [
                  {
                    "name": "git status",
                    "snippet": "clean",
                    "tid": "tool-1",
                    "assistant_msg_idx": 13,
                    "args": {"short": true}
                  }
                ],
                "compression_anchor_visible_idx": 7,
                "compression_anchor_message_key": {
                  "role": "assistant",
                  "ts": 1710000000,
                  "text": "Anchor text",
                  "attachments": 2
                },
                "compression_anchor_summary": "Reference only summary"
              }
            }
            """.trimIndent(),
        )

        assertEquals(12, decoded.session?.messagesOffset)
        assertEquals(true, decoded.session?.messagesTruncated)
        assertEquals("stream-1", decoded.session?.activeStreamId)
        assertEquals(true, decoded.session?.isStreaming)
        assertEquals(7, decoded.session?.compressionAnchorVisibleIdx)
        assertEquals("assistant", decoded.session?.compressionAnchorMessageKey?.role)
        assertEquals(1710000000.0, decoded.session?.compressionAnchorMessageKey?.ts ?: 0.0, 0.0)
        assertEquals("Anchor text", decoded.session?.compressionAnchorMessageKey?.text)
        assertEquals(2, decoded.session?.compressionAnchorMessageKey?.attachments)
        assertEquals("Reference only summary", decoded.session?.compressionAnchorSummary)
        assertEquals("git status", decoded.session?.toolCalls?.single()?.name)
        assertEquals("clean", decoded.session?.toolCalls?.single()?.snippet)
        assertEquals("tool-1", decoded.session?.toolCalls?.single()?.tid)
        assertEquals(13, decoded.session?.toolCalls?.single()?.assistantMsgIdx)
    }

    @Test
    fun sessionDecodesPaginationAliases() {
        val decoded = HermesJson.decodeFromString<SessionResponse>(
            """
            {
              "session": {
                "session_id": "s1",
                "_messagesOffset": 9,
                "messagesTruncated": true,
                "messages": []
              }
            }
            """.trimIndent(),
        )

        assertEquals(9, decoded.session?.transformedMessagesOffset)
        assertEquals(true, decoded.session?.camelMessagesTruncated)
    }

    @Test
    fun streamStatusDecodesRecoveryFields() {
        val decoded = HermesJson.decodeFromString<SessionStatusResponse>(
            """
            {
              "active": true,
              "stream_id": "stream-1",
              "active_stream_id": "stream-1",
              "is_streaming": true,
              "replay_available": true,
              "pending_user_message": "Continue"
            }
            """.trimIndent(),
        )

        assertEquals(true, decoded.active)
        assertEquals("stream-1", decoded.streamId)
        assertEquals("stream-1", decoded.activeStreamId)
        assertEquals(true, decoded.isStreaming)
        assertEquals(true, decoded.replayAvailable)
        assertEquals("Continue", decoded.pendingUserMessage)
    }

    @Test
    fun chatMessageReasoningDecodesObjectAndStringSegments() {
        val decoded = HermesJson.decodeFromString<ChatMessage>(
            """
            {
              "role": "assistant",
              "content": "Done",
              "reasoning": [
                {"text":"Trace one"},
                "Trace two"
              ]
            }
            """.trimIndent(),
        )

        assertEquals("Trace one", decoded.reasoning?.get(0)?.text)
        assertEquals("Trace two", decoded.reasoning?.get(1)?.text)
    }

    @Test
    fun chatMessageDecodesIosCompatibleAliasesAndContentParts() {
        val decoded = HermesJson.decodeFromString<ChatMessage>(
            """
            {
              "role": "assistant",
              "_ts": "1710000000.5",
              "message_id": "msg-1",
              "name": "Hermes",
              "tool_call_id": "tool-call-1",
              "tool_use_id": "tool-use-1",
              "content": [
                {"type": "text", "text": "Hello "},
                "world",
                {"type": "image", "url": "ignored"}
              ],
              "reasoning": "compact thought"
            }
            """.trimIndent(),
        )

        assertEquals("msg-1", decoded.id)
        assertEquals("msg-1", decoded.messageId)
        assertEquals(1710000000.5, decoded.timestamp ?: 0.0, 0.0)
        assertEquals("Hermes", decoded.name)
        assertEquals("tool-call-1", decoded.toolCallId)
        assertEquals("tool-use-1", decoded.toolUseId)
        assertEquals("Hello world", decoded.displayText)
        assertEquals(3, decoded.parts?.size)
        assertEquals("compact thought", decoded.reasoning?.single()?.text)
    }

    @Test
    fun chatMessageEnrichesAttachmentsFromAttachedFilesMarker() {
        val decoded = HermesJson.decodeFromString<ChatMessage>(
            """
            {
              "role": "user",
              "content": "Please inspect these.\n\n[Attached files: /tmp/uploads/photo.png, notes.txt]",
              "attachments": [
                {"name": "photo.png"},
                {"name": "notes.txt"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("photo.png", decoded.attachments?.get(0)?.name)
        assertEquals("/tmp/uploads/photo.png", decoded.attachments?.get(0)?.path)
        assertEquals(true, decoded.attachments?.get(0)?.isImage)
        assertEquals("notes.txt", decoded.attachments?.get(1)?.name)
        assertEquals(null, decoded.attachments?.get(1)?.path)
    }

    @Test
    fun chatMessageInfersAttachmentsWhenOnlyMarkerIsPresent() {
        val decoded = HermesJson.decodeFromString<ChatMessage>(
            """
            {
              "role": "user",
              "content": "Images only.\n\n[Attached files: /tmp/uploads/first.jpg, second.png]"
            }
            """.trimIndent(),
        )

        assertEquals("first.jpg", decoded.attachments?.get(0)?.name)
        assertEquals("/tmp/uploads/first.jpg", decoded.attachments?.get(0)?.path)
        assertEquals("second.png", decoded.attachments?.get(1)?.name)
        assertEquals("/tmp/uploads/second.png", decoded.attachments?.get(1)?.path)
        assertEquals(true, decoded.attachments?.get(1)?.isImage)
    }

    @Test
    fun memoryResponseDecodesProjectContextMetadataAndShadowedShapes() {
        val booleanDecoded = HermesJson.decodeFromString<MemoryResponse>(
            """
            {
              "memory": "Remember Android parity.",
              "memory_mtime": 1710000000.0,
              "user_mtime": 1710000100.0,
              "soul_mtime": 1710000200.0,
              "project_context": "Use Kotlin.",
              "project_context_mtime": 1710000300.0,
              "project_context_shadowed": true
            }
            """.trimIndent(),
        )

        val listDecoded = HermesJson.decodeFromString<MemoryResponse>(
            """
            {
              "project_context": "Workspace override.",
              "project_context_shadowed": [{"path": "/repo/AGENTS.md"}]
            }
            """.trimIndent(),
        )

        val emptyListDecoded = HermesJson.decodeFromString<MemoryResponse>(
            """
            {
              "project_context": "Global context.",
              "project_context_shadowed": []
            }
            """.trimIndent(),
        )

        assertEquals(1710000000.0, booleanDecoded.memoryMtime ?: 0.0, 0.0)
        assertEquals(1710000100.0, booleanDecoded.userMtime ?: 0.0, 0.0)
        assertEquals(1710000200.0, booleanDecoded.soulMtime ?: 0.0, 0.0)
        assertEquals(1710000300.0, booleanDecoded.projectContextMtime ?: 0.0, 0.0)
        assertEquals(true, booleanDecoded.projectContextShadowed)
        assertEquals(true, listDecoded.projectContextShadowed)
        assertEquals(false, emptyListDecoded.projectContextShadowed)
    }
}
