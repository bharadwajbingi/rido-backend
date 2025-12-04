package com.rido.profile.event

import java.util.UUID

import java.time.Instant

data class ProfileUpdatedEvent(
    val userId: UUID,
    val fieldsChanged: List<String>,
    val timestamp: Instant = Instant.now()
)

data class DriverDocumentUploadedEvent(
    val driverId: UUID,
    val documentId: String,
    val type: String,
    val timestamp: Instant = Instant.now()
)

data class DriverApprovedEvent(
    val driverId: UUID,
    val timestamp: Instant = Instant.now()
)

data class DriverRejectedEvent(
    val driverId: UUID,
    val reason: String,
    val timestamp: Instant = Instant.now()
)
