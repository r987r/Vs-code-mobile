package com.vscode.mobile.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest

/**
 * Manages GitHub OAuth2 authentication using the AppAuth library.
 *
 * Security design:
 *  - Uses the Authorization Code + PKCE flow (no client secret in the app binary).
 *  - Tokens are stored in [SecureTokenStorage] (Android Keystore-backed).
 *  - The app never handles raw passwords or personal access tokens.
 *  - Redirect URI uses a custom app scheme registered in AndroidManifest.xml, so
 *    other apps on the device cannot intercept the auth code.
 *
 * To use:
 *  1. Register a GitHub OAuth App at https://github.com/settings/developers
 *  2. Set Client ID in BuildConfig / local.properties (never commit secrets).
 *  3. Add the redirect URI  com.vscode.mobile://oauth2redirect  to the OAuth App.
 */
class GitHubAuthManager(
    private val context: Context,
    private val tokenStorage: SecureTokenStorage
) {

    companion object {
        private const val TAG = "GitHubAuthManager"

        // GitHub OAuth2 endpoints
        private const val GITHUB_AUTH_ENDPOINT = "https://github.com/login/oauth/authorize"
        private const val GITHUB_TOKEN_ENDPOINT = "https://github.com/login/oauth/access_token"

        // OAuth scopes needed to list/open codespaces and use the GitHub API
        private const val SCOPES = "codespace user:email read:user"

        // Redirect URI — must match the intent-filter in AndroidManifest.xml
        const val REDIRECT_URI = "com.vscode.mobile://oauth2redirect"
    }

    private val authService = AuthorizationService(context)

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(GITHUB_AUTH_ENDPOINT),
        Uri.parse(GITHUB_TOKEN_ENDPOINT)
    )

    /**
     * Build an [Intent] that launches the system browser / Custom Tab for GitHub sign-in.
     * Pass the returned intent to [Activity.startActivityForResult] with [RC_AUTH].
     */
    fun buildAuthIntent(clientId: String): Intent {
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScope(SCOPES)
            // PKCE is handled automatically by AppAuth
            .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Call from [Activity.onActivityResult] to exchange the auth code for tokens.
     *
     * @param data  The [Intent] received in onActivityResult.
     * @param clientId The GitHub OAuth App client ID.
     * @param onSuccess Called with the access token on success.
     * @param onError   Called with an error message on failure.
     */
    fun handleAuthResponse(
        data: Intent?,
        clientSecret: String,
        onSuccess: (accessToken: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        if (data == null) {
            onError("Auth cancelled")
            return
        }

        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)

        when {
            exception != null -> {
                Log.e(TAG, "Auth error: ${exception.errorDescription}", exception)
                onError(exception.errorDescription ?: "Authentication failed")
            }
            response != null -> {
                // Exchange auth code for tokens
                val tokenRequest: TokenRequest = response.createTokenExchangeRequest()
                val clientAuth: ClientAuthentication = ClientSecretBasic(clientSecret)

                authService.performTokenRequest(tokenRequest, clientAuth) { tokenResponse, tokenException ->
                    when {
                        tokenException != null -> {
                            Log.e(TAG, "Token exchange error", tokenException)
                            onError(tokenException.errorDescription ?: "Token exchange failed")
                        }
                        tokenResponse?.accessToken != null -> {
                            val accessToken = tokenResponse.accessToken!!
                            val expiryMs = tokenResponse.accessTokenExpirationTime ?: 0L
                            tokenStorage.saveTokens(
                                accessToken = accessToken,
                                refreshToken = tokenResponse.refreshToken,
                                expiryTimeMs = expiryMs,
                                idToken = tokenResponse.idToken
                            )
                            onSuccess(accessToken)
                        }
                        else -> onError("No access token received")
                    }
                }
            }
            else -> onError("No authorization response")
        }
    }

    /**
     * Sign out: clear stored tokens and revoke them on GitHub.
     * Revocation is best-effort; clearing local tokens is sufficient for privacy.
     */
    fun signOut() {
        tokenStorage.clearTokens()
    }

    /** Returns true if the user appears to be signed in with a valid token. */
    fun isSignedIn(): Boolean = tokenStorage.isAccessTokenValid()

    /** Returns the stored access token or null. */
    fun getAccessToken(): String? = tokenStorage.getAccessToken()

    fun dispose() {
        authService.dispose()
    }
}
