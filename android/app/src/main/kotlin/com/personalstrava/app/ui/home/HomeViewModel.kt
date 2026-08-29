package com.personalstrava.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personalstrava.app.data.local.AppDatabase
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.healthconnect.HealthConnectManager
import com.personalstrava.app.quotes.Quotes
import com.personalstrava.app.steps.StepPreferences
import com.personalstrava.app.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val todaySteps: Int? = null,
    val healthConnectAvailable: Boolean = true,
    val recentActivities: List<ActivityEntity> = emptyList(),
    val quote: String = Quotes.quoteOfTheDay(),
    val weather: List<WeatherRepository.DayForecast>? = null,
    val weatherLoading: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val healthConnect = HealthConnectManager(application)
    private val stepPrefs = StepPreferences(application)
    private val weatherRepository = WeatherRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Always-on tracking (StepTrackingService) is the more reliable source when the user has
        // opted in — it keeps writing to daily_stats regardless of whether Health Connect has any
        // source actually feeding it, and stays live via this Flow rather than a one-shot read.
        if (stepPrefs.isAlwaysOnEnabled) {
            viewModelScope.launch {
                db.dailyStatsDao().observeByDate(LocalDate.now().toString()).collect { row ->
                    _uiState.value = _uiState.value.copy(todaySteps = row?.steps, healthConnectAvailable = healthConnect.isAvailable)
                }
            }
        } else {
            refreshSteps()
        }
        viewModelScope.launch {
            db.activityDao().observeRecent(limit = 5).collect { activities ->
                _uiState.value = _uiState.value.copy(recentActivities = activities)
            }
        }
    }

    /** Called once HomeScreen has confirmed location permission is granted — this never requests
     *  it itself (see WeatherRepository's own doc comment). A no-op forecast (null result) just
     *  means the card doesn't render; never a crash or an error the user has to dismiss. */
    fun loadWeather() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(weatherLoading = true)
            val forecast = weatherRepository.fetchForecast()
            _uiState.value = _uiState.value.copy(weather = forecast, weatherLoading = false)
        }
    }

    fun refreshSteps() {
        if (stepPrefs.isAlwaysOnEnabled) return // kept fresh by the observeByDate collector above instead
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(healthConnectAvailable = healthConnect.isAvailable)
            val steps = healthConnect.readStepsForDay(LocalDate.now())
            _uiState.value = _uiState.value.copy(todaySteps = steps)
        }
    }
}
