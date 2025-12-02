package com.rido.profile.service

import com.rido.profile.dto.DriverDocumentResponse
import com.rido.profile.dto.UploadDriverDocumentRequest
import com.rido.profile.event.DriverDocumentUploadedEvent
import com.rido.profile.event.ProfileEventProducer
import com.rido.profile.model.DocumentStatus
import com.rido.profile.model.DriverDocument
import com.rido.profile.repository.DriverDocumentRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class DriverDocumentService(
    private val documentRepository: DriverDocumentRepository,
    private val eventProducer: ProfileEventProducer
) {

    fun getDocuments(driverId: Long): Flux<DriverDocumentResponse> {
        return documentRepository.findAllByDriverId(driverId)
            .map { it.toResponse() }
    }

    fun uploadDocument(driverId: Long, request: UploadDriverDocumentRequest): Mono<DriverDocumentResponse> {
        val document = DriverDocument(
            driverId = driverId,
            type = request.type,
            documentNumber = request.documentNumber,
            url = request.url,
            status = DocumentStatus.PENDING
        )
        return documentRepository.save(document)
            .doOnSuccess { saved ->
                eventProducer.publishDocumentUploaded(
                    DriverDocumentUploadedEvent(
                        driverId = driverId,
                        documentId = saved.id.toString(),
                        type = saved.type.name
                    )
                )
            }
            .map { it.toResponse() }
    }

    private fun DriverDocument.toResponse(): DriverDocumentResponse {
        return DriverDocumentResponse(
            id = this.id!!,
            type = this.type,
            documentNumber = this.documentNumber,
            url = this.url,
            status = this.status,
            reason = this.reason,
            uploadedAt = this.uploadedAt
        )
    }
}
