package com.vscode.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the codespace URL detection logic used in [com.vscode.mobile.ui.MainActivity].
 *
 * This mirrors the same patterns defined in [MainActivity.CODESPACE_URL_PATTERNS]
 * so we can verify detection without an Android instrumentation test.
 */
class CodespaceUrlDetectionTest {

    /** Patterns that indicate a live codespace (mirrors MainActivity). */
    private val codespaceUrlPatterns = listOf(
        ".github.dev",
        ".app.github.dev",
        ".githubpreview.dev"
    )

    /** Mirrors [MainActivity.isCodespaceUrl] logic. */
    private fun isCodespaceUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        // Simplified host extraction without android.net.Uri (which is unavailable in unit tests)
        val host = extractHost(url) ?: return false
        return codespaceUrlPatterns.any { pattern -> host.endsWith(pattern) }
    }

    /** Simple host extraction for unit tests (avoids android.net.Uri). */
    private fun extractHost(url: String): String? {
        return try {
            val withoutScheme = url.substringAfter("://")
            val hostPort = withoutScheme.substringBefore("/")
            hostPort.substringBefore(":")
        } catch (_: Exception) {
            null
        }
    }

    // ── Codespace URLs (should be detected) ────────────────────────────────────

    @Test
    fun `github-dev codespace URL is detected`() {
        assertTrue(isCodespaceUrl("https://my-codespace-abc123.github.dev/"))
    }

    @Test
    fun `app-github-dev codespace URL is detected`() {
        assertTrue(isCodespaceUrl("https://my-codespace-abc123.app.github.dev/"))
    }

    @Test
    fun `githubpreview-dev codespace URL is detected`() {
        assertTrue(isCodespaceUrl("https://abc123.githubpreview.dev/"))
    }

    @Test
    fun `deep subdomain of github-dev is detected`() {
        assertTrue(isCodespaceUrl("https://something.deep.github.dev/"))
    }

    // ── Non-codespace URLs (should NOT be detected) ────────────────────────────

    @Test
    fun `github-com codespace list is not a codespace`() {
        assertFalse(isCodespaceUrl("https://github.com/codespaces"))
    }

    @Test
    fun `github-com login is not a codespace`() {
        assertFalse(isCodespaceUrl("https://github.com/login"))
    }

    @Test
    fun `plain github-com is not a codespace`() {
        assertFalse(isCodespaceUrl("https://github.com/"))
    }

    @Test
    fun `null URL is not a codespace`() {
        assertFalse(isCodespaceUrl(null))
    }

    @Test
    fun `empty URL is not a codespace`() {
        assertFalse(isCodespaceUrl(""))
    }

    @Test
    fun `blank URL is not a codespace`() {
        assertFalse(isCodespaceUrl("   "))
    }

    @Test
    fun `evil domain is not a codespace`() {
        assertFalse(isCodespaceUrl("https://evil.com/"))
    }

    @Test
    fun `bare github-dev without subdomain is not a codespace`() {
        // "github.dev" does not end with ".github.dev" (note the dot)
        assertFalse(isCodespaceUrl("https://github.dev/"))
    }
}
