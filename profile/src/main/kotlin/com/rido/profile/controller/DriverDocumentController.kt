package com.rido.profile.controller

import com.rido.profile.dto.DriverDocumentResponse
import com.rido.profile.dto.UploadDriverDocumentRequest
import com.rido.profile.service.DriverDocumentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/profile/driver/documents")
class DriverDocumentController(
    private val documentService: DriverDocumentService
) {

    @GetMapping
    fun getDocuments(@RequestHeader("X-User-ID") userId: UUID): Flux<DriverDocumentResponse> {
        return documentService.getDocuments(userId)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDocument(
        @RequestHeader("X-User-ID") userId: UUID,
        @RequestBody request: UploadDriverDocumentRequest
    ): Mono<DriverDocumentResponse> {
        return documentService.uploadDocument(userId, request)
    }
}
