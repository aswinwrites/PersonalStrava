package com.personalstrava.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personalstrava.app.data.local.AppDatabase
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.healthconnect.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val todaySteps: Int? = null,
    val healthConnectAvailable: Boolean = true,
    val recentActivities: List<ActivityEntity> = emptyList(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val healthConnect = HealthConnectManager(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshSteps()
        viewModelScope.launch {
            db.activityDao().observeRecent(limit = 5).collect { activities ->
                _uiState.value = _uiState.value.copy(recentActivities = activities)
            }
        }
    }

    fun refreshSteps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(healthConnectAvailable = healthConnect.isAvailable)
            val steps = healthConnect.readStepsForDay(LocalDate.now())
            _uiState.value = _uiState.value.copy(todaySteps = steps)
        }
    }
}
