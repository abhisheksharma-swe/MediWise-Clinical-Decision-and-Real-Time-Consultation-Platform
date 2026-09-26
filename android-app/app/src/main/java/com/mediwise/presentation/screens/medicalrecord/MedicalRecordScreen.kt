package com.mediwise.presentation.screens.medicalrecord

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediwise.domain.model.Allergy
import com.mediwise.domain.model.Condition
import com.mediwise.domain.model.Medication
import com.mediwise.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalRecordScreen(
    patientId: String? = null,
    onBackClick: () -> Unit,
    viewModel: MedicalRecordViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<RecordDialogType?>(null) }

    LaunchedEffect(patientId) { viewModel.load(patientId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medical Record", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
                RecordSection(
                    title = "Conditions",
                    isEmpty = uiState.conditions.isEmpty(),
                    emptyText = "No conditions recorded",
                    onAdd = { activeDialog = RecordDialogType.CONDITION }
                ) {
                    uiState.conditions.forEach { ConditionRow(it) }
                }
            }
            item {
                RecordSection(
                    title = "Allergies",
                    isEmpty = uiState.allergies.isEmpty(),
                    emptyText = "No allergies recorded",
                    onAdd = { activeDialog = RecordDialogType.ALLERGY }
                ) {
                    uiState.allergies.forEach { AllergyRow(it) }
                }
            }
            item {
                RecordSection(
                    title = "Medications",
                    isEmpty = uiState.medications.isEmpty(),
                    emptyText = "No medications recorded",
                    onAdd = { activeDialog = RecordDialogType.MEDICATION }
                ) {
                    uiState.medications.forEach { MedicationRow(it) }
                }
            }
            if (uiState.error != null) {
                item { Text(uiState.error ?: "", color = ErrorRed, fontSize = 13.sp) }
            }
        }
    }

    when (activeDialog) {
        RecordDialogType.CONDITION -> AddConditionDialog(
            onDismiss = { activeDialog = null },
            onSave = { name, notes -> viewModel.addCondition(name, null, notes); activeDialog = null }
        )
        RecordDialogType.ALLERGY -> AddAllergyDialog(
            onDismiss = { activeDialog = null },
            onSave = { allergen, severity, reaction -> viewModel.addAllergy(allergen, reaction, severity, null); activeDialog = null }
        )
        RecordDialogType.MEDICATION -> AddMedicationDialog(
            onDismiss = { activeDialog = null },
            onSave = { name, dosage, frequency -> viewModel.addMedication(name, dosage, frequency); activeDialog = null }
        )
        null -> {}
    }
}

private enum class RecordDialogType { CONDITION, ALLERGY, MEDICATION }

@Composable
private fun RecordSection(
    title: String,
    isEmpty: Boolean,
    emptyText: String,
    onAdd: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Add $title", tint = PrimaryBlue)
                }
            }
            if (isEmpty) {
                Text(emptyText, color = TextSecondary, fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun ConditionRow(condition: Condition) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(condition.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            StatusChip(condition.status)
        }
        if (condition.notes.isNotBlank()) Text(condition.notes, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun AllergyRow(allergy: Allergy) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(allergy.allergen, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
            if (allergy.severity.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                StatusChip(allergy.severity)
            }
        }
        if (allergy.reaction.isNotBlank()) Text(allergy.reaction, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun MedicationRow(medication: Medication) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(medication.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
        val details = listOfNotNull(medication.dosage.ifBlank { null }, medication.frequency.ifBlank { null }).joinToString(" • ")
        if (details.isNotBlank()) Text(details, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun StatusChip(label: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = PrimaryBlueLight) {
        Text(label, color = PrimaryBlue, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun AddConditionDialog(onDismiss: () -> Unit, onSave: (name: String, notes: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Condition") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Condition name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), notes.trim()) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddAllergyDialog(onDismiss: () -> Unit, onSave: (allergen: String, severity: String?, reaction: String) -> Unit) {
    var allergen by remember { mutableStateOf("") }
    var reaction by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("MILD") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Allergy") },
        text = {
            Column {
                OutlinedTextField(value = allergen, onValueChange = { allergen = it }, label = { Text("Allergen *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = reaction, onValueChange = { reaction = it }, label = { Text("Reaction") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MILD", "MODERATE", "SEVERE").forEach { level ->
                        FilterChip(selected = severity == level, onClick = { severity = level }, label = { Text(level, fontSize = 11.sp) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(allergen.trim(), severity, reaction.trim()) }, enabled = allergen.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddMedicationDialog(onDismiss: () -> Unit, onSave: (name: String, dosage: String, frequency: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Medication") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Medication name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text("Dosage") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = frequency, onValueChange = { frequency = it }, label = { Text("Frequency") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), dosage.trim(), frequency.trim()) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
