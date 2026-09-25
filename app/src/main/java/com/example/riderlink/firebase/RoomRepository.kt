package com.example.riderlink.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * A room record. Deliberately carries no LiveKit credentials: the document only
 * proves that a 4-digit code maps to a real room, and access tokens are minted
 * by the token server.
 */
data class RoomDetails(
    val roomCode: String = "",
    val createdAt: Long = 0
)

interface RoomRepository {
    suspend fun createRoom(): RoomDetails

    /**
     * Looks a code up in the directory.
     *
     * Returns null only when the directory was reachable and said the ride does
     * not exist. When the directory itself cannot be reached this returns the
     * room anyway: access is enforced by the token server, which issues
     * room-scoped tokens, so an unreachable directory must not ground a ride.
     */
    suspend fun joinRoom(roomCode: String): RoomDetails?
}

class FirebaseRoomRepository(private val context: Context) : RoomRepository {

    private val isFirebaseAvailable: Boolean by lazy {
        try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                FirebaseApp.initializeApp(context) != null
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization failed: ${e.message}. Using simulated mode.")
            false
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        if (isFirebaseAvailable) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore initialization failed: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "RoomRepository"

        /** Firestore is a convenience here, not a dependency. Never wait long for it. */
        private const val FIRESTORE_TIMEOUT_MS = 6_000L
        // In-memory fallback database for simulation
        private val simulatedRooms = mutableMapOf<String, RoomDetails>()
    }

    override suspend fun createRoom(): RoomDetails {
        val code = generate4DigitCode()
        val roomDetails = RoomDetails(
            roomCode = code,
            createdAt = System.currentTimeMillis()
        )

        val db = firestore
        if (db != null) {
            // Firestore's set() only reports success once the write reaches the
            // server; offline it queues silently and the listener never fires.
            // Without this bound, tapping "Create ride" on a weak signal spins
            // forever with no error and no way out.
            val written = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                runCatching {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        db.collection("rooms")
                            .document(code)
                            .set(roomDetails)
                            .addOnSuccessListener { continuation.resume(Unit) }
                            .addOnFailureListener { continuation.resumeWithException(it) }
                    }
                }.isSuccess
            } ?: false

            if (written) {
                Log.d(TAG, "Registered ride $code in Firestore")
            } else {
                Log.w(TAG, "Could not register ride $code in Firestore; continuing locally")
                synchronized(simulatedRooms) { simulatedRooms[code] = roomDetails }
            }
        } else {
            Log.d(TAG, "Firestore unavailable; registering ride $code locally")
            synchronized(simulatedRooms) { simulatedRooms[code] = roomDetails }
        }

        return roomDetails
    }

    override suspend fun joinRoom(roomCode: String): RoomDetails? {
        val cleanCode = roomCode.trim()
        val db = firestore

        if (db != null) {
            val lookup = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                runCatching {
                    suspendCancellableCoroutine { continuation ->
                        db.collection("rooms")
                            .document(cleanCode)
                            .get()
                            .addOnSuccessListener { continuation.resume(it) }
                            .addOnFailureListener { continuation.resumeWithException(it) }
                    }
                }.getOrNull()
            }

            when {
                lookup == null ->
                    Log.w(TAG, "Ride directory unreachable; joining $cleanCode unverified")
                lookup.exists() -> {
                    Log.d(TAG, "Found ride $cleanCode in Firestore")
                    return lookup.toObject(RoomDetails::class.java)
                        ?: RoomDetails(cleanCode, System.currentTimeMillis())
                }
                else -> {
                    // The directory answered and the ride is not there. This is
                    // the one case where a wrong code is reported as wrong.
                    Log.w(TAG, "Ride $cleanCode does not exist")
                    return null
                }
            }
        }

        synchronized(simulatedRooms) { simulatedRooms[cleanCode] }?.let {
            Log.d(TAG, "Found ride $cleanCode in local registry")
            return it
        }

        // Unverified join. The token server only ever issues a token scoped to
        // this exact code, so the worst outcome is an empty room.
        return RoomDetails(roomCode = cleanCode, createdAt = System.currentTimeMillis())
    }

    private fun generate4DigitCode(): String {
        // Return a 4-digit code as a string (between 1000 and 9999)
        return Random.nextInt(1000, 10000).toString()
    }
}
