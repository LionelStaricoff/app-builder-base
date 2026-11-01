package com.openlab.mx.movo.payment.controller.api;

import com.openlab.mx.movo.payment.service.RidePaymentService;
import com.openlab.mx.movo.shared.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RidePaymentApiController {

    private final RidePaymentService ridePaymentService;
    private final CurrentUser currentUser;

    @PostMapping("/{rideId}/payment/checkout")
    public ResponseEntity<Map<String, String>> checkout(@PathVariable Long rideId) {
        currentUser.require();
        return ResponseEntity.ok(ridePaymentService.createCheckout(rideId));
    }
}
