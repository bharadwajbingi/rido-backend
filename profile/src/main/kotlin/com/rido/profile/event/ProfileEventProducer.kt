package com.rido.profile.event

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class ProfileEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(ProfileEventProducer::class.java)

    fun publishProfileUpdated(event: ProfileUpdatedEvent) {
        logger.info("Publishing profile.updated event for user ${event.userId}")
        kafkaTemplate.send("profile.updated", event.userId.toString(), event)
    }

    fun publishDocumentUploaded(event: DriverDocumentUploadedEvent) {
        logger.info("Publishing driver.document.uploaded event for driver ${event.driverId}")
        kafkaTemplate.send("driver.document.uploaded", event.driverId.toString(), event)
    }

    fun publishDriverApproved(event: DriverApprovedEvent) {
        logger.info("Publishing driver.approved event for driver ${event.driverId}")
        kafkaTemplate.send("driver.approved", event.driverId.toString(), event)
    }

    fun publishDriverRejected(event: DriverRejectedEvent) {
        logger.info("Publishing driver.rejected event for driver ${event.driverId}")
        kafkaTemplate.send("driver.rejected", event.driverId.toString(), event)
    }
}
