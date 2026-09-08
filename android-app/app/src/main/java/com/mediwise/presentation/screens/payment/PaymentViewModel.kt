package com.mediwise.presentation.screens.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.payment.RazorpayResult
import com.mediwise.core.payment.RazorpayResultBus
import com.mediwise.core.result.Result
import com.mediwise.domain.model.PaymentOrder
import com.mediwise.domain.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentUiState(
    val isLoading: Boolean = false,
    val order: PaymentOrder? = null,
    val isVerifying: Boolean = false,
    val verifiedPaymentId: String? = null,
    val error: String? = null
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val razorpayResultBus: RazorpayResultBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var pendingOrderId: String? = null

    init {
        viewModelScope.launch {
            razorpayResultBus.results.collect { result ->
                val orderId = pendingOrderId ?: return@collect
                when (result) {
                    is RazorpayResult.Success -> {
                        pendingOrderId = null
                        verifyPayment(
                            orderId = result.razorpayOrderId ?: orderId,
                            paymentId = result.razorpayPaymentId,
                            signature = result.razorpaySignature.orEmpty()
                        )
                    }
                    is RazorpayResult.Failure -> {
                        pendingOrderId = null
                        _uiState.update {
                            it.copy(error = result.description ?: "Payment was not completed.")
                        }
                    }
                }
            }
        }
    }

    fun initiate(appointmentId: String) {
        if (_uiState.value.order != null || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = paymentRepository.initiatePayment(appointmentId)) {
                is Result.Success -> {
                    pendingOrderId = result.data.gatewayOrderId
                    _uiState.update { it.copy(isLoading = false, order = result.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun retry(appointmentId: String) {
        _uiState.update { it.copy(order = null, error = null) }
        initiate(appointmentId)
    }

    private fun verifyPayment(orderId: String, paymentId: String, signature: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, error = null) }
            when (val result = paymentRepository.verifyPayment(orderId, paymentId, signature)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isVerifying = false, verifiedPaymentId = result.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isVerifying = false, error = result.exception.message) }
                }
                is Result.Loading -> {}
            }
        }
    }
}
