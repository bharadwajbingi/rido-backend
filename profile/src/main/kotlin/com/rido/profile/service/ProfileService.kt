package com.rido.profile.service

import com.rido.profile.dto.UpdateProfileRequest
import com.rido.profile.dto.UserProfileResponse
import com.rido.profile.model.AuditLog
import com.rido.profile.model.UserProfile
import com.rido.profile.repository.AuditLogRepository
import com.rido.profile.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class ProfileService(
    private val userProfileRepository: UserProfileRepository,
    private val auditLogRepository: AuditLogRepository
) {

    fun getProfile(userId: Long): Mono<UserProfileResponse> {
        return userProfileRepository.findByUserId(userId)
            .map { it.toResponse() }
            .switchIfEmpty(Mono.error(RuntimeException("User not found")))
    }

    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): Mono<UserProfileResponse> {
        return userProfileRepository.findByUserId(userId)
            .switchIfEmpty(Mono.error(RuntimeException("User not found")))
            .flatMap { profile ->
                val updatedProfile = profile.copy(
                    name = request.name ?: profile.name,
                    email = request.email ?: profile.email,
                    updatedAt = Instant.now()
                )
                userProfileRepository.save(updatedProfile)
                    .flatMap { saved ->
                        logAudit(userId, "UPDATE", "UserProfile", saved.id.toString(), "Updated profile details")
                            .thenReturn(saved.toResponse())
                    }
            }
    }

    private fun logAudit(actorId: Long, action: String, entity: String, entityId: String, metadata: String): Mono<AuditLog> {
        return auditLogRepository.save(
            AuditLog(
                entity = entity,
                entityId = entityId,
                action = action,
                actor = actorId,
                metadata = metadata
            )
        )
    }

    private fun UserProfile.toResponse(): UserProfileResponse {
        return UserProfileResponse(
            id = this.id!!,
            userId = this.userId,
            name = this.name,
            phone = this.phone,
            email = this.email,
            photoUrl = this.photoUrl,
            role = this.role,
            status = this.status,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
