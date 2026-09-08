package com.mediwise.core.payment

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class RazorpayResult {
    data class Success(val razorpayPaymentId: String, val razorpayOrderId: String?, val razorpaySignature: String?) : RazorpayResult()
    data class Failure(val code: Int, val description: String?) : RazorpayResult()
}

/**
 * Razorpay's Checkout SDK delivers its result to the hosting Activity via the
 * synchronous [com.razorpay.PaymentResultListener] callback, not through Activity Result
 * APIs or a Flow. [MainActivity] implements that listener and republishes the outcome
 * here so [com.mediwise.presentation.screens.payment.PaymentViewModel] can await it.
 */
@Singleton
class RazorpayResultBus @Inject constructor() {
    private val _results = MutableSharedFlow<RazorpayResult>(replay = 0, extraBufferCapacity = 1)
    val results: SharedFlow<RazorpayResult> = _results

    fun emit(result: RazorpayResult) {
        _results.tryEmit(result)
    }
}
