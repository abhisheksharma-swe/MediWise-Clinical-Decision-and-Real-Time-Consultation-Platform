package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.PaymentOrder

interface PaymentRepository {
    /** Creates (or resumes) a Razorpay order for this appointment. The amount is always
     *  computed server-side from the doctor's consultation fee — never trust a client value. */
    suspend fun initiatePayment(appointmentId: String): Result<PaymentOrder>
    suspend fun verifyPayment(orderId: String, paymentId: String, signature: String): Result<String>
}
