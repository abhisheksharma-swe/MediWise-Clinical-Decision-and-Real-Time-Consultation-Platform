package com.mediwise.presentation.screens.doctors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Doctor
import com.mediwise.domain.repository.DoctorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DoctorListUiState(
    val isLoading: Boolean = false,
    val doctors: List<Doctor> = emptyList(),
    val favoriteDoctors: List<Doctor> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedSpecialty: String = "All",
    val error: String? = null
)

@HiltViewModel
class DoctorViewModel @Inject constructor(
    private val repository: DoctorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorListUiState())
    val uiState: StateFlow<DoctorListUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val specialties = listOf("All", "Cardiology", "Dermatology", "Neurology",
        "Orthopedics", "General Medicine", "Pediatrics", "Psychiatry")

    init {
        observeSearch()
        loadDoctors()
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            when (val res = repository.getFavorites()) {
                is Result.Success -> {
                    val favIds = res.data.map { it.id }.toSet()
                    _uiState.update { it.copy(favorites = favIds, favoriteDoctors = res.data) }
                }
                else -> {}
            }
        }
    }


    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .distinctUntilChanged()
                .collect {
                    loadDoctors()
                }
        }
    }

    fun loadDoctors() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val currentState = _uiState.value
            val result = repository.getDoctors(
                specialty = currentState.selectedSpecialty,
                search = currentState.searchQuery
            )
            
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, doctors = result.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        // Update the visible text immediately so the field always reflects what the
        // user typed. The debounced _searchQuery flow (below) only gates when the
        // actual network search is triggered - it must not be the source of truth
        // for the TextField's value, or unrelated uiState updates (favorites/specialty/
        // loading) that land while typing will snap the field back to a stale value.
        _uiState.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    fun onSpecialtySelected(specialty: String) {
        _uiState.update { it.copy(selectedSpecialty = specialty) }
        loadDoctors()
    }

    fun toggleFavorite(doctorId: String) {
        viewModelScope.launch {
            val currentFavorites = _uiState.value.favorites.toMutableSet()
            val isFavorite = currentFavorites.contains(doctorId)
            
            if (isFavorite) {
                currentFavorites.remove(doctorId)
            } else {
                currentFavorites.add(doctorId)
            }
            _uiState.update { it.copy(favorites = currentFavorites) }
            
            // Sync with backend
            repository.toggleFavorite(doctorId)
        }
    }
}
