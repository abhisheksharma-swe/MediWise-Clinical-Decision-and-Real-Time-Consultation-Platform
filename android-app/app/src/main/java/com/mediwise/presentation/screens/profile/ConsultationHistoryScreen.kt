package com.mediwise.presentation.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.model.PrescriptionModel
import com.mediwise.presentation.theme.BackgroundWhite
import com.mediwise.presentation.theme.Divider
import com.mediwise.presentation.theme.ErrorRed
import com.mediwise.presentation.theme.PrimaryBlue
import com.mediwise.presentation.theme.TextPrimary
import com.mediwise.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationHistoryScreen(
    patientId: String? = null,
    onBackClick: () -> Unit,
    viewModel: ConsultationHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(patientId) { viewModel.load(patientId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consultation History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        when {
            state.isLoading -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.padding(24.dp), color = PrimaryBlue)
            }
            state.error != null -> Text(state.error ?: "Unable to load history", color = ErrorRed, modifier = Modifier.padding(padding).padding(16.dp))
            state.consultations.isEmpty() -> Text("No completed consultations yet.", color = TextSecondary, modifier = Modifier.padding(padding).padding(16.dp))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.consultations, key = { it.id }) { consultation ->
                    ConsultationCard(consultation, state.prescriptionsByConsultation[consultation.id].orEmpty())
                }
            }
        }
    }
}

@Composable
private fun ConsultationCard(consultation: Consultation, prescriptions: List<PrescriptionModel>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text(consultation.createdAt.take(10).ifBlank { "Consultation" }, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(consultation.status.ifBlank { "COMPLETED" }, color = TextSecondary, fontSize = 12.sp)
            if (consultation.chiefComplaint.isNotBlank()) Detail("Chief complaint", consultation.chiefComplaint)
            if (consultation.symptoms.isNotEmpty()) Detail("Symptoms", consultation.symptoms.joinToString(", "))
            if (consultation.observations.isNotBlank()) Detail("Observations", consultation.observations)
            if (consultation.assessment.isNotBlank()) Detail("Assessment", consultation.assessment)
            if (consultation.treatmentPlan.isNotBlank()) Detail("Treatment plan", consultation.treatmentPlan)
            if (consultation.doctorNotes.isNotBlank()) Detail("Doctor's notes", consultation.doctorNotes)

            if (prescriptions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Divider)
                Spacer(Modifier.height(10.dp))
                Text("Prescription", fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                prescriptions.forEach { prescription ->
                    prescription.items.forEach { medItem ->
                        Column(Modifier.padding(top = 6.dp)) {
                            Text(medItem.medicineName, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                            val details = listOfNotNull(
                                medItem.dosage.ifBlank { null },
                                medItem.frequency.ifBlank { null },
                                medItem.duration.ifBlank { null }
                            ).joinToString(" • ")
                            if (details.isNotBlank()) Text(details, color = TextSecondary, fontSize = 12.sp)
                            if (medItem.instructions.isNotBlank()) Text(medItem.instructions, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
        Text(value, color = TextPrimary)
    }
}
