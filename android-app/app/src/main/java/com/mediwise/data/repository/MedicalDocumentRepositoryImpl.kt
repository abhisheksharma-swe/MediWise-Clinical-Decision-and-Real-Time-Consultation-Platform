package com.mediwise.data.repository

import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.remote.api.MedicalDocumentApi
import com.mediwise.data.remote.dto.toDomain
import com.mediwise.domain.model.MedicalDocument
import com.mediwise.domain.repository.MedicalDocumentRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicalDocumentRepositoryImpl @Inject constructor(
    private val api: MedicalDocumentApi
) : MedicalDocumentRepository {

    override suspend fun upload(
        patientId: String,
        documentType: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<MedicalDocument> {
        return safeApiCall {
            val patientIdPart = patientId.toRequestBody("text/plain".toMediaTypeOrNull())
            val documentTypePart = documentType.toRequestBody("text/plain".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "file", fileName, fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            val response = api.upload(patientIdPart, documentTypePart, filePart)
            response.data?.toDomain() ?: throw Exception("Empty upload response")
        }
    }

    override suspend fun listForPatient(patientId: String): Result<List<MedicalDocument>> {
        return safeApiCall {
            api.listForPatient(patientId).data?.map { it.toDomain() } ?: emptyList()
        }
    }
}
