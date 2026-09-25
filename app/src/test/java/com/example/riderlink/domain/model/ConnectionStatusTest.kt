package com.example.riderlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStatusTest {

    @Test
    fun `reconnecting counts as an active session so the ride is not torn down`() {
        assertTrue(ConnectionStatus.RECONNECTING.isActive)
        assertTrue(ConnectionStatus.CONNECTING.isActive)
        assertTrue(ConnectionStatus.CONNECTED.isActive)
    }

    @Test
    fun `only CONNECTED is live, so the UI cannot promise audio it does not have`() {
        assertTrue(ConnectionStatus.CONNECTED.isLive)
        for (status in ConnectionStatus.entries - ConnectionStatus.CONNECTED) {
            assertFalse("$status must not report itself as live", status.isLive)
        }
    }

    @Test
    fun `ended states are neither active nor live`() {
        for (status in listOf(ConnectionStatus.IDLE, ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR)) {
            assertFalse("$status", status.isActive)
            assertFalse("$status", status.isLive)
        }
    }

    @Test
    fun `every status has a label for the dashboard`() {
        for (status in ConnectionStatus.entries) {
            assertTrue(status.label.isNotBlank())
            assertEquals(status.label, status.label.uppercase())
        }
    }
}
