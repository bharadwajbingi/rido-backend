package com.rido.profile.service

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.net.URL
import java.util.UUID

interface StorageService {
    fun generateSignedUrl(fileName: String, contentType: String): Mono<String>
}

@Service
class SupabaseStorageService : StorageService {
    // In a real implementation, this would use the Supabase/S3 SDK to generate a pre-signed URL.
    // For now, we return a mock URL for local development/testing.
    
    override fun generateSignedUrl(fileName: String, contentType: String): Mono<String> {
        val uniqueFileName = "${UUID.randomUUID()}-$fileName"
        // Mock signed URL
        return Mono.just("https://mock-storage.rido.com/upload/$uniqueFileName?token=mock-token")
    }
}
