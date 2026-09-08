package com.mediwise.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.mediwise.core.payment.RazorpayResult
import com.mediwise.core.payment.RazorpayResultBus
import com.mediwise.presentation.navigation.NavGraph
import com.mediwise.presentation.theme.ClinicalSystemTheme
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Extends [FragmentActivity] (rather than the base ComponentActivity) because
 * androidx.biometric's BiometricPrompt requires a FragmentActivity host.
 * FragmentActivity extends ComponentActivity, so Compose's `setContent`/
 * `enableEdgeToEdge` extensions still apply unchanged.
 *
 * Also implements [PaymentResultWithDataListener] because Razorpay's Checkout SDK
 * requires the *hosting Activity* itself to receive its result callback (there is no
 * Activity Result API or suspend-friendly hook) — the outcome is republished onto
 * [RazorpayResultBus] so [com.mediwise.presentation.screens.payment.PaymentViewModel]
 * can observe it as a Flow.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity(), PaymentResultWithDataListener {

    @Inject lateinit var razorpayResultBus: RazorpayResultBus

    /** Route to navigate to once, read from an FCM notification tap intent extra. */
    var pendingDeepLinkRoute: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLinkRoute = intent?.getStringExtra(EXTRA_DEEP_LINK_ROUTE)
        setContent {
            ClinicalSystemTheme {
                NavGraph(deepLinkRoute = pendingDeepLinkRoute)
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        razorpayResultBus.emit(
            RazorpayResult.Success(
                razorpayPaymentId = razorpayPaymentId ?: paymentData?.paymentId ?: "",
                razorpayOrderId = paymentData?.orderId,
                razorpaySignature = paymentData?.signature
            )
        )
    }

    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        razorpayResultBus.emit(RazorpayResult.Failure(code, description))
    }

    companion object {
        const val EXTRA_DEEP_LINK_ROUTE = "deep_link_route"
    }
}
