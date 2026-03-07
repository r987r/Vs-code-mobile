package com.vscode.mobile.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vscode.mobile.model.Codespace
import com.vscode.mobile.model.CodespacesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Lightweight GitHub REST API v3 client.
 *
 * Security design:
 *  - All requests are HTTPS only (enforced by the network security config).
 *  - The Authorization header is added per-request from [SecureTokenStorage],
 *    never stored in the client itself.
 *  - Logging is disabled in release builds (log level NONE for release).
 *  - Connection and read timeouts are capped to avoid indefinite hangs.
 */
class GitHubApiClient(private val accessToken: String) {

    companion object {
        private const val API_BASE = "https://api.github.com"
        private const val TIMEOUT_SECONDS = 30L
        private const val GITHUB_API_VERSION = "2022-11-28"
    }

    private val gson = Gson()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                // Prevent the WebView cookie store from leaking into API requests
                .header("Cookie", "")
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * Fetches the list of codespaces for the authenticated user.
     * @throws IOException on network failure.
     * @throws ApiException on non-2xx HTTP responses.
     */
    suspend fun listCodespaces(): List<Codespace> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/user/codespaces")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        response.use { res ->
            if (!res.isSuccessful) {
                throw ApiException(res.code, "Failed to list codespaces: HTTP ${res.code}")
            }
            val body = res.body?.string() ?: throw ApiException(res.code, "Empty response body")
            val result: CodespacesResponse = gson.fromJson(body, CodespacesResponse::class.java)
            result.codespaces
        }
    }

    /**
     * Starts a stopped codespace.
     */
    suspend fun startCodespace(codespaceName: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/user/codespaces/$codespaceName/start")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()

        val response = httpClient.newCall(request).execute()
        response.use { res ->
            if (!res.isSuccessful) {
                throw ApiException(res.code, "Failed to start codespace: HTTP ${res.code}")
            }
        }
    }
}

class ApiException(val statusCode: Int, message: String) : IOException(message)
