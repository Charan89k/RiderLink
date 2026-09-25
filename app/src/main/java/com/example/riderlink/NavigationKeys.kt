package com.example.riderlink

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Where a ride starts: create or join. */
@Serializable data object Lobby : NavKey

/** The live rider dashboard. */
@Serializable data object Ride : NavKey

/** Settings, reachable from both other screens. */
@Serializable data object Settings : NavKey
