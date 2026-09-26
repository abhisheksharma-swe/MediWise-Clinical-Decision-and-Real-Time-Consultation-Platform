package com.mediwise.presentation.screens.consultation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.PrescriptionItemModel
import com.mediwise.presentation.components.PrescriptionItemInput
import com.mediwise.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationDetailScreen(
    appointmentId: String,
    onBackClick: () -> Unit,
    viewModel: ConsultationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var symptomInput by remember { mutableStateOf("") }

    LaunchedEffect(appointmentId) { viewModel.load(appointmentId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consultation", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = "Clinical Notes") {
                    OutlinedTextField(
                        value = uiState.chiefComplaint,
                        onValueChange = { viewModel.updateChiefComplaint(it) },
                        label = { Text("Chief complaint") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Symptoms", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    FlowRowSymptoms(symptoms = uiState.symptoms, onRemove = { viewModel.removeSymptom(it) })
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = symptomInput,
                            onValueChange = { symptomInput = it },
                            placeholder = { Text("Add a symptom") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            viewModel.addSymptom(symptomInput)
                            symptomInput = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add symptom", tint = PrimaryBlue)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.observations,
                        onValueChange = { viewModel.updateObservations(it) },
                        label = { Text("Observations") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.assessment,
                        onValueChange = { viewModel.updateAssessment(it) },
                        label = { Text("Assessment / diagnosis") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.treatmentPlan,
                        onValueChange = { viewModel.updateTreatmentPlan(it) },
                        label = { Text("Treatment plan") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        label = { Text("Doctor's notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.saveConsultation(appointmentId) },
                        enabled = !uiState.isSavingNotes,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (uiState.isSavingNotes) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (uiState.notesSaved) "Saved — Update" else "Save Consultation Notes")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Prescription") {
                    if (uiState.consultationId == null) {
                        Text(
                            "Save the consultation notes above before adding a prescription.",
                            color = TextSecondary, fontSize = 13.sp
                        )
                    }
                    uiState.prescriptionItems.forEachIndexed { index, prescItem ->
                        PrescriptionItemInput(
                            item = prescItem,
                            onChange = { viewModel.updatePrescriptionItem(index, it) },
                            onRemove = { viewModel.removePrescriptionItem(index) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.addPrescriptionItem() },
                        enabled = uiState.consultationId != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Medicine")
                    }
                    if (uiState.prescriptionItems.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.submitPrescription() },
                            enabled = !uiState.isSavingPrescription,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            if (uiState.isSavingPrescription) {
                                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (uiState.prescriptionSaved) "Prescription Saved" else "Save Prescription")
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Follow-up") {
                    if (uiState.consultationId == null) {
                        Text(
                            "Save the consultation notes above before recommending a follow-up.",
                            color = TextSecondary, fontSize = 13.sp
                        )
                    }
                    OutlinedTextField(
                        value = uiState.followUpDate,
                        onValueChange = { viewModel.updateFollowUpDate(it) },
                        label = { Text("Recommended date (YYYY-MM-DD)") },
                        enabled = uiState.consultationId != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.followUpReason,
                        onValueChange = { viewModel.updateFollowUpReason(it) },
                        label = { Text("Reason") },
                        enabled = uiState.consultationId != null,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.submitFollowUp() },
                        enabled = uiState.consultationId != null && !uiState.isSavingFollowUp,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (uiState.isSavingFollowUp) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (uiState.followUpSaved) "Follow-up Saved" else "Recommend Follow-up")
                        }
                    }
                }
            }

            if (uiState.error != null) {
                item {
                    Text(uiState.error ?: "", color = ErrorRed, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSymptoms(symptoms: List<String>, onRemove: (Int) -> Unit) {
    if (symptoms.isEmpty()) {
        Text("No symptoms added yet", color = TextSecondary, fontSize = 13.sp)
        return
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        symptoms.forEachIndexed { index, symptom ->
            AssistChip(
                onClick = { onRemove(index) },
                label = { Text(symptom, fontSize = 12.sp) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
            )
        }
    }
}
