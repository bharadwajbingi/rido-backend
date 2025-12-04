package com.rido.profile.repository

import com.rido.profile.model.AuditLog
import com.rido.profile.model.DriverDocument
import com.rido.profile.model.DriverStats
import com.rido.profile.model.RiderAddress
import com.rido.profile.model.UserProfile
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
interface UserProfileRepository : R2dbcRepository<UserProfile, UUID> {
    fun findByUserId(userId: UUID): Mono<UserProfile>
    fun findByPhone(phone: String): Mono<UserProfile>
    fun findByEmail(email: String): Mono<UserProfile>
}

@Repository
interface RiderAddressRepository : R2dbcRepository<RiderAddress, UUID> {
    fun findAllByRiderId(riderId: UUID): Flux<RiderAddress>
}

@Repository
interface DriverDocumentRepository : R2dbcRepository<DriverDocument, UUID> {
    fun findAllByDriverId(driverId: UUID): Flux<DriverDocument>
}

@Repository
interface DriverStatsRepository : R2dbcRepository<DriverStats, UUID> {
    // PK is driver_id (Long), so standard methods work
}

@Repository
interface AuditLogRepository : R2dbcRepository<AuditLog, UUID>
