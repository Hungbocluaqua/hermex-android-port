package com.uzairansar.hermex.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceBreadcrumbTest {
    @Test
    fun unixAbsolutePathsKeepTheirLeadingSlash() {
        assertEquals(
            listOf(
                WorkspaceBreadcrumb("Root", null),
                WorkspaceBreadcrumb("home", "/home"),
                WorkspaceBreadcrumb("hermes", "/home/hermes"),
                WorkspaceBreadcrumb("workspace", "/home/hermes/workspace"),
            ),
            "/home/hermes/workspace".workspaceBreadcrumbs(),
        )
    }

    @Test
    fun relativeAndWindowsPathsKeepTheirOriginalPathStyle() {
        assertEquals(
            listOf(
                WorkspaceBreadcrumb("Root", null),
                WorkspaceBreadcrumb("work", "work"),
                WorkspaceBreadcrumb("src", "work/src"),
            ),
            "work/src".workspaceBreadcrumbs(),
        )
        assertEquals(
            listOf(
                WorkspaceBreadcrumb("Root", null),
                WorkspaceBreadcrumb("C:", "C:"),
                WorkspaceBreadcrumb("work", "C:\\work"),
                WorkspaceBreadcrumb("src", "C:\\work\\src"),
            ),
            "C:\\work\\src".workspaceBreadcrumbs(),
        )
    }
}
