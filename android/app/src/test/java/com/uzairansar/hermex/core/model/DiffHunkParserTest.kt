package com.uzairansar.hermex.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DiffHunkParserTest {
    @Test
    fun parsesUnifiedHunkRangesAndLineNumbers() {
        val hunks = DiffHunkParser.parse(
            """
            diff --git a/app.kt b/app.kt
            @@ -10,3 +10,4 @@ fun main() {
             context
            -old
            +new
            +extra
            """.trimIndent(),
        )

        assertEquals(1, hunks.size)
        val hunk = hunks.single()
        assertEquals("Lines 10-13", hunk.displayLabel)
        assertEquals(2, hunk.additions)
        assertEquals(1, hunk.deletions)
        assertEquals("10", hunk.lines[0].gutterLabel)
        assertEquals("11", hunk.lines[1].gutterLabel)
        assertEquals("11", hunk.lines[2].gutterLabel)
        assertEquals("12", hunk.lines[3].gutterLabel)
    }

    @Test
    fun fallsBackToSyntheticPatchGroupsWithoutHunkHeaders() {
        val hunks = DiffHunkParser.parse(
            """
            diff --git a/a.txt b/a.txt
            -old
            +new
            diff --git a/b.txt b/b.txt
            +created
            """.trimIndent(),
        )

        assertEquals(2, hunks.size)
        assertEquals("Patch 1 of 2", hunks[0].displayLabel)
        assertEquals("Patch 2 of 2", hunks[1].displayLabel)
        assertEquals(1, hunks[0].additions)
        assertEquals(1, hunks[0].deletions)
        assertEquals(1, hunks[1].additions)
    }

    @Test
    fun excludesFileHeaderLinesFromSyntheticPatchCounts() {
        val hunks = DiffHunkParser.parse(
            """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            -old
            +new
            """.trimIndent(),
        )

        assertEquals(1, hunks.single().additions)
        assertEquals(1, hunks.single().deletions)
    }
}
