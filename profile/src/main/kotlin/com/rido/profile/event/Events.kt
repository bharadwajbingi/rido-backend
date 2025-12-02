package com.rido.profile.event

import java.time.Instant

data class ProfileUpdatedEvent(
    val userId: Long,
    val fieldsChanged: List<String>,
    val timestamp: Instant = Instant.now()
)

data class DriverDocumentUploadedEvent(
    val driverId: Long,
    val documentId: String,
    val type: String,
    val timestamp: Instant = Instant.now()
)

data class DriverApprovedEvent(
    val driverId: Long,
    val timestamp: Instant = Instant.now()
)

data class DriverRejectedEvent(
    val driverId: Long,
    val reason: String,
    val timestamp: Instant = Instant.now()
)
