package com.vscode.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.vscode.mobile.auth.GitHubAuthManager
import com.vscode.mobile.auth.SecureTokenStorage
import com.vscode.mobile.databinding.ActivitySplashBinding

/**
 * Splash / launch screen.
 * Shown briefly while the app decides where to route the user:
 *   - If signed in → [CodespacesListActivity]
 *   - Otherwise   → [LoginActivity]
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var tokenStorage: SecureTokenStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenStorage = SecureTokenStorage(applicationContext)

        Handler(Looper.getMainLooper()).postDelayed({
            navigate()
        }, 800L)
    }

    private fun navigate() {
        val dest = if (tokenStorage.hasTokens() && tokenStorage.isAccessTokenValid()) {
            Intent(this, CodespacesListActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(dest)
        finish()
    }
}
