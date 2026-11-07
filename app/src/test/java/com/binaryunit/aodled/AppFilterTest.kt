package com.binaryunit.aodled

import com.binaryunit.aodled.config.FilterMode
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.withFilterList
import com.binaryunit.aodled.config.withoutPackages
import com.binaryunit.aodled.notify.passesFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {
    private val allow = LightConfig(filterMode = FilterMode.ALLOW, allowList = setOf("com.whatsapp"))
    private val ignore = LightConfig(filterMode = FilterMode.IGNORE, ignoreList = setOf("com.whatsapp"))

    @Test fun `all lets every app through`() = assertTrue(passesFilter("com.whatsapp", LightConfig()))

    @Test fun `allow list lets only checked apps through`() {
        assertTrue(passesFilter("com.whatsapp", allow))
        assertFalse(passesFilter("org.thoughtcrime.securesms", allow))
    }

    @Test fun `ignore list lets every app through except checked ones`() {
        assertFalse(passesFilter("com.whatsapp", ignore))
        assertTrue(passesFilter("org.thoughtcrime.securesms", ignore))
    }

    @Test fun `the inactive list has no effect`() {
        val c = LightConfig(filterMode = FilterMode.IGNORE, allowList = setOf("com.whatsapp"))
        assertTrue(passesFilter("org.thoughtcrime.securesms", c))
    }

    @Test fun `done saves the list and switches to its mode, keeping the other list`() {
        val c = allow.withFilterList(FilterMode.IGNORE, setOf("com.google.android.gm"))!!
        assertEquals(FilterMode.IGNORE, c.filterMode)
        assertEquals(setOf("com.google.android.gm"), c.ignoreList)
        assertEquals(setOf("com.whatsapp"), c.allowList)
    }

    @Test fun `done on an emptied active list deletes it and goes back to all`() {
        val c = allow.withFilterList(FilterMode.ALLOW, emptySet())!!
        assertEquals(FilterMode.ALL, c.filterMode)
        assertEquals(emptySet<String>(), c.allowList)
    }

    @Test fun `done on an emptied inactive list deletes it and keeps the active mode`() {
        val c = ignore.copy(allowList = setOf("com.whatsapp")).withFilterList(FilterMode.ALLOW, emptySet())!!
        assertEquals(FilterMode.IGNORE, c.filterMode)
        assertEquals(emptySet<String>(), c.allowList)
        assertEquals(setOf("com.whatsapp"), c.ignoreList)
    }

    @Test fun `done with nothing checked on a list that never existed changes nothing`() =
        assertNull(allow.withFilterList(FilterMode.IGNORE, emptySet()))

    @Test fun `leaving out other profiles takes their apps off both lists`() {
        val c = LightConfig(
            filterMode = FilterMode.ALLOW,
            allowList = setOf("com.whatsapp", "org.thoughtcrime.securesms"),
            ignoreList = setOf("com.whatsapp", "com.google.android.gm"),
        ).withoutPackages(setOf("com.whatsapp"))
        assertEquals(FilterMode.ALLOW, c.filterMode)
        assertEquals(setOf("org.thoughtcrime.securesms"), c.allowList)
        assertEquals(setOf("com.google.android.gm"), c.ignoreList)
    }

    @Test fun `leaving out other profiles empties the active list and goes back to all`() {
        val c = allow.withoutPackages(setOf("com.whatsapp"))
        assertEquals(FilterMode.ALL, c.filterMode)
        assertEquals(emptySet<String>(), c.allowList)
    }

    @Test fun `leaving out other profiles keeps the active mode when only the inactive list empties`() {
        val c = ignore.copy(ignoreList = setOf("com.google.android.gm"), allowList = setOf("com.whatsapp"))
            .withoutPackages(setOf("com.whatsapp"))
        assertEquals(FilterMode.IGNORE, c.filterMode)
        assertEquals(emptySet<String>(), c.allowList)
    }
}
