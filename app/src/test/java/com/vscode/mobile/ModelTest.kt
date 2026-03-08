package com.vscode.mobile

import com.vscode.mobile.model.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {

    @Test
    fun `ViewMode fromString returns MOBILE for null`() {
        assertEquals(ViewMode.MOBILE, ViewMode.fromString(null))
    }

    @Test
    fun `ViewMode fromString returns MOBILE for empty`() {
        assertEquals(ViewMode.MOBILE, ViewMode.fromString(""))
    }

    @Test
    fun `ViewMode fromString is case insensitive`() {
        assertEquals(ViewMode.DESKTOP, ViewMode.fromString("desktop"))
        assertEquals(ViewMode.DESKTOP, ViewMode.fromString("DESKTOP"))
        assertEquals(ViewMode.MOBILE, ViewMode.fromString("mobile"))
    }
}
