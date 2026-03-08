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
 * Single-activity Codespace viewer.
 *
 * No OAuth credentials needed — the user signs in via GitHub's web UI
 * directly inside the WebView. Cookies persist across sessions so the
 * user stays signed in.
 *
 * The app detects the current page from the URL:
 *   - Codespace list  → hides the Activity Bar, shows a standard toolbar
 *   - Active codespace → shows the VS Code Activity Bar + view-mode toggle
 *
 * A toggle FAB lets the user switch between the codespace list and the
 * last-visited codespace (and vice-versa).
 *
 * Layout:
 *   ┌──────────────────────────────────────────┐
 *   │ Toolbar (page title + menu)              │
 *   ├───────┬──────────────────────────────────┤
 *   │ Left  │  WebView (GitHub / VS Code web)  │
 *   │ Tab   │                                  │
 *   │ Bar   │                                  │
 *   │       │                                  │
 *   └───────┴──────────────────────────────────┘
 *   │ Status bar                               │
 *   └──────────────────────────────────────────┘
 *           [Toggle FAB]
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        /** Entry point: the GitHub Codespaces dashboard. */
        const val CODESPACES_LIST_URL = "https://github.com/codespaces"

        /** URL patterns that indicate we are inside a live codespace. */
        private val CODESPACE_URL_PATTERNS = listOf(
            ".github.dev",
            ".app.github.dev",
            ".githubpreview.dev"
        )
    }

    private lateinit var binding: ActivityMainBinding

    private var currentViewMode = ViewMode.MOBILE
    private var currentPanel = CodespacePanel.EXPLORER

    /** Remembers the last codespace URL so the toggle can jump back. */
    private var lastCodespaceUrl: String? = null

    /** Whether the WebView is currently showing a live codespace. */
    private var isInCodespace = false

    // ── Tab binding helpers ────────────────────────────────────────────────────

    private val tabBindings: Map<CodespacePanel, ItemActivityTabBinding> by lazy {
        mapOf(
            CodespacePanel.EXPLORER   to binding.tabExplorer,
            CodespacePanel.SEARCH     to binding.tabSearch,
            CodespacePanel.GIT        to binding.tabGit,
            CodespacePanel.EXTENSIONS to binding.tabExtensions,
            CodespacePanel.COPILOT    to binding.tabCopilot,
            CodespacePanel.TERMINAL   to binding.tabTerminal,
        )
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupWebView()
        setupActivityBar()
        setupToggleFab()
        setupBackNavigation()

        // Load the codespace list — user signs in via GitHub web if needed
        loadUrl(CODESPACES_LIST_URL)
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
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            @Suppress("DEPRECATION")
            saveFormData = false
            @Suppress("DEPRECATION")
            savePassword = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = buildUserAgent()
        }

        // Accept cookies so the GitHub session persists across app launches
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = CodespacesWebViewClient(
            onPageStarted = { _ ->
                runOnUiThread { showLoading(true) }
            },
            onPageFinished = { url ->
                runOnUiThread {
                    showLoading(false)
                    onPageChanged(url)
                }
            },
            onPageError = { _, description, _ -> showError(description) },
            onBlockedNavigation = { url ->
                runOnUiThread { openInSystemBrowser(url) }
            },
            onInjectJavaScript = { wv -> injectMobileJs(wv) }
        )

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                runOnUiThread { updateToolbarFromMetadata(title, view.url) }
            }
        }
    }

    private fun buildUserAgent(): String {
        val androidVersion = Build.VERSION.RELEASE
        val chromeVersion = "121.0.0.0"
        return "Mozilla/5.0 (Linux; Android $androidVersion; Mobile) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$chromeVersion Mobile Safari/537.36 " +
                "VSCodeMobile/1.0"
    }

    // ── Page change detection ──────────────────────────────────────────────────

    /**
     * Called after every page load. Inspects the URL to decide whether
     * the user is on the codespace list or inside a live codespace,
     * then toggles the Activity Bar and FAB accordingly.
     */
    private fun onPageChanged(url: String) {
        isInCodespace = isCodespaceUrl(url)

        if (isInCodespace) {
            lastCodespaceUrl = url
        }

        // Show / hide Activity Bar
        binding.activityBar.visibility = if (isInCodespace) View.VISIBLE else View.GONE

        // Update toggle FAB icon & description
        updateToggleFab()

        // Update status bar
        if (isInCodespace) {
            val name = extractCodespaceName(url)
            binding.tvStatusLeft.text = "⚡ $name"
            binding.tvStatusRight.text = currentViewMode.name.lowercase()
                .replaceFirstChar { it.uppercase() }
            binding.statusBar.visibility = View.VISIBLE
        } else {
            binding.statusBar.visibility = View.GONE
        }

        // Invalidate options menu so view-mode item shows/hides correctly
        invalidateOptionsMenu()
    }

    /** Returns true when the URL belongs to a live codespace (*.github.dev, etc.). */
    internal fun isCodespaceUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = try { Uri.parse(url).host } catch (_: Exception) { null } ?: return false
        return CODESPACE_URL_PATTERNS.any { pattern -> host.endsWith(pattern) }
    }

    /** Extracts a human-readable codespace name from the URL. */
    private fun extractCodespaceName(url: String): String {
        return try {
            val host = Uri.parse(url).host ?: return url
            // e.g. "my-codespace-abc123.github.dev" → "my-codespace-abc123"
            host.substringBefore(".")
        } catch (_: Exception) {
            url
        }
    }

    /** Parses the page title and URL to update the toolbar. */
    private fun updateToolbarFromMetadata(title: String?, url: String?) {
        val toolbar = binding.toolbar
        if (isCodespaceUrl(url)) {
            toolbar.title = title?.takeIf { it.isNotBlank() } ?: extractCodespaceName(url ?: "")
            toolbar.subtitle = getString(R.string.codespace_active)
        } else if (url?.contains("github.com/login") == true) {
            toolbar.title = getString(R.string.sign_in_title)
            toolbar.subtitle = null
        } else {
            toolbar.title = title?.takeIf { it.isNotBlank() } ?: getString(R.string.codespaces_title)
            toolbar.subtitle = null
        }
    }

    // ── Tab / Activity Bar setup ───────────────────────────────────────────────

    private fun setupActivityBar() {
        configureTab(CodespacePanel.EXPLORER, R.drawable.ic_tab_explorer, getString(R.string.tab_explorer))
        configureTab(CodespacePanel.SEARCH, R.drawable.ic_tab_search, getString(R.string.tab_search))
        configureTab(CodespacePanel.GIT, R.drawable.ic_tab_git, getString(R.string.tab_git))
        configureTab(CodespacePanel.EXTENSIONS, R.drawable.ic_tab_extensions, getString(R.string.tab_extensions))
        configureTab(CodespacePanel.COPILOT, R.drawable.ic_tab_copilot, getString(R.string.tab_copilot))
        configureTab(CodespacePanel.TERMINAL, R.drawable.ic_tab_terminal, getString(R.string.tab_terminal))

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
        setActiveTab(panel)
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

    // ── Toggle FAB ─────────────────────────────────────────────────────────────

    private fun setupToggleFab() {
        binding.fabToggle.setOnClickListener { toggleView() }
        updateToggleFab()
    }

    /** Updates the FAB icon and content description based on the current state. */
    private fun updateToggleFab() {
        if (isInCodespace) {
            binding.fabToggle.setImageResource(android.R.drawable.ic_menu_agenda)
            binding.fabToggle.contentDescription = getString(R.string.toggle_to_list)
        } else {
            binding.fabToggle.setImageResource(android.R.drawable.ic_menu_edit)
            binding.fabToggle.contentDescription = getString(R.string.toggle_to_codespace)
        }
        // Only show FAB when there is somewhere to toggle to
        binding.fabToggle.visibility = if (isInCodespace || lastCodespaceUrl != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /** Switches between the codespace list and the last-visited codespace. */
    private fun toggleView() {
        if (isInCodespace) {
            loadUrl(CODESPACES_LIST_URL)
        } else if (lastCodespaceUrl != null) {
            loadUrl(lastCodespaceUrl!!)
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

    private fun loadUrl(url: String) {
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
        if (!isCodespaceUrl(webView.url)) return
        lifecycleScope.launch {
            delay(800L)
            val script = JavaScriptInjector.buildMobileBootstrap(currentViewMode)
            webView.evaluateJavascript(script, null)
        }
    }

    // ── View Mode bottom sheet ─────────────────────────────────────────────────

    private fun showViewModeSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetViewModeBinding.inflate(layoutInflater)

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

        binding.webView.evaluateJavascript(
            JavaScriptInjector.buildSetViewModeScript(mode), null
        )

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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_view_mode)?.isVisible = isInCodespace
        menu.findItem(R.id.action_clear_session)?.isVisible = true
        return super.onPrepareOptionsMenu(menu)
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
        R.id.action_clear_session -> {
            clearSessionAndReload()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * Clears all cookies (signs the user out of GitHub in the WebView)
     * and reloads the codespace list page.
     */
    private fun clearSessionAndReload() {
        CookieManager.getInstance().removeAllCookies { _ ->
            runOnUiThread {
                lastCodespaceUrl = null
                isInCodespace = false
                loadUrl(CODESPACES_LIST_URL)
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        // Flush cookies so the session persists if the app is killed
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }
}
