package com.rido.profile.controller

import com.rido.profile.dto.DriverDocumentResponse
import com.rido.profile.dto.UploadDriverDocumentRequest
import com.rido.profile.service.DriverDocumentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/profile/driver/documents")
class DriverDocumentController(
    private val documentService: DriverDocumentService
) {

    @GetMapping
    fun getDocuments(@RequestHeader("X-User-ID") userId: Long): Flux<DriverDocumentResponse> {
        return documentService.getDocuments(userId)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDocument(
        @RequestHeader("X-User-ID") userId: Long,
        @RequestBody request: UploadDriverDocumentRequest
    ): Mono<DriverDocumentResponse> {
        return documentService.uploadDocument(userId, request)
    }
}
