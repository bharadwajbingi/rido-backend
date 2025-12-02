package com.rido.profile.service

import com.rido.profile.dto.DriverDocumentResponse
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
    private val auditLogRepository: AuditLogRepository
) {

    @Transactional
    fun approveDocument(adminId: Long, documentId: UUID): Mono<DriverDocumentResponse> {
        return documentRepository.findById(documentId)
            .switchIfEmpty(Mono.error(RuntimeException("Document not found")))
            .flatMap { doc ->
                val updatedDoc = doc.copy(
                    status = DocumentStatus.APPROVED,
                    reviewedBy = adminId
                )
                documentRepository.save(updatedDoc)
                    .flatMap { saved ->
                        logAudit(adminId, "APPROVE", "DriverDocument", saved.id.toString(), "Approved document")
                            .thenReturn(saved.toResponse())
                    }
            }
    }

    @Transactional
    fun rejectDocument(adminId: Long, documentId: UUID, reason: String): Mono<DriverDocumentResponse> {
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
                        logAudit(adminId, "REJECT", "DriverDocument", saved.id.toString(), "Rejected document: $reason")
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
