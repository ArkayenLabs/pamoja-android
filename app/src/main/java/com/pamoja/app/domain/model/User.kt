package com.pamoja.app.domain.model

data class User(
    val userId: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val age: Int? = null,
    val height: Float? = null,
    val weight: Float? = null,
    val deviceToken: String? = null,
    /**
     * Whether this person's photo may be shown to other members of their groups.
     *
     * **Default off, deliberately.** A photo is fine among family and not fine
     * in a group of strangers from the internet, and the safe default is the
     * one you would not regret.
     *
     * This is not a display flag other clients are trusted to honour. It
     * controls whether [photoUrl] is copied onto this user's membership
     * documents at all, and membership documents are the only thing other
     * members can read, since users/{userId} is owner-only. Off means the URL
     * is not there to fetch, rather than there but politely ignored.
     */
    val showPhotoInGroups: Boolean = false,
)