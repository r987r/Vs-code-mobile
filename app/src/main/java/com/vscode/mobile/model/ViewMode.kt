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
 *
 * @property commandId         VS Code command to execute (e.g. via command palette).
 * @property activityBarId     The `id` / `data-id` used on the activity-bar DOM node.
 * @property displayLabel      Human-readable label used to locate the button by `aria-label`.
 */
enum class CodespacePanel(
    val commandId: String,
    val activityBarId: String,
    val displayLabel: String
) {
    EXPLORER("workbench.view.explorer", "workbench.view.explorer", "Explorer"),
    SEARCH("workbench.view.search", "workbench.view.search", "Search"),
    GIT("workbench.view.scm", "workbench.view.scm", "Source Control"),
    EXTENSIONS("workbench.view.extensions", "workbench.view.extensions", "Extensions"),
    COPILOT("workbench.action.chat.open", "workbench.panel.chat", "Chat"),
    TERMINAL("workbench.action.terminal.toggleTerminal", "workbench.panel.terminal", "Terminal");

    companion object {
        fun fromOrdinal(index: Int): CodespacePanel =
            values().getOrElse(index) { EXPLORER }
    }
}
