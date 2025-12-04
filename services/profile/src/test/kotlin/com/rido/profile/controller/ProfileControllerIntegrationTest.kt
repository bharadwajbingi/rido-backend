package com.rido.profile.controller

import com.rido.profile.dto.UserProfileResponse
import com.rido.profile.model.UserProfile
import com.rido.profile.model.UserRole
import com.rido.profile.repository.UserProfileRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureWebTestClient
class ProfileControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @BeforeEach
    fun setup() {
        userProfileRepository.deleteAll().block()
    }

    @Test
    fun `getMyProfile should return profile`() {
        val userId = 123L
        val profile = UserProfile(
            id = UUID.randomUUID(),
            userId = userId,
            name = "Test User",
            phone = "+1234567890",
            email = "test@example.com",
            role = UserRole.RIDER,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        userProfileRepository.save(profile).block()

        webTestClient.get().uri("/profile/me")
            .header("X-User-ID", userId.toString())
            .header("X-Roles", "RIDER")
            .exchange()
            .expectStatus().isOk
            .expectBody(UserProfileResponse::class.java)
            .consumeWith { response ->
                val body = response.responseBody!!
                assert(body.userId == userId)
                assert(body.name == "Test User")
            }
    }
}
