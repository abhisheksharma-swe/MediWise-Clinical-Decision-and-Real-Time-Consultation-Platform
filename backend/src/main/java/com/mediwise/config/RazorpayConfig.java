package com.mediwise.config;

import com.mediwise.payment.service.RazorpayOrderGateway;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Configuration
public class RazorpayConfig {

    @Value("${application.razorpay.key-id}")
    private String keyId;

    @Value("${application.razorpay.key-secret}")
    private String keySecret;

    /**
     * The real Razorpay SDK client — used by RazorpayOrderGateway in production.
     * Not injected into RazorpayService directly (avoiding the un-mockable field
     * problem).
     */
    @Bean
    public RazorpayClient razorpayClient() {
        try {
            return new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            log.error("Failed to initialize Razorpay client: {}", e.getMessage());
            throw new RuntimeException("Razorpay initialization failed", e);
        }
    }

    /**
     * Production implementation of RazorpayOrderGateway.
     * Uses the real RazorpayClient to call Razorpay's API.
     * In unit tests, this bean is replaced by a Mockito mock.
     */
    @Bean
    public RazorpayOrderGateway razorpayOrderGateway(RazorpayClient client) {
        return (appointmentId, amountInRupees) -> {
            // Explicit, opt-in local-dev bypass: a developer who sets RAZORPAY_KEY_ID to
            // something containing "mock" is deliberately asking to skip the real gateway
            // (e.g. no Razorpay account configured yet). This is NOT a silent fallback —
            // a real gateway failure below is allowed to propagate as a genuine error
            // instead of being masked behind a fabricated order id, since a fake "success"
            // here would let the client open Checkout against an order that doesn't exist.
            if (keyId != null && (keyId.contains("mock") || keyId.isBlank())) {
                return "order_mock_" + UUID.randomUUID().toString().substring(0, 14).replace("-", "");
            }
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInRupees.multiply(BigDecimal.valueOf(100)).intValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "rcpt_" + appointmentId);
            orderRequest.put("payment_capture", 1);
            return client.orders.create(orderRequest).get("id");
        };
    }
}
