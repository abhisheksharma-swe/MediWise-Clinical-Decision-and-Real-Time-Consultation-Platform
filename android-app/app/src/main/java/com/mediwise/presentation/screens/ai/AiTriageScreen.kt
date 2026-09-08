package com.mediwise.presentation.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.AiTriageReport
import com.mediwise.presentation.theme.AIPurple
import com.mediwise.presentation.theme.AccentGreen
import com.mediwise.presentation.theme.BackgroundWhite
import com.mediwise.presentation.theme.ErrorRed
import com.mediwise.presentation.theme.PrimaryBlue
import com.mediwise.presentation.theme.TextPrimary
import com.mediwise.presentation.theme.TextSecondary

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiTriageScreen(
    patientId: String? = null,
    onBackClick: () -> Unit,
    onDoctorClick: (String) -> Unit,
    viewModel: AiTriageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPatientEntry = patientId.isNullOrBlank()
    var symptoms by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("") }

    LaunchedEffect(patientId) { viewModel.load(patientId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPatientEntry) "AI Symptom Triage" else "Patient AI Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        if (state.isLoading && state.report == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = AIPurple.copy(alpha = 0.08f)), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = AIPurple, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(12.dp))
                        Text("This is decision support, not a diagnosis. Seek urgent care for severe or worsening symptoms.", color = TextPrimary)
                    }
                }
            }

            if (isPatientEntry) {
                item {
                    Text("Describe your symptoms", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Separate symptoms with commas or new lines.", color = TextSecondary)
                }
                item {
                    OutlinedTextField(
                        value = symptoms,
                        onValueChange = { symptoms = it },
                        label = { Text("Symptoms") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Severity", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Mild", "Moderate", "Severe").forEach { option ->
                            FilterChip(selected = severity == option, onClick = { severity = option }, label = { Text(option) })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Additional notes (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Button(
                        onClick = { viewModel.submit(symptoms, severity, notes) },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Analyze symptoms", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            state.error?.let { error ->
                item { Text(error, color = ErrorRed) }
            }

            state.report?.let { report ->
                item { ReportSummary(report) }
                if (report.riskFactors.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Risk factors", fontWeight = FontWeight.Bold, color = TextPrimary)
                                report.riskFactors.forEach { factor -> Text("• $factor", color = TextPrimary, modifier = Modifier.padding(top = 6.dp)) }
                            }
                        }
                    }
                }
                if (report.matchedDoctors.isNotEmpty()) {
                    item { Text("Verified doctors for ${report.suggestedSpecialty}", fontWeight = FontWeight.Bold, color = TextPrimary) }
                    items(report.matchedDoctors) { doctor ->
                        Card(onClick = { onDoctorClick(doctor.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Dr. ${doctor.fullName}", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(doctor.specialty, color = TextSecondary)
                                    if (doctor.verified) Text("Verified", color = AccentGreen, fontWeight = FontWeight.Medium)
                                }
                                Text("View", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSummary(report: AiTriageReport) {
    val urgent = report.urgencyScore >= 70
    Card(colors = CardDefaults.cardColors(containerColor = if (urgent) ErrorRed.copy(alpha = 0.08f) else Color(0xFFEFF8F2)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (urgent) Icons.Default.Warning else Icons.Default.CheckCircle, null, tint = if (urgent) ErrorRed else AccentGreen)
                Spacer(Modifier.size(8.dp))
                Text(if (urgent) "Needs prompt medical attention" else "Triage assessment", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text("Urgency score: ${report.urgencyScore}/100", color = TextPrimary)
            Text("Suggested specialty: ${report.suggestedSpecialty.ifBlank { "General Medicine" }}", color = TextPrimary)
            if (report.confidence > 0) Text("Confidence: ${(report.confidence * 100).toInt()}%", color = TextSecondary)
            if (report.recommendation.isNotBlank()) Text(report.recommendation, color = TextPrimary, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
