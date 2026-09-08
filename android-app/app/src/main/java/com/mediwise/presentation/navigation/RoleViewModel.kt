package com.mediwise.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.domain.model.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes only the logged-in user's role, so navigation-level composables (the bottom
 * nav bar, tab routing) can adapt without instantiating a heavier, screen-specific
 * ViewModel (e.g. ProfileViewModel) just to read one field.
 */
@HiltViewModel
class RoleViewModel @Inject constructor(
    sessionDataStore: SessionDataStore
) : ViewModel() {
    val role: StateFlow<Role?> = sessionDataStore.userRoleEnum
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
