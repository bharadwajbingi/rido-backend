package com.rido.profile.controller

import com.rido.profile.dto.UpdateProfileRequest
import com.rido.profile.dto.UserProfileResponse
import com.rido.profile.service.ProfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/profile")
class ProfileController(
    private val profileService: ProfileService
) {

    @GetMapping("/me")
    fun getMyProfile(@RequestHeader("X-User-ID") userId: Long): Mono<UserProfileResponse> {
        return profileService.getProfile(userId)
    }

    @PutMapping("/me")
    fun updateMyProfile(
        @RequestHeader("X-User-ID") userId: Long,
        @RequestBody request: UpdateProfileRequest
    ): Mono<UserProfileResponse> {
        return profileService.updateProfile(userId, request)
    }
}
