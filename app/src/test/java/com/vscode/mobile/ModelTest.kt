package com.vscode.mobile

import com.vscode.mobile.model.Codespace
import com.vscode.mobile.model.GitStatus
import com.vscode.mobile.model.Repository
import com.vscode.mobile.model.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    @Test
    fun `ViewMode fromString returns MOBILE for null`() {
        assertEquals(ViewMode.MOBILE, ViewMode.fromString(null))
    }

    @Test
    fun `ViewMode fromString returns MOBILE for empty`() {
        assertEquals(ViewMode.MOBILE, ViewMode.fromString(""))
    }

    @Test
    fun `ViewMode fromString is case insensitive`() {
        assertEquals(ViewMode.DESKTOP, ViewMode.fromString("desktop"))
        assertEquals(ViewMode.DESKTOP, ViewMode.fromString("DESKTOP"))
        assertEquals(ViewMode.MOBILE, ViewMode.fromString("mobile"))
    }

    @Test
    fun `Codespace label uses displayName when set`() {
        val cs = makeCodespace(name = "cs-12345", displayName = "My Workspace")
        assertEquals("My Workspace", cs.label)
    }

    @Test
    fun `Codespace label falls back to name when displayName is blank`() {
        val cs = makeCodespace(name = "cs-12345", displayName = "")
        assertEquals("cs-12345", cs.label)
    }

    @Test
    fun `Codespace label falls back to name when displayName is null`() {
        val cs = makeCodespace(name = "cs-12345", displayName = null)
        assertEquals("cs-12345", cs.label)
    }

    @Test
    fun `Codespace isAvailable reflects state`() {
        assertTrue(makeCodespace(state = "Available").isAvailable)
        assertFalse(makeCodespace(state = "Shutdown").isAvailable)
    }

    @Test
    fun `Codespace isStopped reflects state`() {
        assertTrue(makeCodespace(state = "Shutdown").isStopped)
        assertFalse(makeCodespace(state = "Available").isStopped)
    }

    @Test
    fun `Codespace isStarting reflects state`() {
        assertTrue(makeCodespace(state = "Starting").isStarting)
        assertFalse(makeCodespace(state = "Available").isStarting)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun makeCodespace(
        id: String = "id-1",
        name: String = "cs-name",
        displayName: String? = null,
        state: String = "Available"
    ) = Codespace(
        id = id,
        name = name,
        displayName = displayName,
        environmentId = null,
        state = state,
        url = "https://api.github.com/user/codespaces/$name",
        webUrl = "https://$name.github.dev",
        gitStatus = GitStatus(ref = "main", commitId = null, hasUncommittedChanges = false),
        repository = Repository(fullName = "owner/repo", htmlUrl = "https://github.com/owner/repo"),
        machine = null
    )
}
