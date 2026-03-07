package com.vscode.mobile.model

/**
 * The rendering mode for the Codespace WebView.
 *
 * [MOBILE]  – JavaScript injection resizes/reorganises panels for touch.
 * [DESKTOP] – The WebView renders VS Code as-is (full desktop layout, user can pan/zoom).
 */
enum class ViewMode {
    MOBILE,
    DESKTOP;

    companion object {
        fun fromString(value: String?): ViewMode =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MOBILE
    }
}

/**
 * Which VS Code activity-bar panel is currently active.
 */
enum class CodespacePanel(
    val commandId: String,
    val activityBarId: String
) {
    EXPLORER("workbench.view.explorer", "workbench.view.explorer"),
    SEARCH("workbench.view.search", "workbench.view.search"),
    GIT("workbench.view.scm", "workbench.view.scm"),
    EXTENSIONS("workbench.view.extensions", "workbench.view.extensions"),
    COPILOT("workbench.panel.chat", "workbench.panel.chat"),
    TERMINAL("workbench.action.terminal.toggleTerminal", "workbench.panel.terminal");

    companion object {
        fun fromOrdinal(index: Int): CodespacePanel =
            values().getOrElse(index) { EXPLORER }
    }
}
