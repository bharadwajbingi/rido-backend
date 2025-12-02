package com.rido.profile.service

import com.rido.profile.dto.AddAddressRequest
import com.rido.profile.dto.RiderAddressResponse
import com.rido.profile.model.RiderAddress
import com.rido.profile.repository.RiderAddressRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class AddressService(
    private val addressRepository: RiderAddressRepository
) {

    fun getAddresses(riderId: Long): Flux<RiderAddressResponse> {
        return addressRepository.findAllByRiderId(riderId)
            .map { it.toResponse() }
    }

    fun addAddress(riderId: Long, request: AddAddressRequest): Mono<RiderAddressResponse> {
        val address = RiderAddress(
            riderId = riderId,
            label = request.label,
            lat = request.lat,
            lng = request.lng
        )
        return addressRepository.save(address)
            .map { it.toResponse() }
    }

    fun deleteAddress(riderId: Long, addressId: UUID): Mono<Void> {
        return addressRepository.findById(addressId)
            .filter { it.riderId == riderId }
            .flatMap { addressRepository.delete(it) }
    }

    private fun RiderAddress.toResponse(): RiderAddressResponse {
        return RiderAddressResponse(
            id = this.id!!,
            label = this.label,
            lat = this.lat,
            lng = this.lng,
            createdAt = this.createdAt
        )
    }
}
