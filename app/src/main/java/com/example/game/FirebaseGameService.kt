package com.example.game

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PropertyName
import java.util.UUID

data class FirebaseCard(
    val id: String = "",
    val suit: String = "",
    val number: Int = 0
) {
    fun toWhotCard(): WhotCard = WhotCard(id, WhotSuit.valueOf(suit), number)
}

fun WhotCard.toFirebaseCard(): FirebaseCard = FirebaseCard(id, suit.name, number)

data class FirebaseGameRoom(
    val roomCode: String = "",
    val hostId: String = "",
    val guestId: String = "",
    val status: String = "waiting", // "waiting", "playing", "finished"
    val hostHand: List<FirebaseCard> = emptyList(),
    val guestHand: List<FirebaseCard> = emptyList(),
    val deck: List<FirebaseCard> = emptyList(),
    val discardPile: List<FirebaseCard> = emptyList(),
    @get:PropertyName("isHostTurn") @set:PropertyName("isHostTurn") var isHostTurn: Boolean = true,
    val demandedSuit: String? = null,
    val activePenalty: Int = 0,
    val activePenaltyCardType: Int? = null,
    val winnerId: String? = null,
    val turnMessage: String = "",
    val lastMoveDescription: String = ""
)

object FirebaseGameService {
    private val db: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.e("FirebaseGameService", "FirebaseFirestore unavailable: ${e.message}")
            null
        }

    private val roomsCollection
        get() = db?.collection("rooms")
    
    var myPlayerId: String = UUID.randomUUID().toString().take(6).uppercase()
        private set

    private var roomListener: ListenerRegistration? = null

    fun createRoom(
        roomCode: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val initialRoom = FirebaseGameRoom(
            roomCode = roomCode,
            hostId = myPlayerId,
            status = "waiting",
            turnMessage = "Waiting for an opponent to join..."
        )

        val collection = roomsCollection
        if (collection == null) {
            onFailure(Exception("Firebase service unavailable. Please check internet connection."))
            return
        }

        collection.document(roomCode)
            .set(initialRoom)
            .addOnSuccessListener {
                Log.d("FirebaseGameService", "Room $roomCode successfully created in Firestore")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseGameService", "Failed to create room $roomCode in Firestore: ${e.message}")
                onFailure(e)
            }
    }

    fun joinRoom(
        roomCode: String,
        onSuccess: (FirebaseGameRoom) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedCode = roomCode.trim()
        val collection = roomsCollection
        if (collection == null) {
            onFailure(Exception("Firebase service unavailable. Please check internet connection."))
            return
        }

        collection.document(trimmedCode).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val room = document.toObject(FirebaseGameRoom::class.java)
                    if (room != null) {
                        if (room.status == "waiting") {
                            val updatedRoom = room.copy(
                                guestId = myPlayerId,
                                status = "playing",
                                turnMessage = "Opponent joined! Game started."
                            )
                            collection.document(trimmedCode).set(updatedRoom)
                                .addOnSuccessListener {
                                    onSuccess(updatedRoom)
                                }
                                .addOnFailureListener { e ->
                                    onFailure(e)
                                }
                        } else {
                            onFailure(Exception("This room is already full or finished."))
                        }
                    } else {
                        onFailure(Exception("Error parsing room data."))
                    }
                } else {
                    onFailure(Exception("Room not found. Please check the code."))
                }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    fun getRoomOnce(
        roomCode: String,
        onSuccess: (FirebaseGameRoom) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val collection = roomsCollection
        if (collection == null) {
            onFailure(Exception("Firebase service unavailable."))
            return
        }

        collection.document(roomCode).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val room = document.toObject(FirebaseGameRoom::class.java)
                    if (room != null) {
                        onSuccess(room)
                    } else {
                        onFailure(Exception("Error parsing room data."))
                    }
                } else {
                    onFailure(Exception("Room not found."))
                }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    fun listenToRoom(
        roomCode: String,
        onUpdate: (FirebaseGameRoom) -> Unit,
        onError: (Exception) -> Unit
    ) {
        roomListener?.remove()
        val collection = roomsCollection
        if (collection == null) {
            onError(Exception("Firebase service unavailable."))
            return
        }

        roomListener = collection.document(roomCode)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseGameService", "Snapshot listener error: ${e.message}")
                    onError(e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val room = snapshot.toObject(FirebaseGameRoom::class.java)
                    if (room != null) {
                        onUpdate(room)
                    }
                }
            }
    }

    fun updateRoomState(roomCode: String, room: FirebaseGameRoom, onComplete: (Boolean) -> Unit = {}) {
        val collection = roomsCollection
        if (collection == null) {
            onComplete(false)
            return
        }
        collection.document(roomCode)
            .set(room)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
    }

    fun stopListening() {
        roomListener?.remove()
        roomListener = null
    }

    fun deleteRoom(roomCode: String) {
        stopListening()
        roomsCollection?.document(roomCode)?.delete()
    }
}
