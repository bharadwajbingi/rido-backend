package com.rido.profile.controller

import com.rido.profile.dto.AddAddressRequest
import com.rido.profile.dto.RiderAddressResponse
import com.rido.profile.service.AddressService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/profile/rider/addresses")
class AddressController(
    private val addressService: AddressService
) {

    @GetMapping
    fun getAddresses(@RequestHeader("X-User-ID") userId: Long): Flux<RiderAddressResponse> {
        return addressService.getAddresses(userId)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addAddress(
        @RequestHeader("X-User-ID") userId: Long,
        @RequestBody request: AddAddressRequest
    ): Mono<RiderAddressResponse> {
        return addressService.addAddress(userId, request)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAddress(
        @RequestHeader("X-User-ID") userId: Long,
        @PathVariable id: UUID
    ): Mono<Void> {
        return addressService.deleteAddress(userId, id)
    }
}
