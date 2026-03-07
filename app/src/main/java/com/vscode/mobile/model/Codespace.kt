package com.vscode.mobile.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a GitHub Codespace returned by the GitHub API.
 */
data class Codespace(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("environment_id") val environmentId: String?,
    @SerializedName("state") val state: String,
    @SerializedName("url") val url: String,
    @SerializedName("web_url") val webUrl: String,
    @SerializedName("git_status") val gitStatus: GitStatus?,
    @SerializedName("repository") val repository: Repository?,
    @SerializedName("machine") val machine: Machine?
) {
    val isAvailable: Boolean get() = state.equals("Available", ignoreCase = true)
    val isStarting: Boolean get() = state.equals("Starting", ignoreCase = true)
    val isStopped: Boolean get() = state.equals("Shutdown", ignoreCase = true)

    /** Human-readable name: prefer displayName, fall back to name. */
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: name
}

data class GitStatus(
    @SerializedName("ref") val ref: String?,
    @SerializedName("commit_id") val commitId: String?,
    @SerializedName("has_uncommitted_changes") val hasUncommittedChanges: Boolean
)

data class Repository(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("html_url") val htmlUrl: String
)

data class Machine(
    @SerializedName("name") val name: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("cpus") val cpus: Int,
    @SerializedName("memory_in_bytes") val memoryInBytes: Long
)

/**
 * API response wrapper for the list-codespaces endpoint.
 */
data class CodespacesResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("codespaces") val codespaces: List<Codespace>
)
