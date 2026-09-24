package com.pamoja.app.domain.model

/** One owner-scoped server record. Fields contain JSON primitives/maps only. */
data class AdventureExportRecord(
    val groupId: String,
    val adventureId: String,
    val kind: String,
    val segmentId: String? = null,
    val fields: Map<String, Any?>,
)
