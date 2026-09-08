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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.ConsultationRecord
import com.mediwise.presentation.theme.BackgroundWhite
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
            state.records.isEmpty() -> Text("No completed consultations yet.", color = TextSecondary, modifier = Modifier.padding(padding).padding(16.dp))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.records, key = { it.id }) { record -> ConsultationRecordCard(record) }
            }
        }
    }
}

@Composable
private fun ConsultationRecordCard(record: ConsultationRecord) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text("Dr. ${record.doctorName}", fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(record.doctorSpecialty.ifBlank { "Medical consultation" }, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text("${record.date}  •  Completed", color = TextSecondary)
            if (record.chiefComplaint.isNotBlank()) Detail("Chief complaint", record.chiefComplaint)
            if (record.notes.isNotBlank()) Detail("Doctor's notes", record.notes)
            if (record.diagnosis.isNotBlank()) Detail("Diagnosis", record.diagnosis)
            if (record.prescription.isNotBlank()) Detail("Prescription", record.prescription)
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
