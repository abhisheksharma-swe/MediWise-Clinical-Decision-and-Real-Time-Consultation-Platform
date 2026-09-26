package com.mediwise.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediwise.domain.model.PrescriptionItemModel
import com.mediwise.presentation.theme.ErrorRed
import com.mediwise.presentation.theme.SurfaceWhite

private val FOOD_TIMING_OPTIONS = listOf("BEFORE_FOOD" to "Before food", "AFTER_FOOD" to "After food", "NOT_APPLICABLE" to "Not applicable")

/** One repeatable row in a doctor's structured prescription entry — replaces the old free-text field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionItemInput(
    item: PrescriptionItemModel,
    onChange: (PrescriptionItemModel) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var foodTimingExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = item.medicineName,
                    onValueChange = { onChange(item.copy(medicineName = it)) },
                    label = { Text("Medicine name *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove medicine", tint = ErrorRed)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.dosage,
                    onValueChange = { onChange(item.copy(dosage = it)) },
                    label = { Text("Dosage") },
                    placeholder = { Text("e.g. 500mg") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.frequency,
                    onValueChange = { onChange(item.copy(frequency = it)) },
                    label = { Text("Frequency") },
                    placeholder = { Text("e.g. 2x/day") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.duration,
                    onValueChange = { onChange(item.copy(duration = it)) },
                    label = { Text("Duration") },
                    placeholder = { Text("e.g. 5 days") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                ExposedDropdownMenuBox(
                    expanded = foodTimingExpanded,
                    onExpandedChange = { foodTimingExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = FOOD_TIMING_OPTIONS.firstOrNull { it.first == item.beforeAfterFood }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Timing") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = foodTimingExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = foodTimingExpanded, onDismissRequest = { foodTimingExpanded = false }) {
                        FOOD_TIMING_OPTIONS.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onChange(item.copy(beforeAfterFood = value))
                                    foodTimingExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = item.instructions,
                onValueChange = { onChange(item.copy(instructions = it)) },
                label = { Text("Instructions (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3
            )
        }
    }
}
