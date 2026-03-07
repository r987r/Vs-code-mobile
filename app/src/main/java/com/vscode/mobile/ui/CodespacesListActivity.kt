package com.vscode.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vscode.mobile.R
import com.vscode.mobile.auth.GitHubAuthManager
import com.vscode.mobile.auth.SecureTokenStorage
import com.vscode.mobile.databinding.ActivityCodespacesListBinding
import com.vscode.mobile.model.Codespace
import com.vscode.mobile.util.ApiException
import com.vscode.mobile.util.GitHubApiClient
import kotlinx.coroutines.launch

/**
 * Displays the user's GitHub Codespaces. Tapping a codespace opens [MainActivity]
 * with the WebView pointed at that codespace's web URL.
 */
class CodespacesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodespacesListBinding
    private lateinit var tokenStorage: SecureTokenStorage
    private lateinit var authManager: GitHubAuthManager
    private lateinit var adapter: CodespacesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodespacesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        tokenStorage = SecureTokenStorage(applicationContext)
        authManager = GitHubAuthManager(applicationContext, tokenStorage)
        adapter = CodespacesAdapter { codespace -> openCodespace(codespace) }

        binding.rvCodespaces.apply {
            layoutManager = LinearLayoutManager(this@CodespacesListActivity)
            adapter = this@CodespacesListActivity.adapter
        }

        binding.swipeRefresh.setOnRefreshListener { loadCodespaces() }

        loadCodespaces()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_codespaces, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_sign_out -> {
            signOut()
            true
        }
        R.id.action_refresh -> {
            loadCodespaces()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadCodespaces() {
        val token = tokenStorage.getAccessToken()
        if (token == null) {
            signOut()
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            try {
                val codespaces = GitHubApiClient(token).listCodespaces()
                showCodespaces(codespaces)
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    signOut()
                } else {
                    showError(getString(R.string.error_loading_codespaces))
                }
            } catch (e: Exception) {
                showError(getString(R.string.error_loading_codespaces))
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showCodespaces(codespaces: List<Codespace>) {
        showLoading(false)
        if (codespaces.isEmpty()) {
            binding.tvEmptyState.text = getString(R.string.no_codespaces)
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvCodespaces.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvCodespaces.visibility = View.VISIBLE
            adapter.submitList(codespaces)
        }
    }

    private fun showError(message: String) {
        showLoading(false)
        binding.tvEmptyState.text = message
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.rvCodespaces.visibility = View.GONE
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    private fun openCodespace(codespace: Codespace) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_CODESPACE_URL, codespace.webUrl)
            putExtra(MainActivity.EXTRA_CODESPACE_NAME, codespace.label)
        }
        startActivity(intent)
    }

    private fun signOut() {
        authManager.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        authManager.dispose()
    }
}
