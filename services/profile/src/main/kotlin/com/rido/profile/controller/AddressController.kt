package com.rido.profile.controller

import com.rido.profile.dto.AddAddressRequest
import com.rido.profile.dto.RiderAddressResponse
import com.rido.profile.service.AddressService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/profile/rider/addresses")
class AddressController(
    private val addressService: AddressService
) {

    @GetMapping
    fun getAddresses(@RequestHeader("X-User-ID") userId: UUID): Flux<RiderAddressResponse> {
        return addressService.getAddresses(userId)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addAddress(
        @RequestHeader("X-User-ID") userId: UUID,
        @RequestBody request: AddAddressRequest
    ): Mono<RiderAddressResponse> {
        return addressService.addAddress(userId, request)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAddress(
        @RequestHeader("X-User-ID") userId: UUID,
        @PathVariable id: UUID
    ): Mono<Void> {
        return addressService.deleteAddress(userId, id)
    }
}
