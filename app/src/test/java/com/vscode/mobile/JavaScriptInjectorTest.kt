package com.vscode.mobile

import com.vscode.mobile.model.CodespacePanel
import com.vscode.mobile.model.ViewMode
import com.vscode.mobile.webview.JavaScriptInjector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaScriptInjectorTest {

    @Test
    fun `mobile bootstrap contains MOBILE mode marker`() {
        val script = JavaScriptInjector.buildMobileBootstrap(ViewMode.MOBILE)
        assertTrue("Script should reference MOBILE mode", script.contains("MOBILE"))
    }

    @Test
    fun `desktop bootstrap contains DESKTOP mode marker`() {
        val script = JavaScriptInjector.buildMobileBootstrap(ViewMode.DESKTOP)
        assertTrue("Script should reference DESKTOP mode", script.contains("DESKTOP"))
    }

    @Test
    fun `mobile bootstrap hides activity bar in mobile mode`() {
        val script = JavaScriptInjector.buildMobileBootstrap(ViewMode.MOBILE)
        assertTrue("Mobile mode should hide the activity bar", script.contains(".activitybar"))
    }

    @Test
    fun `desktop bootstrap has no activity bar override`() {
        val script = JavaScriptInjector.buildMobileBootstrap(ViewMode.DESKTOP)
        // In desktop mode, mobileCss is empty, so there should be no activitybar rule
        assertFalse("Desktop mode should not have activitybar positioning", script.contains("left: -9999px"))
    }

    @Test
    fun `bootstrap defines VSCMobile global object`() {
        val script = JavaScriptInjector.buildMobileBootstrap(ViewMode.MOBILE)
        assertTrue(script.contains("window.VSCMobile"))
    }

    @Test
    fun `bootstrap sets double-injection guard`() {
        val script = JavaScriptInjector.buildMobileBootstrap(ViewMode.MOBILE)
        assertTrue(script.contains("__vscMobileInjected"))
    }

    @Test
    fun `activate panel script contains panel id`() {
        val script = JavaScriptInjector.buildActivatePanelScript(CodespacePanel.TERMINAL)
        assertTrue(script.contains(CodespacePanel.TERMINAL.activityBarId))
    }

    @Test
    fun `activate panel script is self-contained`() {
        // The script should work without window.VSCMobile being set up
        val script = JavaScriptInjector.buildActivatePanelScript(CodespacePanel.EXPLORER)
        assertTrue("Script should contain selector strategy", script.contains("querySelector"))
        assertTrue("Script should contain panel label", script.contains(CodespacePanel.EXPLORER.displayLabel))
    }

    @Test
    fun `activate panel script contains keyboard shortcut fallback`() {
        val script = JavaScriptInjector.buildActivatePanelScript(CodespacePanel.EXPLORER)
        assertTrue("Script should contain keyboard shortcut dispatch", script.contains("KeyboardEvent"))
    }

    @Test
    fun `set view mode script for mobile`() {
        val script = JavaScriptInjector.buildSetViewModeScript(ViewMode.MOBILE)
        assertTrue(script.contains("MOBILE"))
        assertTrue(script.contains("setViewMode"))
    }

    @Test
    fun `set view mode script for desktop`() {
        val script = JavaScriptInjector.buildSetViewModeScript(ViewMode.DESKTOP)
        assertTrue(script.contains("DESKTOP"))
    }
}
