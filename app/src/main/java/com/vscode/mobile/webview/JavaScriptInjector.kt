package com.vscode.mobile.webview

import com.vscode.mobile.model.CodespacePanel
import com.vscode.mobile.model.ViewMode

/**
 * Provides JavaScript snippets injected into the Codespace WebView to
 * reshape the VS Code UI for mobile.
 *
 * Security note:
 *  - Only injected into trusted GitHub/Codespaces domains (enforced by [CodespacesWebViewClient]).
 *  - No user data is exfiltrated; scripts only manipulate DOM layout.
 *  - Scripts are plain string constants — no dynamic construction from external input.
 */
object JavaScriptInjector {

    /**
     * Returns the full bootstrap script to inject once the page has loaded.
     * Includes mobile-layout overrides and exposes the VSCMobile bridge.
     */
    fun buildMobileBootstrap(viewMode: ViewMode): String {
        val mobileCssLiteral = if (viewMode == ViewMode.MOBILE) {
            buildMobileCss()
        } else {
            ""
        }

        return """
        (function() {
            'use strict';

            // Prevent double-injection
            if (window.__vscMobileInjected) return;
            window.__vscMobileInjected = true;

            const MODE = '${viewMode.name}';

            // ─── Utility ───────────────────────────────────────────────
            function injectCss(id, css) {
                var existing = document.getElementById(id);
                if (existing) existing.remove();
                var s = document.createElement('style');
                s.id = id;
                s.textContent = css;
                document.head.appendChild(s);
            }

            // ─── Mobile CSS overrides ───────────────────────────────────
            injectCss('vsc-mobile-overrides', ${escapeCssForJs(mobileCssLiteral)});

            // ─── VSCMobile bridge (called from Kotlin via evaluateJavascript) ──
            window.VSCMobile = {
                /**
                 * Activate a VS Code panel by its view ID.
                 * @param {string} viewId  e.g. 'workbench.view.explorer'
                 */
                activatePanel: function(viewId) {
                    try {
                        // Attempt workbench command API (available in VS Code web)
                        var commands = window.require && window.require('vs/workbench/services/commands/common/commandService');
                        if (commands) {
                            commands.executeCommand(viewId);
                            return;
                        }
                    } catch(e) {}

                    // Fallback: click the activity bar icon with matching data-id
                    var btn = document.querySelector(
                        '.actions-container .action-item[data-id="' + viewId + '"]'
                    );
                    if (btn) btn.click();
                },

                /**
                 * Switch the view mode CSS without reloading the page.
                 * @param {string} mode 'MOBILE' | 'DESKTOP'
                 */
                setViewMode: function(mode) {
                    var s = document.getElementById('vsc-mobile-overrides');
                    if (s) s.textContent = (mode === 'DESKTOP') ? '' : s.getAttribute('data-mobile-css') || '';
                },

                /**
                 * Returns basic diagnostics (useful for debugging).
                 */
                getDiagnostics: function() {
                    return JSON.stringify({
                        title: document.title,
                        url: window.location.href,
                        mode: MODE
                    });
                }
            };

        })();
        """.trimIndent()
    }

    /** Builds the mobile CSS override rules. */
    private fun buildMobileCss(): String = listOf(
        /* Enlarge touch targets */
        ".action-label { min-height: 44px; min-width: 44px; }",
        ".monaco-list-row { min-height: 44px; font-size: 15px; }",
        /* Wider scrollbars for touch */
        "::-webkit-scrollbar { width: 12px; height: 12px; }",
        /* Hide the activity bar (we have our own) */
        ".activitybar { display: none; }",
        /* Collapse the left sidebar panel label strip */
        ".part.sidebar > .composite.title { display: none; }",
        /* Make editor font bigger */
        ".monaco-editor .view-line { font-size: 14px; line-height: 22px; }",
        /* Terminal font */
        ".terminal .xterm-rows { font-size: 14px; }",
        /* Breadcrumb bar — hide to save vertical space */
        ".breadcrumbs-control { display: none; }",
        /* Status bar text */
        ".statusbar-item { font-size: 12px; }"
    ).joinToString("\n")

    /**
     * Escapes a CSS string for safe inline inclusion in a JS string literal.
     * Wraps in backtick template literal (safe since CSS contains no backticks).
     */
    private fun escapeCssForJs(css: String): String {
        val escaped = css.replace("`", "\\`").replace("\\$", "\\${'$'}")
        return "`$escaped`"
    }

    /**
     * Returns a JS snippet that activates a specific panel in VS Code.
     */
    fun buildActivatePanelScript(panel: CodespacePanel): String =
        "window.VSCMobile && window.VSCMobile.activatePanel('${panel.activityBarId}');"

    /**
     * Returns a JS snippet that switches the in-page view mode.
     */
    fun buildSetViewModeScript(mode: ViewMode): String =
        "window.VSCMobile && window.VSCMobile.setViewMode('${mode.name}');"
}
