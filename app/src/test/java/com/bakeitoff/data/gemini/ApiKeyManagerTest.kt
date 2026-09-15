package com.bakeitoff.data.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiKeyManagerTest {

    @Test
    fun `toggleKey rotates through the keys and wraps back to the first after the last`() {
        val manager = ApiKeyManager()
        val names = mutableListOf(manager.getActiveKeyName())

        repeat(manager.sizeKeys) {
            manager.toggleKey()
            names.add(manager.getActiveKeyName())
        }

        // After rotating sizeKeys times, it's back to the initial name.
        assertEquals(names.first(), names.last())
        // And it doesn't get stuck on the same name the whole time.
        assertNotEquals(names[0], names[1])
    }

    @Test
    fun `different instances do not share state`() {
        val a = ApiKeyManager()
        val b = ApiKeyManager()

        a.toggleKey()

        // Rotating one instance shouldn't affect the other — exactly what the
        // old singleton made impossible to test (shared global state).
        assertNotEquals(a.getActiveKeyName(), b.getActiveKeyName())
    }
}
