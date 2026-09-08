package com.mediwise.presentation.screens.payment

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.presentation.components.AppButton
import com.mediwise.presentation.components.ErrorStateCard
import com.mediwise.presentation.theme.*
import com.razorpay.Checkout
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    appointmentId: String,
    onPaymentSuccess: (paymentId: String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    // Guards against a rapid double-tap opening Razorpay's Checkout activity twice —
    // there's a window between the tap and the SDK's own activity taking over where
    // uiState.isVerifying hasn't flipped yet, so it can't be relied on alone here.
    var isLaunchingCheckout by remember { mutableStateOf(false) }

    LaunchedEffect(appointmentId) {
        viewModel.initiate(appointmentId)
    }

    // Razorpay reports cancellation/failure as an error via the ViewModel — once that
    // happens (or a fresh order loads), the user is free to tap "Pay Now" again.
    LaunchedEffect(uiState.error, uiState.order?.gatewayOrderId) {
        isLaunchingCheckout = false
    }

    LaunchedEffect(uiState.verifiedPaymentId) {
        uiState.verifiedPaymentId?.let { onPaymentSuccess(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complete Payment", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(color = PrimaryBlue)
                    Spacer(Modifier.height(16.dp))
                    Text("Preparing your payment…", color = TextSecondary)
                }

                uiState.isVerifying -> {
                    CircularProgressIndicator(color = PrimaryBlue)
                    Spacer(Modifier.height(16.dp))
                    Text("Verifying payment…", color = TextSecondary)
                }

                uiState.order != null -> {
                    val order = uiState.order!!
                    Icon(Icons.Default.Payments, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Consultation Fee", fontSize = 14.sp, color = TextSecondary)
                    Text(
                        "₹${"%.2f".format(order.amount)}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your appointment is reserved and will be confirmed once payment succeeds.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(32.dp))

                    if (uiState.error != null) {
                        Text(uiState.error ?: "", color = ErrorRed, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                    }

                    AppButton(
                        text = "Pay Now",
                        enabled = !isLaunchingCheckout,
                        onClick = {
                            if (isLaunchingCheckout) return@AppButton
                            isLaunchingCheckout = true
                            activity?.let { act ->
                                val checkout = Checkout()
                                checkout.setKeyID(order.keyId)
                                val options = JSONObject().apply {
                                    put("name", "MediWise")
                                    put("description", "Consultation payment")
                                    put("order_id", order.gatewayOrderId)
                                    put("currency", order.currency)
                                    put("amount", Math.round(order.amount * 100))
                                    put("theme.color", "#2563EB")
                                    put("method", JSONObject().apply {
                                        put("upi", true)
                                        put("card", true)
                                        put("netbanking", true)
                                        put("wallet", true)
                                        put("emi", false)
                                    })
                                }
                                checkout.open(act, options)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }

                uiState.error != null -> {
                    ErrorStateCard(
                        message = uiState.error ?: "Something went wrong",
                        onRetry = { viewModel.retry(appointmentId) }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Back")
                    }
                }
            }
        }
    }
}
