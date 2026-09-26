package com.mediwise.presentation.screens.medicalrecord

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
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
import com.mediwise.domain.model.MedicalDocument
import com.mediwise.presentation.theme.*

private val DOCUMENT_TYPES = listOf(
    "LAB_REPORT" to "Lab Report", "XRAY" to "X-Ray", "MRI_CT" to "MRI/CT",
    "PRESCRIPTION_SCAN" to "Prescription Scan", "DISCHARGE_SUMMARY" to "Discharge Summary", "OTHER" to "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalDocumentsScreen(
    patientId: String? = null,
    onBackClick: () -> Unit,
    viewModel: MedicalDocumentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingType by remember { mutableStateOf("OTHER") }
    var showTypePicker by remember { mutableStateOf(false) }

    LaunchedEffect(patientId) { viewModel.load(patientId) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                val fileName = queryDisplayName(context, it) ?: "document"
                if (bytes != null) viewModel.upload(pendingType, bytes, fileName, mimeType)
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medical Documents", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showTypePicker = true },
                containerColor = PrimaryBlue
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Upload")
            }
        },
        containerColor = BackgroundWhite
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            uiState.documents.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No documents uploaded yet.", color = TextSecondary)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.documents, key = { it.id }) { doc -> DocumentRow(doc, context) }
            }
        }

        if (uiState.error != null) {
            Text(uiState.error ?: "", color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(padding).padding(16.dp))
        }
        if (uiState.isUploading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        }
    }

    if (showTypePicker) {
        AlertDialog(
            onDismissRequest = { showTypePicker = false },
            title = { Text("Document type") },
            text = {
                Column {
                    DOCUMENT_TYPES.forEach { (value, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                pendingType = value
                                showTypePicker = false
                                filePickerLauncher.launch("*/*")
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTypePicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DocumentRow(document: MedicalDocument, context: android.content.Context) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            if (document.url.isNotBlank()) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(document.url)))
                } catch (_: Exception) {}
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryBlue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    document.originalFilename.ifBlank { "Document" },
                    fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp
                )
                Text(
                    DOCUMENT_TYPES.firstOrNull { it.first == document.documentType }?.second ?: document.documentType,
                    color = TextSecondary, fontSize = 12.sp
                )
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    } catch (_: Exception) {
        null
    }
}
