package com.vscode.mobile

import org.junit.Test

/**
 * Tests for the domain allowlist logic mirroring [com.vscode.mobile.webview.CodespacesWebViewClient].
 *
 * The isTrustedHost method is private in the production class, so we test the same logic
 * here using an identical allowlist and helper.  Keep these in sync with the production class.
 */
class CodespacesWebViewClientTest {

    /**
     * Mirror of the allowlist defined in [CodespacesWebViewClient].
     * Keep in sync with the production class.
     */
    private val allowedHosts = setOf(
        "github.com",
        "github.dev",
        "app.github.dev",
        "githubpreview.dev",
        "github.githubassets.com",
        "avatars.githubusercontent.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
    )

    private fun isTrustedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    @Test
    fun `github-com is trusted`() {
        assert(isTrustedHost("github.com")) { "github.com should be trusted" }
    }

    @Test
    fun `api-github-com is trusted via subdomain`() {
        assert(isTrustedHost("api.github.com")) { "api.github.com should be trusted" }
    }

    @Test
    fun `codespace subdomain of app-github-dev is trusted`() {
        assert(isTrustedHost("mycodespace.app.github.dev"))
    }

    @Test
    fun `github-dev is trusted`() {
        assert(isTrustedHost("github.dev"))
    }

    @Test
    fun `deep subdomain of githubpreview-dev is trusted`() {
        assert(isTrustedHost("abc123.githubpreview.dev"))
    }

    @Test
    fun `malicious-github-com is not trusted`() {
        assert(!isTrustedHost("malicious-github.com")) { "evil domain must not be trusted" }
    }

    @Test
    fun `evil-com is not trusted`() {
        assert(!isTrustedHost("evil.com"))
    }

    @Test
    fun `null host is not trusted`() {
        assert(!isTrustedHost(null))
    }

    @Test
    fun `empty host is not trusted`() {
        assert(!isTrustedHost(""))
    }

    @Test
    fun `github-com-evil-com is not trusted`() {
        assert(!isTrustedHost("github.com.evil.com"))
    }
}
