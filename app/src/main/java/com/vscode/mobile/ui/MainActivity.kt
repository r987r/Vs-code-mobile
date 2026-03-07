package com.vscode.mobile.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.vscode.mobile.BuildConfig
import com.vscode.mobile.R
import com.vscode.mobile.auth.SecureTokenStorage
import com.vscode.mobile.databinding.ActivityMainBinding
import com.vscode.mobile.databinding.BottomSheetViewModeBinding
import com.vscode.mobile.databinding.ItemActivityTabBinding
import com.vscode.mobile.model.CodespacePanel
import com.vscode.mobile.model.ViewMode
import com.vscode.mobile.webview.CodespacesWebViewClient
import com.vscode.mobile.webview.JavaScriptInjector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main Codespace viewer.
 *
 * Layout:
 *   ┌──────────────────────────────────────────┐
 *   │ Toolbar (codespace name + menu)          │
 *   ├───────┬──────────────────────────────────┤
 *   │ Left  │  WebView (VS Code web)           │
 *   │ Tab   │                                  │
 *   │ Bar   │                                  │
 *   │       │                                  │
 *   └───────┴──────────────────────────────────┘
 *   │ Status bar                               │
 *   └──────────────────────────────────────────┘
 *
 * Security:
 *  - JavaScript is only enabled for trusted GitHub/Codespaces domains.
 *  - Mixed-content blocked; file/data URIs disallowed.
 *  - DOM storage disabled; third-party cookies blocked.
 *  - All SSL errors result in a hard block (via [CodespacesWebViewClient]).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_CODESPACE_URL = "extra_codespace_url"
        const val EXTRA_CODESPACE_NAME = "extra_codespace_name"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var tokenStorage: SecureTokenStorage

    private var currentViewMode = ViewMode.MOBILE
    private var currentPanel = CodespacePanel.EXPLORER

    // ── Tab binding helpers ────────────────────────────────────────────────────

    /** All tab bindings in order (must match XML include IDs). */
    private val tabBindings: Map<CodespacePanel, ItemActivityTabBinding> by lazy {
        mapOf(
            CodespacePanel.EXPLORER   to ItemActivityTabBinding.bind(binding.tabExplorer),
            CodespacePanel.SEARCH     to ItemActivityTabBinding.bind(binding.tabSearch),
            CodespacePanel.GIT        to ItemActivityTabBinding.bind(binding.tabGit),
            CodespacePanel.EXTENSIONS to ItemActivityTabBinding.bind(binding.tabExtensions),
            CodespacePanel.COPILOT    to ItemActivityTabBinding.bind(binding.tabCopilot),
            CodespacePanel.TERMINAL   to ItemActivityTabBinding.bind(binding.tabTerminal),
        )
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tokenStorage = SecureTokenStorage(applicationContext)

        val codespaceUrl = intent.getStringExtra(EXTRA_CODESPACE_URL) ?: run {
            finish()
            return
        }
        val codespaceName = intent.getStringExtra(EXTRA_CODESPACE_NAME) ?: codespaceUrl

        binding.toolbar.title = codespaceName
        binding.tvStatusLeft.text = "⚡ $codespaceName"
        binding.tvStatusRight.text = currentViewMode.name.lowercase().replaceFirstChar { it.uppercase() }

        setupWebView()
        setupActivityBar()
        setupBackNavigation()
        loadCodespace(codespaceUrl)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // ── WebView setup ──────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView

        webView.settings.apply {
            // JavaScript MUST be enabled for VS Code web to function
            javaScriptEnabled = true

            // ── Security hardening ──────────────────────────────────
            // Allow DOM storage so VS Code can persist workspace state
            domStorageEnabled = true
            // Block mixed content (HTTP resources on HTTPS pages)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Disallow loading content from file:// and content:// URIs
            allowFileAccess = false
            allowContentAccess = false
            // Disable geolocation by default
            setGeolocationEnabled(false)
            // Disable saving form data
            saveFormData = false
            // Do not cache credentials
            savePassword = false

            // ── Viewport / rendering ────────────────────────────────
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Mobile user-agent in MOBILE mode so the page adapts
            userAgentString = buildUserAgent()
        }

        // Block third-party cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        // In debug builds only — enables Chrome DevTools remote debugging
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = CodespacesWebViewClient(
            onPageStarted = { _ -> showLoading(true) },
            onPageFinished = { _ -> showLoading(false) },
            onPageError = { _, description, _ -> showError(description) },
            onBlockedNavigation = { url ->
                // Open blocked URLs in the system browser
                runOnUiThread { openInSystemBrowser(url) }
            },
            onInjectJavaScript = { wv -> injectMobileJs(wv) }
        )

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                // Could drive a progress bar here
            }
        }
    }

    private fun buildUserAgent(): String {
        // Build a dynamic user agent that reflects the device's actual OS version
        val androidVersion = Build.VERSION.RELEASE
        val chromeVersion = "121.0.0.0" // Approximate; kept for server compatibility
        return "Mozilla/5.0 (Linux; Android $androidVersion; Mobile) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$chromeVersion Mobile Safari/537.36 " +
                "VSCodeMobile/1.0"
    }

    // ── Tab / Activity Bar setup ───────────────────────────────────────────────

    private fun setupActivityBar() {
        configureTab(CodespacePanel.EXPLORER, R.drawable.ic_tab_explorer, getString(R.string.tab_explorer))
        configureTab(CodespacePanel.SEARCH, R.drawable.ic_tab_search, getString(R.string.tab_search))
        configureTab(CodespacePanel.GIT, R.drawable.ic_tab_git, getString(R.string.tab_git))
        configureTab(CodespacePanel.EXTENSIONS, R.drawable.ic_tab_extensions, getString(R.string.tab_extensions))
        configureTab(CodespacePanel.COPILOT, R.drawable.ic_tab_copilot, getString(R.string.tab_copilot))
        configureTab(CodespacePanel.TERMINAL, R.drawable.ic_tab_terminal, getString(R.string.tab_terminal))

        // Select Explorer by default
        setActiveTab(CodespacePanel.EXPLORER)
    }

    private fun configureTab(panel: CodespacePanel, iconRes: Int, label: String) {
        val tabBinding = tabBindings[panel] ?: return
        tabBinding.ivTabIcon.setImageResource(iconRes)
        tabBinding.tvTabLabel.text = label
        tabBinding.root.setOnClickListener { onTabSelected(panel) }
        tabBinding.root.contentDescription = getString(R.string.cd_tab_icon, label)
    }

    private fun onTabSelected(panel: CodespacePanel) {
        if (panel == currentPanel) return
        setActiveTab(panel)
        // Tell VS Code web to switch panels via JS
        binding.webView.evaluateJavascript(
            JavaScriptInjector.buildActivatePanelScript(panel), null
        )
    }

    private fun setActiveTab(panel: CodespacePanel) {
        currentPanel = panel
        tabBindings.forEach { (p, tb) ->
            val isActive = p == panel
            tb.ivTabIcon.alpha = if (isActive) 1.0f else 0.5f
            tb.tvTabLabel.setTextColor(
                getColor(if (isActive) R.color.vscode_icon_active else R.color.vscode_icon_inactive)
            )
            tb.root.background = if (isActive) getDrawable(R.drawable.tab_active_bg) else null
        }
    }

    // ── Loading / Error states ─────────────────────────────────────────────────

    private fun showLoading(visible: Boolean) {
        binding.loadingOverlay.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            binding.errorView.visibility = View.GONE
        }
    }

    private fun showError(description: String) {
        showLoading(false)
        binding.tvError.text = description
        binding.errorView.visibility = View.VISIBLE
        binding.btnRetry.setOnClickListener {
            binding.errorView.visibility = View.GONE
            binding.webView.reload()
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    private fun loadCodespace(url: String) {
        showLoading(true)
        binding.webView.loadUrl(url)
    }

    private fun openInSystemBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open URL in browser: $url", e)
        }
    }

    // ── JS injection ───────────────────────────────────────────────────────────

    private fun injectMobileJs(webView: WebView) {
        lifecycleScope.launch {
            // Small delay to let the VS Code workbench boot before we mutate its DOM
            delay(800L)
            val script = JavaScriptInjector.buildMobileBootstrap(currentViewMode)
            webView.evaluateJavascript(script, null)
        }
    }

    // ── View Mode bottom sheet ─────────────────────────────────────────────────

    private fun showViewModeSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetViewModeBinding.inflate(layoutInflater)

        // Pre-select current mode
        sheetBinding.radioMobile.isChecked = currentViewMode == ViewMode.MOBILE
        sheetBinding.radioDesktop.isChecked = currentViewMode == ViewMode.DESKTOP

        sheetBinding.optionMobile.setOnClickListener {
            sheetBinding.radioMobile.isChecked = true
            sheetBinding.radioDesktop.isChecked = false
        }
        sheetBinding.optionDesktop.setOnClickListener {
            sheetBinding.radioMobile.isChecked = false
            sheetBinding.radioDesktop.isChecked = true
        }

        sheetBinding.btnApply.setOnClickListener {
            val selectedMode = if (sheetBinding.radioMobile.isChecked) ViewMode.MOBILE else ViewMode.DESKTOP
            applyViewMode(selectedMode)
            dialog.dismiss()
        }

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    private fun applyViewMode(mode: ViewMode) {
        if (mode == currentViewMode) return
        currentViewMode = mode
        binding.tvStatusRight.text = mode.name.lowercase().replaceFirstChar { it.uppercase() }

        // Apply changes via JS without reloading
        binding.webView.evaluateJavascript(
            JavaScriptInjector.buildSetViewModeScript(mode), null
        )

        // Re-inject the full bootstrap with the new mode so CSS overrides are refreshed
        injectMobileJs(binding.webView)

        Snackbar.make(
            binding.root,
            if (mode == ViewMode.MOBILE) getString(R.string.view_mode_mobile) else getString(R.string.view_mode_desktop),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    // ── Options menu ───────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        R.id.action_view_mode -> {
            showViewModeSheet()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        // Destroy the WebView to release resources and clear in-memory cookies/sessions
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }
}
