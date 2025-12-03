package com.rido.profile.controller

import com.rido.profile.dto.DriverDocumentResponse
import com.rido.profile.service.AdminProfileService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/profile/admin/driver")
class AdminController(
    private val adminProfileService: AdminProfileService
) {

    @PostMapping("/{id}/approve")
    fun approveDocument(
        @RequestHeader("X-User-ID") adminId: UUID,
        @PathVariable id: UUID
    ): Mono<DriverDocumentResponse> {
        return adminProfileService.approveDocument(adminId, id)
    }

    @PostMapping("/{id}/reject")
    fun rejectDocument(
        @RequestHeader("X-User-ID") adminId: UUID,
        @PathVariable id: UUID,
        @RequestBody reasonMap: Map<String, String>
    ): Mono<DriverDocumentResponse> {
        val reason = reasonMap["reason"] ?: "No reason provided"
        return adminProfileService.rejectDocument(adminId, id, reason)
    }
}
