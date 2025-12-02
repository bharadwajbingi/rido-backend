package com.rido.profile.controller

import com.rido.profile.dto.DriverDocumentResponse
import com.rido.profile.service.AdminProfileService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/profile/admin/driver")
class AdminController(
    private val adminProfileService: AdminProfileService
) {

    @PostMapping("/{id}/approve")
    fun approveDocument(
        @RequestHeader("X-User-ID") adminId: Long,
        @PathVariable id: UUID
    ): Mono<DriverDocumentResponse> {
        return adminProfileService.approveDocument(adminId, id)
    }

    @PostMapping("/{id}/reject")
    fun rejectDocument(
        @RequestHeader("X-User-ID") adminId: Long,
        @PathVariable id: UUID,
        @RequestBody reasonMap: Map<String, String>
    ): Mono<DriverDocumentResponse> {
        val reason = reasonMap["reason"] ?: "No reason provided"
        return adminProfileService.rejectDocument(adminId, id, reason)
    }
}
