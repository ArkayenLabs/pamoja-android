package com.pamoja.app.domain.model

/** A cheer or a nudge. [wire] is the value stored in Firestore and checked by the rules. */
enum class SignalKind(val wire: String) {
    CHEER("cheer"),
    NUDGE("nudge"),
    ;

    companion object {
        fun fromWire(value: String): SignalKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One cheer or nudge between two members of a group, on the sender's local
 * [date]. At most one of each kind per sender, recipient and day: the rules
 * fix the document ID to exactly that, and a signal can never be rewritten.
 *
 * Only the two people involved can read it.
 */
data class Signal(
    val kind: SignalKind,
    val fromUserId: String,
    val toUserId: String,
    val date: String,
)
