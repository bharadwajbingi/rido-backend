package com.rido.profile.service

import com.rido.profile.dto.DriverDocumentResponse
import com.rido.profile.event.DriverApprovedEvent
import com.rido.profile.event.DriverRejectedEvent
import com.rido.profile.event.ProfileEventProducer
import com.rido.profile.model.AuditLog
import com.rido.profile.model.DocumentStatus
import com.rido.profile.model.DriverDocument
import com.rido.profile.repository.AuditLogRepository
import com.rido.profile.repository.DriverDocumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class AdminProfileService(
    private val documentRepository: DriverDocumentRepository,
    private val auditLogRepository: AuditLogRepository,
    private val eventProducer: ProfileEventProducer
) {

    @Transactional
    fun approveDocument(adminId: UUID, documentId: UUID): Mono<DriverDocumentResponse> {
        return documentRepository.findById(documentId)
            .switchIfEmpty(Mono.error(RuntimeException("Document not found")))
            .flatMap { doc ->
                val updatedDoc = doc.copy(
                    status = DocumentStatus.APPROVED,
                    reviewedBy = adminId
                )
                documentRepository.save(updatedDoc)
                    .flatMap { saved ->
                        logAudit(adminId, "APPROVE", "DriverDocument", saved.id.toString(), "Approved document", "DOCUMENT_APPROVED", "Admin")
                            .doOnSuccess {
                                eventProducer.publishDriverApproved(
                                    DriverApprovedEvent(driverId = saved.driverId)
                                )
                            }
                            .thenReturn(saved.toResponse())
                    }
            }
    }

    @Transactional
    fun rejectDocument(adminId: UUID, documentId: UUID, reason: String): Mono<DriverDocumentResponse> {
        return documentRepository.findById(documentId)
            .switchIfEmpty(Mono.error(RuntimeException("Document not found")))
            .flatMap { doc ->
                val updatedDoc = doc.copy(
                    status = DocumentStatus.REJECTED,
                    reason = reason,
                    reviewedBy = adminId
                )
                documentRepository.save(updatedDoc)
                    .flatMap { saved ->
                        logAudit(adminId, "REJECT", "DriverDocument", saved.id.toString(), "Rejected document: $reason", "DOCUMENT_REJECTED", "Admin")
                            .doOnSuccess {
                                eventProducer.publishDriverRejected(
                                    DriverRejectedEvent(driverId = saved.driverId, reason = reason)
                                )
                            }
                            .thenReturn(saved.toResponse())
                    }
            }
    }

    private fun logAudit(actorId: UUID, action: String, entity: String, entityId: String, metadata: String, eventType: String = "UNKNOWN", username: String = "Unknown"): Mono<AuditLog> {
        return auditLogRepository.save(
            AuditLog(
                entity = entity,
                entityId = entityId,
                action = action,
                actor = actorId,
                metadata = metadata,
                eventType = eventType,
                username = username
            )
        )
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
