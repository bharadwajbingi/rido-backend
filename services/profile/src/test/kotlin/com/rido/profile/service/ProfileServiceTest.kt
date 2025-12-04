package com.rido.profile.service

import com.rido.profile.dto.UpdateProfileRequest
import com.rido.profile.event.ProfileEventProducer
import com.rido.profile.model.AuditLog
import com.rido.profile.model.UserProfile
import com.rido.profile.model.UserRole
import com.rido.profile.repository.AuditLogRepository
import com.rido.profile.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

class ProfileServiceTest {

    private val userProfileRepository = mockk<UserProfileRepository>()
    private val auditLogRepository = mockk<AuditLogRepository>()
    private val storageService = mockk<StorageService>()
    private val eventProducer = mockk<ProfileEventProducer>(relaxed = true)

    private val profileService = ProfileService(
        userProfileRepository,
        auditLogRepository,
        storageService,
        eventProducer
    )

    @Test
    fun `getProfile should return profile when found`() {
        val userId = 123L
        val profile = UserProfile(
            id = UUID.randomUUID(),
            userId = userId,
            name = "John Doe",
            phone = "+1234567890",
            email = "john@example.com",
            role = UserRole.RIDER
        )

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(profile)

        StepVerifier.create(profileService.getProfile(userId))
            .assertNext { response ->
                assertEquals(userId, response.userId)
                assertEquals("John Doe", response.name)
            }
            .verifyComplete()
    }

    @Test
    fun `updateProfile should update fields and log audit`() {
        val userId = 123L
        val profile = UserProfile(
            id = UUID.randomUUID(),
            userId = userId,
            name = "John Doe",
            phone = "+1234567890",
            email = "john@example.com",
            role = UserRole.RIDER
        )
        val request = UpdateProfileRequest(name = "Jane Doe", email = null)
        val updatedProfile = profile.copy(name = "Jane Doe")

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(profile)
        every { userProfileRepository.save(any()) } returns Mono.just(updatedProfile)
        every { auditLogRepository.save(any()) } returns Mono.just(mockk<AuditLog>())

        StepVerifier.create(profileService.updateProfile(userId, request))
            .assertNext { response ->
                assertEquals("Jane Doe", response.name)
            }
            .verifyComplete()

        verify { userProfileRepository.save(match { it.name == "Jane Doe" }) }
        verify { eventProducer.publishProfileUpdated(any()) }
    }
}
