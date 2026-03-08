package com.vscode.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.vscode.mobile.databinding.ActivitySplashBinding

/**
 * Splash / launch screen.
 * Shown briefly then routes the user directly to [MainActivity].
 *
 * No credential or token checks — authentication is handled
 * by the user signing in through the GitHub web UI in the WebView.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 800L)
    }
}
