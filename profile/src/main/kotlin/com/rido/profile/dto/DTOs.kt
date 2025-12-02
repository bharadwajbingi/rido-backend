package com.rido.profile.dto

import com.rido.profile.model.DocumentStatus
import com.rido.profile.model.DocumentType
import com.rido.profile.model.UserRole
import com.rido.profile.model.UserStatus
import java.time.Instant
import java.util.UUID

data class UserProfileResponse(
    val id: UUID,
    val userId: Long,
    val name: String,
    val phone: String,
    val email: String,
    val photoUrl: String?,
    val role: UserRole,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class UpdateProfileRequest(
    val name: String?,
    val email: String?
)

data class RiderAddressResponse(
    val id: UUID,
    val label: String,
    val lat: Double,
    val lng: Double,
    val createdAt: Instant
)

data class AddAddressRequest(
    val label: String,
    val lat: Double,
    val lng: Double
)

data class DriverDocumentResponse(
    val id: UUID,
    val type: DocumentType,
    val documentNumber: String,
    val url: String,
    val status: DocumentStatus,
    val reason: String?,
    val uploadedAt: Instant
)

data class UploadDriverDocumentRequest(
    val type: DocumentType,
    val documentNumber: String,
    val url: String // In real flow, this might be returned from a pre-signed URL step, or we handle upload separately
)

data class DriverStatsResponse(
    val driverId: Long,
    val totalTrips: Int,
    val rating: Double,
    val earnings: Double
)
