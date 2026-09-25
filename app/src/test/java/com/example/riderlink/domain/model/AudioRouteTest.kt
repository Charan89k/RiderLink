package com.example.riderlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRouteTest {

    @Test
    fun `only a bluetooth route counts as a connected helmet`() {
        assertTrue(AudioRoute(AudioRouteType.BLUETOOTH_HEADSET).isHelmetConnected)
        assertFalse(AudioRoute(AudioRouteType.WIRED_HEADSET).isHelmetConnected)
        assertFalse(AudioRoute(AudioRouteType.PHONE).isHelmetConnected)
        assertFalse(AudioRoute(AudioRouteType.UNKNOWN).isHelmetConnected)
    }

    @Test
    fun `the device name is shown when the system provides one`() {
        assertEquals("Cardo Packtalk", AudioRoute(AudioRouteType.BLUETOOTH_HEADSET, "Cardo Packtalk").label)
    }

    @Test
    fun `a nameless bluetooth device still reads as connected`() {
        assertEquals("Helmet connected", AudioRoute(AudioRouteType.BLUETOOTH_HEADSET).label)
    }

    @Test
    fun `the default route is unknown rather than falsely reporting the phone`() {
        assertEquals(AudioRouteType.UNKNOWN, AudioRoute().type)
        assertFalse(AudioRoute().isHelmetConnected)
    }

    @Test
    fun `every route type has a label`() {
        for (type in AudioRouteType.entries) {
            assertTrue(AudioRoute(type).label.isNotBlank())
        }
    }
}
