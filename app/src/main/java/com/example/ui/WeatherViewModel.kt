package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.ApiClient
import com.example.data.api.GeminiWeatherClient
import com.example.data.database.WeatherDatabase
import com.example.data.model.FavoriteCity
import com.example.data.model.GeocodingResult
import com.example.data.model.WeatherResponse
import com.example.data.model.AirQualityResponse
import com.example.data.model.AirQualityItem
import com.example.data.model.AirQualityMain
import com.example.data.model.AirQualityComponents
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Idle : WeatherUiState
    object Loading : WeatherUiState
    data class Success(
        val cityName: String,
        val country: String?,
        val weather: WeatherResponse,
        val isFavorite: Boolean,
        val resultId: Long
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val results: List<GeocodingResult>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

sealed interface AiInsightState {
    object Idle : AiInsightState
    object Loading : AiInsightState
    data class Success(val insight: String) : AiInsightState
    data class Error(val message: String) : AiInsightState
}

sealed interface AirQualityUiState {
    object Idle : AirQualityUiState
    object Loading : AirQualityUiState
    data class Success(
        val airQuality: AirQualityResponse,
        val isFallback: Boolean = false
    ) : AirQualityUiState
    data class Error(val message: String, val isKeyMissing: Boolean = false) : AirQualityUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WeatherRepository

    init {
        val database = WeatherDatabase.getDatabase(application)
        repository = WeatherRepository(database.favoriteCityDao(), ApiClient.apiService)
    }

    val favorites: StateFlow<List<FavoriteCity>> = repository.favorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private val _aiInsightState = MutableStateFlow<AiInsightState>(AiInsightState.Idle)
    val aiInsightState: StateFlow<AiInsightState> = _aiInsightState.asStateFlow()

    private val _airQualityState = MutableStateFlow<AirQualityUiState>(AirQualityUiState.Idle)
    val airQualityState: StateFlow<AirQualityUiState> = _airQualityState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var searchJob: Job? = null

    // Local storage preference cache keying
    private val prefs = application.getSharedPreferences("weather_settings_prefs", Context.MODE_PRIVATE)

    // Account state
    val isLoggedIn = MutableStateFlow(false)
    val userEmail = MutableStateFlow<String?>(null)
    val userDisplayName = MutableStateFlow<String?>(null)

    // Interactive settings state backed by local storage persistence
    val useFahrenheit = MutableStateFlow(prefs.getBoolean("use_fahrenheit", false))
    val windSpeedUnit = MutableStateFlow(prefs.getString("wind_speed_unit", "km/h") ?: "km/h") // km/h, mph, m/s
    val radarOverlay = MutableStateFlow(prefs.getString("radar_overlay", "radar") ?: "radar") // radar, wind, temp
    val enableNotifications = MutableStateFlow(prefs.getBoolean("enable_notifications", true))
    val themePreference = MutableStateFlow(prefs.getString("theme_preference", "dark") ?: "dark") // "dark", "light", "system"

    fun signInUser(email: String, name: String) {
        userEmail.value = email
        userDisplayName.value = name
        isLoggedIn.value = true
    }

    fun signOutUser() {
        userEmail.value = null
        userDisplayName.value = null
        isLoggedIn.value = false
    }

    fun toggleTempUnit() {
        val newVal = !useFahrenheit.value
        useFahrenheit.value = newVal
        prefs.edit().putBoolean("use_fahrenheit", newVal).apply()
    }

    fun setWindSpeedUnit(unit: String) {
        windSpeedUnit.value = unit
        prefs.edit().putString("wind_speed_unit", unit).apply()
    }

    fun setRadarOverlayStyle(overlay: String) {
        radarOverlay.value = overlay
        prefs.edit().putString("radar_overlay", overlay).apply()
    }

    fun toggleNotifications() {
        val newVal = !enableNotifications.value
        enableNotifications.value = newVal
        prefs.edit().putBoolean("enable_notifications", newVal).apply()
    }

    fun setThemePreference(theme: String) {
        themePreference.value = theme
        prefs.edit().putString("theme_preference", theme).apply()
    }

    fun clearAllFavorites() {
        viewModelScope.launch {
            favorites.value.forEach {
                repository.removeFavorite(it)
            }
        }
    }

    init {
        // Load default city (London) on startup
        loadWeatherForCoordinates(51.5085, -0.1257, "London", "United Kingdom", 2643743)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.length < 3) {
            _searchUiState.value = SearchUiState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // Debounce search
            _searchUiState.value = SearchUiState.Loading
            try {
                val response = repository.searchCity(query)
                val results = response.results
                if (results.isNullOrEmpty()) {
                    _searchUiState.value = SearchUiState.Success(emptyList())
                } else {
                    _searchUiState.value = SearchUiState.Success(results)
                }
            } catch (e: Exception) {
                _searchUiState.value = SearchUiState.Error("Failed to find cities: ${e.localizedMessage}")
            }
        }
    }

    fun loadWeatherForCoordinates(
        lat: Double,
        lon: Double,
        cityName: String,
        country: String?,
        resultId: Long
    ) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            _aiInsightState.value = AiInsightState.Idle
            try {
                val weather = repository.getForecast(lat, lon)
                val isFav = repository.isFavorite(resultId)
                _uiState.value = WeatherUiState.Success(
                    cityName = cityName,
                    country = country,
                    weather = weather,
                    isFavorite = isFav,
                    resultId = resultId
                )
                // Fetch Air Quality indices concurrently
                fetchAirPollution(lat, lon)
                
                // Auto generate AI diagnostics for loaded weather
                generateAiDiagnostics(cityName, country, weather)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("Failed to fetch weather: ${e.localizedMessage}")
            }
        }
    }

    fun fetchAirPollution(lat: Double, lon: Double) {
        viewModelScope.launch {
            _airQualityState.value = AirQualityUiState.Loading
            var apiKey = ""
            try {
                apiKey = BuildConfig.OPENWEATHER_API_KEY
            } catch (e: Exception) {
                // BuildConfig mapping might be missing temporarily
            }

            val isPlaceholder = apiKey.isBlank() || 
                    apiKey == "place_your_open_weather_api_key_here" || 
                    apiKey == "MY_OPENWEATHER_API_KEY"

            if (isPlaceholder) {
                val simulatedAqi = calculateSimulatedAqi(lat, lon)
                _airQualityState.value = AirQualityUiState.Success(
                    airQuality = AirQualityResponse(
                        list = listOf(
                            AirQualityItem(
                                main = AirQualityMain(aqi = simulatedAqi),
                                components = generateSimulatedComponents(simulatedAqi),
                                dt = System.currentTimeMillis() / 1000
                            )
                        )
                    ),
                    isFallback = true
                )
                return@launch
            }

            try {
                val response = repository.getAirPollution(lat, lon, apiKey)
                if (response.list.isNullOrEmpty()) {
                    _airQualityState.value = AirQualityUiState.Error("No air pollution data is currently available", isKeyMissing = false)
                } else {
                    _airQualityState.value = AirQualityUiState.Success(response, isFallback = false)
                }
            } catch (e: Exception) {
                val message = e.localizedMessage ?: "Unknown key/network error"
                val is401 = message.contains("401") || message.contains("Unauthorized")
                
                val simulatedAqi = calculateSimulatedAqi(lat, lon)
                _airQualityState.value = AirQualityUiState.Success(
                    airQuality = AirQualityResponse(
                        list = listOf(
                            AirQualityItem(
                                main = AirQualityMain(aqi = simulatedAqi),
                                components = generateSimulatedComponents(simulatedAqi),
                                dt = System.currentTimeMillis() / 1000
                            )
                        )
                    ),
                    isFallback = true
                )
            }
        }
    }

    private fun calculateSimulatedAqi(lat: Double, lon: Double): Int {
        val factor = Math.abs(lat + lon).toInt()
        return (factor % 5) + 1
    }

    private fun generateSimulatedComponents(aqi: Int): AirQualityComponents {
        return when (aqi) {
            1 -> AirQualityComponents(co = 180.0, no = 0.05, no2 = 0.5, o3 = 35.0, so2 = 0.2, pm2_5 = 4.2, pm10 = 8.5, nh3 = 0.1)
            2 -> AirQualityComponents(co = 250.0, no = 0.12, no2 = 1.2, o3 = 50.0, so2 = 0.6, pm2_5 = 11.0, pm10 = 18.0, nh3 = 0.3)
            3 -> AirQualityComponents(co = 400.0, no = 0.35, no2 = 2.8, o3 = 75.0, so2 = 1.8, pm2_5 = 22.5, pm10 = 34.2, nh3 = 0.8)
            4 -> AirQualityComponents(co = 650.0, no = 0.82, no2 = 5.5, o3 = 110.0, so2 = 4.2, pm2_5 = 39.8, pm10 = 55.4, nh3 = 1.9)
            else -> AirQualityComponents(co = 980.0, no = 2.1, no2 = 12.4, o3 = 165.0, so2 = 9.8, pm2_5 = 65.2, pm10 = 88.0, nh3 = 4.5)
        }
    }

    fun toggleFavorite(successState: WeatherUiState.Success) {
        viewModelScope.launch {
            if (successState.isFavorite) {
                repository.removeFavorite(
                    FavoriteCity(
                        id = successState.resultId,
                        name = successState.cityName,
                        latitude = successState.weather.latitude,
                        longitude = successState.weather.longitude,
                        country = successState.country,
                        admin1 = null
                    )
                )
                _uiState.value = successState.copy(isFavorite = false)
            } else {
                repository.addFavorite(
                    FavoriteCity(
                        id = successState.resultId,
                        name = successState.cityName,
                        latitude = successState.weather.latitude,
                        longitude = successState.weather.longitude,
                        country = successState.country,
                        admin1 = null
                    )
                )
                _uiState.value = successState.copy(isFavorite = true)
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchUiState.value = SearchUiState.Idle
    }

    private fun generateAiDiagnostics(
        cityName: String,
        country: String?,
        weather: WeatherResponse
    ) {
        val current = weather.current ?: return
        val code = current.weather_code
        val condition = getWmoWeatherCondition(code)

        viewModelScope.launch {
            _aiInsightState.value = AiInsightState.Loading
            try {
                val insight = GeminiWeatherClient.getAiDiagnostics(
                    cityName = cityName,
                    temp = current.temperature_2m,
                    humidity = current.relative_humidity_2m,
                    windSpeed = current.wind_speed_10m,
                    weatherCode = code,
                    condition = condition
                )
                _aiInsightState.value = AiInsightState.Success(insight)
            } catch (e: Exception) {
                _aiInsightState.value = AiInsightState.Error("AI Assistant currently sleeping.")
            }
        }
    }

    private fun getWmoWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Clear skies"
            1, 2, 3 -> "Partly cloudy"
            45, 48 -> "Foggy conditions"
            51, 53, 55 -> "Light drizzle"
            56, 57 -> "Freezing drizzle"
            61, 63, 65 -> "Heavy rain"
            66, 67 -> "Freezing rain"
            71, 73, 75 -> "Snowy conditions"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Severe thunderstorms"
            else -> "Unspecified weather"
        }
    }
}
