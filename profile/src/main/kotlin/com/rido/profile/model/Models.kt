package com.rido.profile.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

enum class UserRole { RIDER, DRIVER, ADMIN }
enum class UserStatus { ACTIVE, BANNED, PENDING_VERIFICATION }

@Table("user_profiles")
data class UserProfile(
    @Id
    val id: UUID? = null,
    val userId: Long, // From Auth Service
    val name: String,
    val phone: String,
    val email: String,
    val photoUrl: String? = null,
    val role: UserRole,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

@Table("rider_addresses")
data class RiderAddress(
    @Id
    val id: UUID? = null,
    val riderId: Long,
    val label: String, // e.g. "Home", "Work"
    val lat: Double,
    val lng: Double,
    val createdAt: Instant = Instant.now()
)

enum class DocumentType { LICENSE, REGISTRATION, INSURANCE, PERMIT }
enum class DocumentStatus { PENDING, APPROVED, REJECTED }

@Table("driver_documents")
data class DriverDocument(
    @Id
    val id: UUID? = null,
    val driverId: Long,
    val type: DocumentType,
    val documentNumber: String,
    val url: String,
    val status: DocumentStatus = DocumentStatus.PENDING,
    val reason: String? = null, // Rejection reason
    val uploadedAt: Instant = Instant.now(),
    val reviewedBy: Long? = null
)

@Table("driver_stats")
data class DriverStats(
    @Id
    val driverId: Long, // PK is driver_id (same as user_id)
    val totalTrips: Int = 0,
    val cancelledTrips: Int = 0,
    val rating: Double = 5.0,
    val earnings: Double = 0.0,
    val updatedAt: Instant = Instant.now()
)

@Table("audit_logs")
data class AuditLog(
    @Id
    val id: UUID? = null,
    val entity: String, // e.g. "UserProfile", "DriverDocument"
    val entityId: String,
    val action: String, // e.g. "UPDATE", "APPROVE"
    val actor: Long, // User ID of who performed the action
    val metadata: String? = null, // JSON string
    val createdAt: Instant = Instant.now()
)
