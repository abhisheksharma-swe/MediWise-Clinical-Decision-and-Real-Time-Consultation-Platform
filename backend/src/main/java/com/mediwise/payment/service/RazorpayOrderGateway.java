package com.mediwise.payment.service;

import com.razorpay.RazorpayException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adapter interface that wraps the Razorpay SDK's order creation.
 *
 * The Razorpay SDK exposes sub-resources as public fields (client.orders),
 * not as injectable beans, so a public field can't be mocked directly in unit
 * tests. This interface lets RazorpayService depend on an abstraction instead
 * of the SDK directly, so tests can mock it cleanly.
 */
public interface RazorpayOrderGateway {

    /**
     * Creates an order on Razorpay's servers.
     *
     * @param appointmentId  used as the receipt reference
     * @param amountInRupees server-authoritative fee (e.g. 500.00)
     * @return the Razorpay order ID (e.g. "order_Mx2ABCDEF")
     * @throws RazorpayException if the API call fails
     */
    String createOrder(UUID appointmentId, BigDecimal amountInRupees) throws RazorpayException;
}
