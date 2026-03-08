package com.vscode.mobile

import com.vscode.mobile.auth.GitHubCredentialsValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GitHubCredentialsValidator].
 *
 * These tests ensure that the guard in [com.vscode.mobile.ui.LoginActivity]
 * correctly rejects placeholder or empty credentials so that the GitHub OAuth
 * URL is never opened with an invalid client ID.
 */
class GitHubCredentialsValidatorTest {

    // ── isClientIdValid ────────────────────────────────────────────────────────

    @Test
    fun `isClientIdValid returns false for the build-time placeholder`() {
        assertFalse(GitHubCredentialsValidator.isClientIdValid("REPLACE_WITH_YOUR_CLIENT_ID"))
    }

    @Test
    fun `isClientIdValid returns false for blank string`() {
        assertFalse(GitHubCredentialsValidator.isClientIdValid(""))
    }

    @Test
    fun `isClientIdValid returns false for whitespace-only string`() {
        assertFalse(GitHubCredentialsValidator.isClientIdValid("   "))
    }

    @Test
    fun `isClientIdValid returns true for a real-looking client ID`() {
        assertTrue(GitHubCredentialsValidator.isClientIdValid("Ov23liABCDEFGHIJ1234"))
    }

    // ── isClientSecretValid ────────────────────────────────────────────────────

    @Test
    fun `isClientSecretValid returns false for the build-time placeholder`() {
        assertFalse(GitHubCredentialsValidator.isClientSecretValid("REPLACE_WITH_YOUR_CLIENT_SECRET"))
    }

    @Test
    fun `isClientSecretValid returns false for blank string`() {
        assertFalse(GitHubCredentialsValidator.isClientSecretValid(""))
    }

    @Test
    fun `isClientSecretValid returns false for whitespace-only string`() {
        assertFalse(GitHubCredentialsValidator.isClientSecretValid("   "))
    }

    @Test
    fun `isClientSecretValid returns true for a real-looking client secret`() {
        assertTrue(GitHubCredentialsValidator.isClientSecretValid("abcdef1234567890abcdef1234567890abcdef12"))
    }

    // ── Constant values ────────────────────────────────────────────────────────

    @Test
    fun `PLACEHOLDER_CLIENT_ID matches the default in app build gradle`() {
        // This constant must stay in sync with the Gradle fallback value so that
        // the runtime guard in LoginActivity catches unconfigured CI builds.
        assert(GitHubCredentialsValidator.PLACEHOLDER_CLIENT_ID == "REPLACE_WITH_YOUR_CLIENT_ID")
    }

    @Test
    fun `PLACEHOLDER_CLIENT_SECRET matches the default in app build gradle`() {
        assert(GitHubCredentialsValidator.PLACEHOLDER_CLIENT_SECRET == "REPLACE_WITH_YOUR_CLIENT_SECRET")
    }
}
