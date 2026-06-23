package com.example.riderlink.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

data class RoomDetails(
    val roomCode: String = "",
    val livekitUrl: String = "",
    val livekitApiKey: String = "",
    val livekitApiSecret: String = "",
    val createdAt: Long = 0
)

interface RoomRepository {
    suspend fun createRoom(livekitUrl: String, apiKey: String, apiSecret: String): RoomDetails
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
        // In-memory fallback database for simulation
        private val simulatedRooms = mutableMapOf<String, RoomDetails>()
    }

    override suspend fun createRoom(livekitUrl: String, apiKey: String, apiSecret: String): RoomDetails {
        val code = generate4DigitCode()
        val roomDetails = RoomDetails(
            roomCode = code,
            livekitUrl = livekitUrl,
            livekitApiKey = apiKey,
            livekitApiSecret = apiSecret,
            createdAt = System.currentTimeMillis()
        )

        val db = firestore
        if (db != null) {
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    db.collection("rooms")
                        .document(code)
                        .set(roomDetails)
                        .addOnSuccessListener {
                            continuation.resume(Unit)
                        }
                        .addOnFailureListener { exception ->
                            continuation.resumeWithException(exception)
                        }
                }
                Log.d(TAG, "Successfully created room $code in Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Error writing room to Firestore, falling back to simulated memory", e)
                synchronized(simulatedRooms) {
                    simulatedRooms[code] = roomDetails
                }
            }
        } else {
            Log.d(TAG, "Simulating room creation in memory for code $code")
            synchronized(simulatedRooms) {
                simulatedRooms[code] = roomDetails
            }
        }

        return roomDetails
    }

    override suspend fun joinRoom(roomCode: String): RoomDetails? {
        val cleanCode = roomCode.trim()
        val db = firestore
        if (db != null) {
            try {
                val document = suspendCancellableCoroutine<com.google.firebase.firestore.DocumentSnapshot> { continuation ->
                    db.collection("rooms")
                        .document(cleanCode)
                        .get()
                        .addOnSuccessListener { doc ->
                            continuation.resume(doc)
                        }
                        .addOnFailureListener { exception ->
                            continuation.resumeWithException(exception)
                        }
                }
                if (document.exists()) {
                    val roomDetails = document.toObject(RoomDetails::class.java)
                    if (roomDetails != null) {
                        Log.d(TAG, "Successfully fetched room $cleanCode from Firestore")
                        return roomDetails
                    }
                } else {
                    Log.w(TAG, "Room $cleanCode not found in Firestore. Trying local simulation lookup.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading room from Firestore: ${e.message}. Trying local simulation lookup.", e)
            }
        }

        // Fallback to simulated database
        val simulated = synchronized(simulatedRooms) {
            simulatedRooms[cleanCode]
        }
        if (simulated != null) {
            Log.d(TAG, "Found room $cleanCode in simulated memory")
        } else {
            Log.w(TAG, "Room $cleanCode not found in simulated memory either")
        }
        return simulated
    }

    private fun generate4DigitCode(): String {
        // Return a 4-digit code as a string (between 1000 and 9999)
        return Random.nextInt(1000, 10000).toString()
    }
}
