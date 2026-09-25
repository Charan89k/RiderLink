package com.example.riderlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderTest {

    private fun rider(name: String) =
        Rider(identity = "id", displayName = name, isLocal = false, state = RiderState.CONNECTED)

    @Test
    fun `two words give one initial each`() {
        assertEquals("CK", rider("Charan Kumar").initials)
    }

    @Test
    fun `a single word gives its first two letters`() {
        assertEquals("CH", rider("Charan").initials)
    }

    @Test
    fun `generated call signs split on hyphens and underscores`() {
        assertEquals("R4", rider("Rider-402").initials)
        assertEquals("R4", rider("Rider_402").initials)
    }

    @Test
    fun `a one letter name does not crash the avatar`() {
        assertEquals("C", rider("C").initials)
    }

    @Test
    fun `a blank name falls back to a placeholder rather than an empty circle`() {
        assertEquals("?", rider("   ").initials)
        assertEquals("?", rider("").initials)
    }

    @Test
    fun `initials are always upper case regardless of how the rider typed their name`() {
        assertEquals("CK", rider("charan kumar").initials)
    }

    @Test
    fun `every rider state has a label`() {
        for (state in RiderState.entries) {
            assertTrue(state.label.isNotBlank())
        }
    }
}
