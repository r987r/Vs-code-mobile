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
                 * Activate a VS Code panel by clicking the real activity-bar
                 * button inside the WebView.  Multiple selector strategies are
                 * tried so the script works across VS Code web versions.
                 *
                 * @param {string} viewId   e.g. 'workbench.view.explorer'
                 * @param {string} label    e.g. 'Explorer'
                 */
                activatePanel: function(viewId, label) {
                    ${buildPanelActivationJs()}
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
        /* Move the activity bar off-screen but keep it in the DOM so
           programmatic .click() still reaches the real buttons. */
        ".activitybar { position: fixed !important; left: -9999px !important; top: -9999px !important; opacity: 0 !important; pointer-events: none !important; }",
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
     * Returns a self-contained JS snippet that activates a specific panel
     * in VS Code.  The snippet does **not** depend on the bootstrap having
     * been injected first – it carries its own multi-strategy activation
     * logic so that it works even if the user taps a tab before the
     * 800 ms injection delay fires.
     */
    fun buildActivatePanelScript(panel: CodespacePanel): String =
        """
        (function() {
            var viewId = '${panel.activityBarId}';
            var label  = '${panel.displayLabel}';
            ${buildPanelActivationJs()}
        })();
        """.trimIndent()

    /**
     * Shared JS body used by both the bootstrap bridge and the standalone
     * panel-activation snippet.  It expects `viewId` and `label` to be in
     * scope when evaluated.
     *
     * Strategies tried (in order):
     *  1. Click the real activity-bar button (multiple selector patterns).
     *  2. Dispatch the matching keyboard shortcut.
     *  3. Fall back to legacy `require()` command service.
     */
    private fun buildPanelActivationJs(): String = """
        // ── Strategy 1: click the real activity-bar button ──────────
        var selectors = [
            '.activitybar [id="' + viewId + '"] .action-label',
            '.activitybar [id="' + viewId + '"]',
            '.activitybar .action-item .action-label[aria-label*="' + label + '"]',
            '.activitybar .action-item[aria-label*="' + label + '"]',
            '[role="tab"][aria-label*="' + label + '"]',
            '.actions-container .action-item[data-id="' + viewId + '"] .action-label',
            '.actions-container .action-item[data-id="' + viewId + '"]',
            '[id="workbench.parts.activitybar"] [data-action-id="' + viewId + '"]'
        ];
        for (var i = 0; i < selectors.length; i++) {
            try {
                var el = document.querySelector(selectors[i]);
                if (el) { el.click(); return; }
            } catch(_) {}
        }

        // ── Strategy 2: dispatch keyboard shortcut ─────────────────
        var shortcuts = {
            'workbench.view.explorer':    {key:'e',code:'KeyE',keyCode:69,ctrl:true,shift:true},
            'workbench.view.search':      {key:'f',code:'KeyF',keyCode:70,ctrl:true,shift:true},
            'workbench.view.scm':         {key:'g',code:'KeyG',keyCode:71,ctrl:true,shift:true},
            'workbench.view.extensions':  {key:'x',code:'KeyX',keyCode:88,ctrl:true,shift:true},
            /* Both terminal IDs map to the same shortcut because the
               commandId and activityBarId differ for the Terminal panel. */
            'workbench.action.terminal.toggleTerminal': {key:'`',code:'Backquote',keyCode:192,ctrl:true,shift:false},
            'workbench.panel.terminal':   {key:'`',code:'Backquote',keyCode:192,ctrl:true,shift:false}
        };
        var sc = shortcuts[viewId];
        if (sc) {
            var target = document.querySelector('.monaco-workbench') || document.body;
            target.dispatchEvent(new KeyboardEvent('keydown', {
                key: sc.key, code: sc.code, keyCode: sc.keyCode, which: sc.keyCode,
                ctrlKey: !!sc.ctrl, metaKey: !!sc.ctrl,
                shiftKey: !!sc.shift,
                bubbles: true, cancelable: true
            }));
        }

        // ── Strategy 3: legacy require() command service ───────────
        try {
            var cmds = typeof require === 'function' && require('vs/workbench/services/commands/common/commandService');
            if (cmds && cmds.executeCommand) cmds.executeCommand(viewId);
        } catch(_) {}
    """.trimIndent()

    /**
     * Returns a JS snippet that switches the in-page view mode.
     */
    fun buildSetViewModeScript(mode: ViewMode): String =
        "window.VSCMobile && window.VSCMobile.setViewMode('${mode.name}');"
}
