package com.vscode.mobile.auth

/**
 * Validates GitHub OAuth App credentials before attempting authentication.
 *
 * Centralises all placeholder/empty detection in one place so that
 * [com.vscode.mobile.ui.LoginActivity] and unit tests share identical logic.
 */
object GitHubCredentialsValidator {

    /** Build-time default injected when GH_CLIENT_ID is absent from local.properties / CI secrets. */
    const val PLACEHOLDER_CLIENT_ID = "REPLACE_WITH_YOUR_CLIENT_ID"

    /** Build-time default injected when GH_CLIENT_SECRET is absent from local.properties / CI secrets. */
    const val PLACEHOLDER_CLIENT_SECRET = "REPLACE_WITH_YOUR_CLIENT_SECRET"

    /**
     * Returns `true` only when [clientId] looks like a real OAuth App client ID.
     *
     * A value is considered unconfigured if it is blank or equals the placeholder
     * that `app/build.gradle` injects when no real ID is supplied.
     */
    fun isClientIdValid(clientId: String): Boolean =
        clientId.isNotBlank() && clientId != PLACEHOLDER_CLIENT_ID

    /**
     * Returns `true` only when [clientSecret] looks like a real OAuth App secret.
     *
     * Mirrors the logic in [isClientIdValid].
     */
    fun isClientSecretValid(clientSecret: String): Boolean =
        clientSecret.isNotBlank() && clientSecret != PLACEHOLDER_CLIENT_SECRET
}
