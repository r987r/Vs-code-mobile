package com.vscode.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vscode.mobile.BuildConfig
import com.vscode.mobile.R
import com.vscode.mobile.auth.GitHubAuthManager
import com.vscode.mobile.auth.SecureTokenStorage
import com.vscode.mobile.databinding.ActivityLoginBinding

/**
 * Sign-in screen.
 *
 * Initiates GitHub OAuth2 via [GitHubAuthManager] (AppAuth + PKCE).
 * On successful token exchange the user is forwarded to [CodespacesListActivity].
 *
 * Security notes:
 *  - Client ID is read from BuildConfig (set via local.properties; never hard-coded here).
 *  - Client Secret is required by GitHub's non-PKCE token endpoint; it is also read from
 *    BuildConfig. In a production app you would proxy the token exchange through your own
 *    backend to avoid shipping the secret in the APK.
 *  - We never log tokens.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: GitHubAuthManager
    private lateinit var tokenStorage: SecureTokenStorage

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        authManager.handleAuthResponse(
            data = result.data,
            clientId = getClientId(),
            clientSecret = getClientSecret(),
            onSuccess = { _ ->
                setLoading(false)
                navigateToList()
            },
            onError = { message ->
                runOnUiThread {
                    setLoading(false)
                    showError(message)
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenStorage = SecureTokenStorage(applicationContext)
        authManager = GitHubAuthManager(applicationContext, tokenStorage)

        // If somehow already signed in, skip straight to the list
        if (authManager.isSignedIn()) {
            navigateToList()
            return
        }

        binding.btnSignIn.setOnClickListener {
            startAuthFlow()
        }
    }

    private fun startAuthFlow() {
        if (!isClientIdConfigured()) {
            showError(getString(R.string.error_client_id_not_configured))
            return
        }
        setLoading(true)
        try {
            val intent = authManager.buildAuthIntent(getClientId())
            authLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start auth", e)
            showError(getString(R.string.auth_error_message))
            setLoading(false)
        }
    }

    /**
     * Returns true only when a real client ID has been injected at build time.
     * The placeholder value means local.properties / CI secrets were not configured.
     */
    private fun isClientIdConfigured(): Boolean =
        BuildConfig.GITHUB_CLIENT_ID != "REPLACE_WITH_YOUR_CLIENT_ID"

    // onActivityResult removed — handled by authLauncher above

    private fun navigateToList() {
        startActivity(Intent(this, CodespacesListActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSignIn.isEnabled = !loading
        binding.btnSignIn.text = if (loading) getString(R.string.signing_in) else getString(R.string.btn_sign_in)
        binding.tvError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    /**
     * Read the GitHub OAuth App Client ID.
     * Set GH_CLIENT_ID in local.properties; it is injected into BuildConfig
     * via the app/build.gradle buildConfigField.
     *
     * Example local.properties entry:
     *   GH_CLIENT_ID=Ov23liXXXXXXXXXX
     */
    private fun getClientId(): String = BuildConfig.GITHUB_CLIENT_ID

    private fun getClientSecret(): String = BuildConfig.GITHUB_CLIENT_SECRET

    override fun onDestroy() {
        super.onDestroy()
        authManager.dispose()
    }
}
