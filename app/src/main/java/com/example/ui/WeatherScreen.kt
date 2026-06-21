package com.example.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FavoriteCity
import com.example.data.model.GeocodingResult
import com.example.data.model.WeatherResponse
import com.example.data.model.AirQualityResponse
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()
    val aiInsightState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    val airQualityState by viewModel.airQualityState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf("forecast") }

    val locationPermissionState = rememberPermissionState(
        permission = Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context.applicationContext) }

    // Resolve details card background depending on WMO code
    val weatherCode = when (val state = uiState) {
        is WeatherUiState.Success -> state.weather.current?.weather_code ?: 0
        else -> 0
    }
    val backgroundBrush = getWeatherBackgroundBrush(weatherCode)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        AtmosphericAmbientEffects(weatherCode = weatherCode)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (currentTab == "forecast" || currentTab == "radar") {
                // Search Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input"),
                        placeholder = {
                            Text(
                                text = "Search city...",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 15.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search icon",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear search",
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF34D399),
                            focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                            focusedBorderColor = Color(0xFF34D399).copy(alpha = 0.8f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = Color.White)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // GPS Button
                    IconButton(
                        onClick = {
                            if (locationPermissionState.status.isGranted) {
                                try {
                                    locationClient.lastLocation.addOnSuccessListener { location ->
                                        if (location != null) {
                                            viewModel.loadWeatherForCoordinates(
                                                location.latitude,
                                                location.longitude,
                                                "Current Location",
                                                null,
                                                location.hashCode().toLong()
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Awaiting GPS lock. Ensure location services are active.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                } catch (e: SecurityException) {
                                    Toast.makeText(context, "Location permission rejected.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                locationPermissionState.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("use_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = "Get geolocation forecast",
                            tint = Color(0xFF34D399)
                        )
                    }
                }

                // Suggestions dropdown overlay
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (searchUiState is SearchUiState.Loading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF34D399),
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.4.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Searching locations...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    } else if (searchUiState is SearchUiState.Success) {
                        val results = (searchUiState as SearchUiState.Success).results
                        if (results.isNotEmpty()) {
                            Popup(
                                alignment = Alignment.TopCenter,
                                onDismissRequest = { viewModel.clearSearch() }
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .heightIn(max = 280.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF0F172A)
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.15f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                                        items(results) { city ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.loadWeatherForCoordinates(
                                                            city.latitude,
                                                            city.longitude,
                                                            city.name,
                                                            city.country,
                                                            city.id
                                                        )
                                                        viewModel.clearSearch()
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.LocationOn,
                                                    contentDescription = null,
                                                    tint = Color(0xFF34D399),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = city.name,
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    val details = listOfNotNull(city.admin1, city.country).joinToString(", ")
                                                    if (details.isNotEmpty()) {
                                                        Text(
                                                            text = details,
                                                            color = Color.White.copy(alpha = 0.6f),
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                }
                                            }
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                        }
                                    }
                                }
                            }
                        } else if (searchQuery.length >= 3) {
                            Popup {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = "No cities found matching \"$searchQuery\"",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else if (searchUiState is SearchUiState.Error) {
                        val err = (searchUiState as SearchUiState.Error).message
                        Popup {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDE8E8)),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.2.dp, Color(0xFFF87171).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = err,
                                    color = Color(0xFF991B1B),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                // Favorites quick access bar
                if (favorites.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(favorites) { favorite ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.loadWeatherForCoordinates(
                                            favorite.latitude,
                                            favorite.longitude,
                                            favorite.name,
                                            favorite.country,
                                            favorite.id
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = favorite.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Weather view states
            Box(modifier = Modifier.weight(1f)) {
                if (currentTab == "radar") {
                    val lat = when (val state = uiState) {
                        is WeatherUiState.Success -> state.weather.latitude
                        else -> 22.396
                    }
                    val lon = when (val state = uiState) {
                        is WeatherUiState.Success -> state.weather.longitude
                        else -> 114.109
                    }
                    val overlayStyle by viewModel.radarOverlay.collectAsStateWithLifecycle()
                    WeatherRadarView(
                        latitude = lat,
                        longitude = lon,
                        overlayStyle = overlayStyle,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (currentTab == "settings") {
                    SettingsView(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (currentTab == "account") {
                    AccountView(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val useFahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
                    val windSpeedUnit by viewModel.windSpeedUnit.collectAsStateWithLifecycle()
                    when (val state = uiState) {
                        is WeatherUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF34D399), strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Analyzing weather maps...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is WeatherUiState.Success -> {
                            WeatherContent(
                                state = state,
                                useFahrenheit = useFahrenheit,
                                windSpeedUnit = windSpeedUnit,
                                airQualityState = airQualityState,
                                onToggleFavorite = { viewModel.toggleFavorite(state) }
                            )
                        }
                        is WeatherUiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                GlassCard(modifier = Modifier.padding(24.dp)) {
                                    Icon(
                                        imageVector = Icons.Rounded.SentimentVeryDissatisfied,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .align(Alignment.CenterHorizontally)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Sync failure",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.message,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                        is WeatherUiState.Idle -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Search a city or tap Geolocation to trace current weather forecast.",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Bar
            BottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Made by HASHER KHAN",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
fun WeatherContent(
    state: WeatherUiState.Success,
    useFahrenheit: Boolean,
    windSpeedUnit: String,
    airQualityState: AirQualityUiState,
    onToggleFavorite: () -> Unit
) {
    val current = state.weather.current
    val daily = state.weather.daily
    val hourly = state.weather.hourly

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Weather Dashboard
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.cityName,
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            if (state.country != null) {
                                Text(
                                    text = ", ${state.country}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Light,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = getFormattedTodayDate(),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Favorite Button
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("favorite_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (state.isFavorite) Color(0xFFFBBF24) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (current != null) {
                    val weatherCode = current.weather_code
                    val conditionStr = getWmoWeatherCondition(weatherCode)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Temp
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatTempNoUnit(current.temperature_2m, useFahrenheit),
                                color = Color.White,
                                fontSize = 68.sp,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = conditionStr,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (daily != null && daily.temperature_2m_max.isNotEmpty()) {
                                Text(
                                    text = "H: ${formatTempNoUnit(daily.temperature_2m_max[0], useFahrenheit)}   L: ${formatTempNoUnit(daily.temperature_2m_min[0], useFahrenheit)}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Weather Visual
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getWmoWeatherIcon(weatherCode),
                                contentDescription = "Weather condition icon",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
        }

        // Hourly Progression (Next 24 Hours)
        if (hourly != null && current != null) {
            item {
                HourlyForecastSection(
                    hourly = hourly,
                    currentTime = current.time,
                    useFahrenheit = useFahrenheit
                )
            }
        }

        // Metrics Grid (Humidity, Apparent Temp, Wind, UV Index)
        item {
            if (current != null) {
                val maxUv = if (daily != null && daily.uv_index_max.isNotEmpty()) {
                    daily.uv_index_max[0].toString()
                } else {
                    "N/A"
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(
                        icon = Icons.Rounded.Thermostat,
                        label = "Feels Like",
                        value = formatTemp(current.apparent_temperature, useFahrenheit),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MetricItem(
                        icon = Icons.Rounded.WaterDrop,
                        label = "Humidity",
                        value = "${current.relative_humidity_2m.toInt()}%",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(
                        icon = Icons.Rounded.Air,
                        label = "Wind Speed",
                        value = formatWindSpeed(current.wind_speed_10m, windSpeedUnit),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    UvIndexProgressGaugeCard(
                        uvString = maxUv,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Air Quality Index (AQI) Widget
        item {
            AirQualityWidget(
                state = airQualityState,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Forecast Card (5/7 Days)
        item {
            if (daily != null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "7-Day Forecast",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    daily.time.forEachIndexed { index, dateStr ->
                        ForecastRow(
                            dateStr = dateStr,
                            weatherCode = daily.weather_code[index],
                            maxTemp = daily.temperature_2m_max[index].toInt(),
                            minTemp = daily.temperature_2m_min[index].toInt(),
                            useFahrenheit = useFahrenheit
                        )
                        if (index < daily.time.size - 1) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(102.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label.uppercase(Locale.getDefault()),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun UvIndexProgressGaugeCard(
    uvString: String,
    modifier: Modifier = Modifier
) {
    val uvValue = uvString.toFloatOrNull() ?: 0f
    val (levelText, levelColor) = getUvInfo(uvValue)
    
    val progress = (uvValue / 12f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "UvProgress"
    )

    Card(
        modifier = modifier.height(102.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UV INDEX",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uvString,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(levelColor, shape = CircleShape)
                    )
                    Text(
                        text = levelText,
                        color = levelColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(56.dp)) {
                    val strokeWidth = 5.dp.toPx()
                    val sizeMin = size.minDimension
                    val offset = strokeWidth / 2f
                    val arcSize = sizeMin - strokeWidth
                    
                    drawArc(
                        color = Color.White.copy(alpha = 0.12f),
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(offset, offset),
                        size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
                    )

                    if (animatedProgress > 0.01f) {
                        drawArc(
                            color = levelColor.copy(alpha = 0.15f),
                            startAngle = 150f,
                            sweepAngle = 240f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth * 2.2f, cap = StrokeCap.Round),
                            topLeft = androidx.compose.ui.geometry.Offset(offset, offset),
                            size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
                        )

                        drawArc(
                            color = levelColor,
                            startAngle = 150f,
                            sweepAngle = 240f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = androidx.compose.ui.geometry.Offset(offset, offset),
                            size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
                        )

                        val angleRad = Math.toRadians((150f + 240f * animatedProgress).toDouble())
                        val r = arcSize / 2f
                        val cx = (size.width / 2f) + r * Math.cos(angleRad).toFloat()
                        val cy = (size.height / 2f) + r * Math.sin(angleRad).toFloat()
                        
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(cx, cy)
                        )
                        drawCircle(
                            color = levelColor,
                            radius = 2.5.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(cx, cy)
                        )
                    }
                }
                
                val percentage = (progress * 100).toInt()
                Text(
                    text = "${percentage}%",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

fun getUvInfo(uvVal: Float): Pair<String, Color> {
    return when {
        uvVal <= 2.9f -> Pair("Low", Color(0xFF34D399))
        uvVal <= 5.9f -> Pair("Moderate", Color(0xFFFBBF24))
        uvVal <= 7.9f -> Pair("High", Color(0xFFF97316))
        uvVal <= 10.9f -> Pair("Very High", Color(0xFFEF4444))
        else -> Pair("Extreme", Color(0xFFD946EF))
    }
}

@Composable
fun ForecastRow(
    dateStr: String,
    weatherCode: Int,
    maxTemp: Int,
    minTemp: Int,
    useFahrenheit: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatForecastDay(dateStr),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = getWmoWeatherIcon(weatherCode),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = getWmoWeatherCondition(weatherCode),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.width(80.dp)
        ) {
            Text(
                text = formatTempNoUnit(maxTemp.toDouble(), useFahrenheit),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatTempNoUnit(minTemp.toDouble(), useFahrenheit),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) {
        Color.Black.copy(alpha = 0.42f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                Color.Black.copy(alpha = 0.08f)
            )
        )
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.2.dp, borderBrush),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    )
}

fun formatHourlyTime(timeStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = inputFormat.parse(timeStr) ?: return timeStr
        val outputFormat = SimpleDateFormat("h a", Locale.getDefault())
        outputFormat.format(date)
    } catch (e: Exception) {
        timeStr.substringAfter("T")
    }
}

@Composable
fun HourlyForecastSection(
    hourly: com.example.data.model.HourlyWeather,
    currentTime: String,
    useFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    val prefix = if (currentTime.length >= 13) currentTime.substring(0, 13) else ""
    val startIndex = if (prefix.isNotEmpty()) {
        hourly.time.indexOfFirst { it.startsWith(prefix) }.let { if (it == -1) 0 else it }
    } else {
        0
    }
    val endIndex = minOf(startIndex + 24, hourly.time.size)

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Hourly Forecast (24h)",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
        ) {
            for (i in startIndex until endIndex) {
                val timeStr = hourly.time.getOrNull(i) ?: continue
                val tempVal = hourly.temperature_2m.getOrNull(i) ?: 0.0
                val code = hourly.weather_code?.getOrNull(i) ?: 0
                val isCurrentHour = i == startIndex

                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isCurrentHour) Color.White.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCurrentHour) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isCurrentHour) "Now" else formatHourlyTime(timeStr),
                            color = if (isCurrentHour) Color(0xFF34D399) else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Icon(
                            imageVector = getWmoWeatherIcon(code),
                            contentDescription = null,
                            tint = if (isCurrentHour) Color(0xFF34D399) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = formatTempNoUnit(tempVal, useFahrenheit),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

fun formatTempNoUnit(celsius: Double, useFahrenheit: Boolean): String {
    return if (useFahrenheit) {
        val f = (celsius * 9.0 / 5.0) + 32.0
        "${f.toInt()}°"
    } else {
        "${celsius.toInt()}°"
    }
}

fun formatTemp(celsius: Double, useFahrenheit: Boolean): String {
    return if (useFahrenheit) {
        val f = (celsius * 9.0 / 5.0) + 32.0
        "${f.toInt()}°F"
    } else {
        "${celsius.toInt()}°C"
    }
}

fun formatWindSpeed(speedKmh: Double, unit: String): String {
    return when (unit) {
        "mph" -> {
            val mph = speedKmh * 0.621371
            String.format(Locale.US, "%.1f mph", mph)
        }
        "m/s" -> {
            val ms = speedKmh / 3.6
            String.format(Locale.US, "%.1f m/s", ms)
        }
        else -> {
            String.format(Locale.US, "%.1f km/h", speedKmh)
        }
    }
}

fun getFormattedTodayDate(): String {
    val sdf = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    return sdf.format(Date())
}

fun formatForecastDay(dateStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(dateStr) ?: return dateStr
        val outputFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val calCurrent = Calendar.getInstance()
        val calTarget = Calendar.getInstance().apply { time = date }

        if (calCurrent.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)) {
            "Today"
        } else {
            outputFormat.format(date)
        }
    } catch (e: Exception) {
        dateStr
    }
}

@Composable
fun AtmosphericAmbientEffects(weatherCode: Int) {
    val isDark = isSystemInDarkTheme()
    var timeSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            timeSec = elapsed / 1000f
            androidx.compose.runtime.withFrameMillis { }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Core visual dynamic theme radial glow backdrops to maintain beautiful Material 3 depth
        if (isDark) {
            val purpleCenter = androidx.compose.ui.geometry.Offset(
                w * 0.2f + kotlin.math.sin(timeSec * 0.15f) * w * 0.12f,
                h * 0.35f + kotlin.math.cos(timeSec * 0.2f) * h * 0.08f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF818CF8).copy(alpha = 0.18f), Color.Transparent),
                    center = purpleCenter,
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = purpleCenter
            )

            val tealCenter = androidx.compose.ui.geometry.Offset(
                w * 0.82f + kotlin.math.cos(timeSec * 0.12f) * w * 0.15f,
                h * 0.68f + kotlin.math.sin(timeSec * 0.18f) * h * 0.09f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF34D399).copy(alpha = 0.14f), Color.Transparent),
                    center = tealCenter,
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = tealCenter
            )
        } else {
            val sunGlowCenter = androidx.compose.ui.geometry.Offset(
                w * 0.85f + kotlin.math.sin(timeSec * 0.08f) * w * 0.05f,
                h * 0.15f + kotlin.math.cos(timeSec * 0.1f) * h * 0.05f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFBBF24).copy(alpha = 0.22f), Color.Transparent),
                    center = sunGlowCenter,
                    radius = w * 0.72f
                ),
                radius = w * 0.72f,
                center = sunGlowCenter
            )

            val roseCenter = androidx.compose.ui.geometry.Offset(
                w * 0.15f + kotlin.math.cos(timeSec * 0.11f) * w * 0.06f,
                h * 0.65f + kotlin.math.sin(timeSec * 0.14f) * h * 0.1f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFF43F5E).copy(alpha = 0.12f), Color.Transparent),
                    center = roseCenter,
                    radius = w * 0.85f
                ),
                radius = w * 0.85f,
                center = roseCenter
            )
        }

        // 2. Overlay custom particulate weather animations based on active condition
        when (weatherCode) {
            // --- CLEAR SKY / SUNNY (WMO 0) ---
            0 -> {
                if (isDark) {
                    val random = java.util.Random(100)
                    for (i in 0 until 40) {
                        val sx = random.nextFloat() * w
                        val sy = random.nextFloat() * h
                        val phase = random.nextFloat() * 100f
                        val scale = 0.4f + 0.6f * kotlin.math.sin(timeSec * 2f + phase)
                        val sa = (0.2f + random.nextFloat() * 0.6f) * scale
                        drawCircle(
                            color = Color.White.copy(alpha = sa.coerceIn(0f, 1f)),
                            radius = 1.0f + random.nextFloat() * 1.5f,
                            center = androidx.compose.ui.geometry.Offset(sx, sy)
                        )
                    }
                } else {
                    val random = java.util.Random(100)
                    for (i in 0 until 12) {
                        val phase = random.nextFloat() * 100f
                        val speed = 25f + random.nextFloat() * 15f
                        val sx = random.nextFloat() * w
                        val sy = (h - (timeSec * speed + phase * h) % h) // floats up
                        val scale = 0.3f + 0.7f * kotlin.math.sin(timeSec * 0.8f + phase)
                        drawCircle(
                            color = Color(0xFFFBBF24).copy(alpha = (0.05f + random.nextFloat() * 0.12f) * scale),
                            radius = 4f + random.nextFloat() * 8f,
                            center = androidx.compose.ui.geometry.Offset(sx, sy)
                        )
                    }
                }
            }

            // --- PARTLY CLOUDY / OVERCAST (WMO 1, 2, 3) ---
            1, 2, 3 -> {
                val cloudCount = 3
                val random = java.util.Random(88)
                val cloudAlpha = if (isDark) 0.05f else 0.22f
                val cloudColor = Color.White.copy(alpha = cloudAlpha)
                
                for (i in 0 until cloudCount) {
                    val speed = 15f + random.nextFloat() * 20f
                    val cyPercent = 0.15f + i * 0.12f
                    // clouds wrap around horizontally
                    val cx = (timeSec * speed + i * (w / 3f)) % (w + 400f) - 200f
                    val cy = h * cyPercent + kotlin.math.sin(timeSec * 0.4f + i) * 25f
                    
                    val radius1 = 110.dp.toPx() + random.nextFloat() * 40.dp.toPx()
                    val radius2 = 80.dp.toPx() + random.nextFloat() * 30.dp.toPx()
                    
                    drawCircle(
                        color = cloudColor,
                        radius = radius1,
                        center = androidx.compose.ui.geometry.Offset(cx, cy)
                    )
                    drawCircle(
                        color = cloudColor,
                        radius = radius2,
                        center = androidx.compose.ui.geometry.Offset(cx + 80.dp.toPx(), cy + 15.dp.toPx())
                    )
                }
            }

            // --- FOG / FOGGY (WMO 45, 48) ---
            45, 48 -> {
                val mistColor = if (isDark) Color(0xFF94A3B8).copy(alpha = 0.10f) else Color(0xFFE2E8F0).copy(alpha = 0.35f)
                val waveHeight = 16.dp.toPx()
                val numLayers = 3
                for (layer in 0 until numLayers) {
                    val yOffset = h * (0.35f + layer * 0.18f)
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, yOffset)
                    var x = 0f
                    while (x < w + 30f) {
                        val y = yOffset + kotlin.math.sin((x / 130f) + (timeSec * 0.6f) + (layer * 3f)) * waveHeight
                        path.lineTo(x, y)
                        x += 30f
                    }
                    path.lineTo(w, h)
                    path.lineTo(0f, h)
                    path.close()
                    drawPath(path = path, color = mistColor)
                }
            }

            // --- DRIZZLE / RAIN / SHOWERS (WMO 51 to 65, 80 to 82) ---
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> {
                val random = java.util.Random(99)
                val rainColor = if (isDark) Color(0xFF818CF8).copy(alpha = 0.28f) else Color(0xFF3B82F6).copy(alpha = 0.38f)
                val rainCount = if (weatherCode in listOf(63, 65, 81, 82)) 60 else 35 // heavier rain for some codes
                
                for (i in 0 until rainCount) {
                    val phaseX = random.nextFloat() * w
                    val phaseY = random.nextFloat() * h
                    val speedY = 450.dp.toPx() + random.nextFloat() * 250.dp.toPx()
                    val speedX = -45.dp.toPx() // slanted winds blow leftwards

                    val y = (phaseY + timeSec * speedY) % h
                    val x = (phaseX + timeSec * speedX) % w

                    drawLine(
                        color = rainColor,
                        start = androidx.compose.ui.geometry.Offset(x, y),
                        end = androidx.compose.ui.geometry.Offset(x + speedX * 0.08f, y + speedY * 0.08f),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // --- SNOWFALL / ICE (WMO 71 to 77, 85 to 86) ---
            71, 73, 75, 77, 85, 86 -> {
                val random = java.util.Random(77)
                val snowColor = Color.White.copy(alpha = if (isDark) 0.58f else 0.78f)
                val snowCount = 42

                for (i in 0 until snowCount) {
                    val phaseX = random.nextFloat() * w
                    val phaseY = random.nextFloat() * h
                    val speedY = 60.dp.toPx() + random.nextFloat() * 50.dp.toPx()
                    val driftAmp = 16.dp.toPx() + random.nextFloat() * 16.dp.toPx()
                    val freq = 1.2f + random.nextFloat() * 1.8f

                    val y = (phaseY + timeSec * speedY) % h
                    val x = (phaseX + kotlin.math.sin(timeSec * freq + i) * driftAmp) % w

                    drawCircle(
                        color = snowColor,
                        radius = 2.2f.dp.toPx() + random.nextFloat() * 2.8f.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }

            // --- THUNDERSTORM (WMO 95, 96, 99) ---
            95, 96, 99 -> {
                // Heavy storm torrential rain first
                val random = java.util.Random(13)
                val rainColor = if (isDark) Color(0xFFC7D2FE).copy(alpha = 0.35f) else Color(0xFF3B82F6).copy(alpha = 0.45f)
                
                for (i in 0 until 70) {
                    val phaseX = random.nextFloat() * w
                    val phaseY = random.nextFloat() * h
                    val speedY = 620.dp.toPx() + random.nextFloat() * 220.dp.toPx()
                    val speedX = -85.dp.toPx() // high gale storms blow hard

                    val y = (phaseY + timeSec * speedY) % h
                    val x = (phaseX + timeSec * speedX) % w

                    drawLine(
                        color = rainColor,
                        start = androidx.compose.ui.geometry.Offset(x, y),
                        end = androidx.compose.ui.geometry.Offset(x + speedX * 0.08f, y + speedY * 0.08f),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Thunderstorm instant split-second electrical discharge flashes!
                val flashInterval = (timeSec * 0.22f) % 1.0f 
                val flashOpacity = if (flashInterval in 0.08f..0.12f) {
                    val progress = (flashInterval - 0.08f) / 0.04f
                    val sineFlash = kotlin.math.sin(progress * Math.PI.toFloat() * 2f)
                    (sineFlash * 0.22f).coerceIn(0f, 1f)
                } else if (flashInterval in 0.45f..0.48f) {
                    val progress = (flashInterval - 0.45f) / 0.03f
                    val sineFlash = kotlin.math.sin(progress * Math.PI.toFloat())
                    (sineFlash * 0.35f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                if (flashOpacity > 0.01f) {
                    drawRect(
                        color = Color(0xFFE0E7FF).copy(alpha = flashOpacity),
                        size = size
                    )
                }
            }

            // Default cosmic floating space stars (Base slate background)
            else -> {
                val random = java.util.Random(42)
                for (i in 0 until 40) {
                    val sx = random.nextFloat() * w
                    val sy = random.nextFloat() * h
                    val scale = 0.5f + 0.5f * kotlin.math.sin(timeSec * 0.5f + sx)
                    val sa = (0.1f + random.nextFloat() * 0.4f) * scale
                    drawCircle(
                        color = Color.White.copy(alpha = sa.coerceIn(0f, 1f)),
                        radius = 1.5f + random.nextFloat() * 2f,
                        center = androidx.compose.ui.geometry.Offset(sx, sy)
                    )
                }
            }
        }
    }
}

@Composable
fun getWeatherBackgroundBrush(weatherCode: Int): Brush {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        val startColor = when (weatherCode) {
            0 -> Color(0xFF0D0B21)         // Midnight Cosmic Clear deep slate
            1, 2, 3 -> Color(0xFF111E2E)   // Twilight Storm Slate
            45, 48 -> Color(0xFF1F2B3E)    // Foggy Velvet Charcoal
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> Color(0xFF22153B) // Nocturnal Indigo Aurora
            71, 73, 75, 77, 85, 86 -> Color(0xFF0F1E36) // Frosty Deep Blue
            95, 96, 99 -> Color(0xFF290B3B) // Cyber Thunder Deep Purple
            else -> Color(0xFF0F172A)
        }
        val endColor = when (weatherCode) {
            0 -> Color(0xFF020208)         // Absolute space depth
            1, 2, 3 -> Color(0xFF050912)   // Space charcoal black
            45, 48 -> Color(0xFF090F1A)    // Dark velvet grey
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> Color(0xFF03050C) // Absolute midnight blue
            71, 73, 75, 77, 85, 86 -> Color(0xFF050D1D) // Frosty black space
            95, 96, 99 -> Color(0xFF08020E) // Deep storm black
            else -> Color(0xFF020617)
        }
        Brush.verticalGradient(colors = listOf(startColor, endColor))
    } else {
        val startColor = when (weatherCode) {
            0 -> Color(0xFFFFECEF)         // Warm Sunset Clear sky peach blush
            1, 2, 3 -> Color(0xFFFFF9F2)   // Cozy apricot cloud
            45, 48 -> Color(0xFFF8FAFC)    // Soft pearl fog
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> Color(0xFFEEF2FF) // Lavender drizzle sky
            71, 73, 75, 77, 85, 86 -> Color(0xFFF0FDF4) // Minty crisp snowfall
            95, 96, 99 -> Color(0xFFFAF5FF) // Amethyst thunder sky
            else -> Color(0xFFF8FAFC)
        }
        val endColor = when (weatherCode) {
            0 -> Color(0xFFFCD34D)         // Radiant warm golden sunset
            1, 2, 3 -> Color(0xFFFED7AA)   // Pastel orange
            45, 48 -> Color(0xFFE2E8F0)    // Gentle gray slate mist
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> Color(0xFFC7D2FE) // Deep blue-lavender dawn
            71, 73, 75, 77, 85, 86 -> Color(0xFFCCFBF1) // Aquamarine frost breeze
            95, 96, 99 -> Color(0xFFE9D5FF) // Light storm lavender
            else -> Color(0xFFE2E8F0)
        }
        Brush.verticalGradient(colors = listOf(startColor, endColor))
    }
}

fun getWmoWeatherIcon(weatherCode: Int): ImageVector {
    return when (weatherCode) {
        0 -> Icons.Rounded.WbSunny
        1, 2, 3 -> Icons.Rounded.Cloud
        45, 48 -> Icons.Rounded.FilterDrama
        51, 53, 55 -> Icons.Rounded.WaterDrop
        61, 63, 65 -> Icons.Rounded.Umbrella
        66, 67 -> Icons.Rounded.AcUnit
        71, 73, 75, 77 -> Icons.Rounded.AcUnit
        80, 81, 82 -> Icons.Rounded.WaterDrop
        85, 86 -> Icons.Rounded.AcUnit
        95, 96, 99 -> Icons.Rounded.FlashOn
        else -> Icons.AutoMirrored.Rounded.HelpOutline
    }
}

fun getWmoWeatherCondition(code: Int): String {
    return when (code) {
        0 -> "Clear Sky"
        1 -> "Mainly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy Rime"
        51, 53, 55 -> "Drizzle Mist"
        56, 57 -> "Freezing Drizzle"
        61 -> "Light Rain"
        63 -> "Moderate Rain"
        65 -> "Heavy Downpours"
        66, 67 -> "Freezing Rain"
        71 -> "Light Snowfall"
        73 -> "Moderate Snow"
        75 -> "Blizzard snow"
        77 -> "Snow Grains"
        80 -> "Light Showers"
        81 -> "Moderate Showers"
        82 -> "Violent Showers"
        85 -> "Snow Showers"
        86 -> "Heavy Snow Showers"
        95 -> "Thunderstorms"
        96, 99 -> "Severe Hail Storm"
        else -> "Unspecified"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WeatherRadarView(
    latitude: Double,
    longitude: Double,
    overlayStyle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f))
    ) {
        val embedUrl = remember(latitude, longitude, overlayStyle) {
            "https://embed.windy.com/embed2.html?lat=$latitude&lon=$longitude&zoom=5&level=surface&overlay=$overlayStyle&menu=&message=&marker=&calendar=now&pressure=&type=map&location=coordinates&detail=&detailLat=$latitude&detailLon=$longitude&metricWind=default&metricTemp=default&radarRange=-1"
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    webViewClient = WebViewClient()
                    loadUrl(embedUrl)
                }
            },
            update = { webView ->
                if (webView.url != embedUrl) {
                    webView.loadUrl(embedUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CinematicExpeditionEarthView() {
    val globePainter = painterResource(id = com.example.R.drawable.img_cinematic_globe_texture)
    
    var timeSec by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(true) }
    
    val stars = remember {
        val random = java.util.Random(42)
        List(60) {
            StardustStar(
                x = random.nextFloat(),
                y = random.nextFloat(),
                baseAlpha = 0.3f + random.nextFloat() * 0.7f,
                scale = 1.2f + random.nextFloat() * 2.5f
            )
        }
    }
    
    val cities = remember {
        listOf(
            UrbanCity("New York", -0.45f, -0.15f),
            UrbanCity("London", -0.05f, -0.25f),
            UrbanCity("Cairo", 0.12f, -0.05f),
            UrbanCity("Dubai", 0.25f, -0.01f),
            UrbanCity("Mumbai", 0.38f, 0.08f),
            UrbanCity("Shanghai", 0.55f, -0.10f),
            UrbanCity("Tokyo", 0.62f, -0.12f),
            UrbanCity("Johannesburg", 0.15f, 0.38f),
            UrbanCity("Sydney", 0.72f, 0.45f),
            UrbanCity("Rio de Janeiro", -0.32f, 0.30f)
        )
    }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val startTime = System.currentTimeMillis() - (timeSec * 1000).toLong()
            while (isPlaying) {
                val elapsed = System.currentTimeMillis() - startTime
                timeSec = (elapsed / 1000f) % 15f
                androidx.compose.runtime.withFrameMillis { }
            }
        }
    }
    
    val activePhase = when {
        timeSec < 3f -> 1
        timeSec < 10f -> 2
        else -> 3
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF020205))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            val centerX = w / 2f
            val centerY = h / 2f
            val radius = size.minDimension * 0.32f
            
            stars.forEachIndexed { idx, star ->
                val pulse = (0.25f + 0.75f * kotlin.math.sin(timeSec * 3f + idx).plus(1f).div(2f)) * star.baseAlpha
                val starX = star.x * w
                val starY = star.y * h
                drawCircle(
                    color = Color.White.copy(alpha = pulse),
                    radius = star.scale,
                    center = androidx.compose.ui.geometry.Offset(starX, starY)
                )
            }
            
            var zoom = 1.3f
            var offsetX = 0f
            var offsetY = 0f
            var rotationAngle = 0f
            var neonOutlineAlpha = 0f
            
            when (activePhase) {
                1 -> {
                    val p1 = timeSec / 3f
                    val easeT = 1f - p1
                    zoom = 1.3f + 4.2f * easeT * easeT * easeT
                    offsetX = -220f * easeT * easeT
                    offsetY = 110f * easeT * easeT
                    neonOutlineAlpha = (1f - p1).coerceIn(0f, 1f)
                    rotationAngle = p1 * 12f
                }
                2 -> {
                    val p2 = (timeSec - 3f) / 7f
                    zoom = 1.3f
                    offsetX = 0f
                    offsetY = 0f
                    rotationAngle = 12f + p2 * 32f
                }
                3 -> {
                    val p3 = (timeSec - 10f) / 5f
                    zoom = lerp(1.3f, 1.15f, p3)
                    offsetX = 0f
                    offsetY = 0f
                    rotationAngle = 44f + p3 * 6f
                }
            }
            
            val spherePath = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius))
            }
            
            val edgeGlowBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF34D399).copy(alpha = 0.35f),
                    Color(0xFF60A5FA).copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                radius = radius * 1.18f
            )
            drawCircle(
                brush = edgeGlowBrush,
                radius = radius * 1.18f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
            
            clipPath(spherePath) {
                val imgWidth = radius * 2.4f * zoom
                val imgHeight = radius * 2.4f * zoom
                
                translate(
                    left = centerX - imgWidth / 2f + offsetX,
                    top = centerY - imgHeight / 2f + offsetY
                ) {
                    rotate(
                        degrees = rotationAngle,
                        pivot = androidx.compose.ui.geometry.Offset(imgWidth / 2f, imgHeight / 2f)
                    ) {
                        with(globePainter) {
                            draw(size = androidx.compose.ui.geometry.Size(imgWidth, imgHeight))
                        }
                    }
                }
                
                val sweepFraction = when {
                    activePhase == 1 -> 1.0f
                    activePhase == 3 -> 0.32f
                    else -> 1.0f - 0.68f * ((timeSec - 3f) / 7f)
                }
                
                val shadowBrush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    start = androidx.compose.ui.geometry.Offset(centerX - radius + radius * 2f * sweepFraction, centerY - radius),
                    end = androidx.compose.ui.geometry.Offset(centerX - radius + radius * 2f * sweepFraction + radius * 0.45f, centerY + radius)
                )
                drawCircle(
                    brush = shadowBrush,
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                )
                
                cities.forEach { city ->
                    val cityGlobalX = centerX + city.x * radius * zoom
                    val cityGlobalY = centerY + city.y * radius * zoom
                    
                    if (cityGlobalX > (centerX - radius + radius * 2f * sweepFraction)) {
                        val animReveal = if (activePhase == 1) 0f else if (activePhase == 3) 1f else ((timeSec - 3f) / 7f).coerceIn(0f, 1f)
                        if (animReveal > 0f) {
                            val pulse = animReveal * (0.5f + 0.5f * kotlin.math.sin(timeSec * 4f + city.name.hashCode()).plus(1f).div(2f))
                            drawCircle(
                                color = Color(0xFFFBBF24).copy(alpha = pulse * 0.9f),
                                radius = 2.5.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(cityGlobalX, cityGlobalY)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = pulse * 0.4f),
                                radius = 5.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(cityGlobalX, cityGlobalY)
                            )
                        }
                    }
                }
                
                if (activePhase == 1) {
                    val borderPath = Path().apply {
                        moveTo(centerX - radius * 0.45f + offsetX, centerY - radius * 0.15f + offsetY)
                        lineTo(centerX - radius * 0.15f + offsetX, centerY - radius * 0.35f + offsetY)
                        lineTo(centerX + radius * 0.15f + offsetX, centerY - radius * 0.20f + offsetY)
                        lineTo(centerX + radius * 0.05f + offsetX, centerY + radius * 0.15f + offsetY)
                        lineTo(centerX - radius * 0.25f + offsetX, centerY + radius * 0.25f + offsetY)
                        close()
                    }
                    drawPath(
                        path = borderPath,
                        color = Color(0xFF34D399).copy(alpha = neonOutlineAlpha),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = borderPath,
                        color = Color.White.copy(alpha = neonOutlineAlpha * 0.75f),
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                
                if (activePhase >= 2) {
                    val fadeVal = if (activePhase == 2) ((timeSec - 3f) / 7f).coerceIn(0f, 1f) else 1f
                    
                    val routePoints = listOf(
                        Pair(androidx.compose.ui.geometry.Offset(centerX - radius * 0.45f, centerY - radius * 0.15f), androidx.compose.ui.geometry.Offset(centerX - radius * 0.05f, centerY - radius * 0.25f)),
                        Pair(androidx.compose.ui.geometry.Offset(centerX - radius * 0.05f, centerY - radius * 0.25f), androidx.compose.ui.geometry.Offset(centerX + radius * 0.35f, centerY - radius * 0.08f)),
                        Pair(androidx.compose.ui.geometry.Offset(centerX + radius * 0.35f, centerY - radius * 0.08f), androidx.compose.ui.geometry.Offset(centerX + radius * 0.52f, centerY + radius * 0.32f)),
                        Pair(androidx.compose.ui.geometry.Offset(centerX - radius * 0.32f, centerY + radius * 0.15f), androidx.compose.ui.geometry.Offset(centerX + radius * 0.15f, centerY + radius * 0.35f))
                    )
                    
                    routePoints.forEachIndexed { routeIdx, pair ->
                        val start = pair.first
                        val end = pair.second
                        val controlX = (start.x + end.x) / 2f
                        val controlY = kotlin.math.min(start.y, end.y) - radius * 0.25f
                        
                        val arcPath = Path().apply {
                            moveTo(start.x, start.y)
                            quadraticTo(controlX, controlY, end.x, end.y)
                        }
                        
                        drawPath(
                            path = arcPath,
                            color = Color(0xFFFBBF24).copy(alpha = fadeVal * 0.32f),
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        
                        val t = (timeSec * 0.55f + routeIdx * 0.25f) % 1.0f
                        val pX = (1f - t) * (1f - t) * start.x + 2f * (1f - t) * t * controlX + t * t * end.x
                        val pY = (1f - t) * (1f - t) * start.y + 2f * (1f - t) * t * controlY + t * t * end.y
                        
                        drawCircle(
                            color = Color(0xFFFBBF24).copy(alpha = fadeVal),
                            radius = 4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(pX, pY)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = fadeVal * 0.8f),
                            radius = 2.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(pX, pY)
                        )
                    }
                }
                
                if (activePhase >= 2) {
                    val cloudAlpha = if (activePhase == 2) ((timeSec - 3f) / 7f).coerceIn(0f, 0.45f) else 0.45f
                    val driftOffset = timeSec * 6.5f
                    
                    drawOval(
                        color = Color.White.copy(alpha = cloudAlpha * 0.75f),
                        topLeft = androidx.compose.ui.geometry.Offset(centerX - radius * 0.8f + driftOffset % 120f, centerY - radius * 0.4f),
                        size = androidx.compose.ui.geometry.Size(radius * 0.6f, radius * 0.25f)
                    )
                    drawOval(
                        color = Color.White.copy(alpha = cloudAlpha * 0.85f),
                        topLeft = androidx.compose.ui.geometry.Offset(centerX - radius * 0.3f - driftOffset * 0.8f % 160f, centerY + radius * 0.3f),
                        size = androidx.compose.ui.geometry.Size(radius * 0.8f, radius * 0.3f)
                    )
                }
                
                val innerShadeBrush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                    radius = radius
                )
                drawCircle(
                    brush = innerShadeBrush,
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                )
            }
            
            if (timeSec >= 14.1f) {
                val fadeFraction = ((timeSec - 14.1f) / 0.9f).coerceIn(0f, 1f)
                drawRect(
                    color = Color.Black.copy(alpha = fadeFraction),
                    size = size
                )
            }
        }
        
        if (timeSec in 0.5f..4.0f) {
            val slamProgress = (timeSec - 0.5f) / 1.0f
            val slamScale = if (slamProgress < 0f) 5f else if (slamProgress > 1f) 1f else 1f + 4f * (1f - slamProgress) * (1f - slamProgress) * (1f - slamProgress)
            val slamAlpha = if (slamProgress < 0f) 0f else if (slamProgress > 1f) 1.0f - ((timeSec - 2.8f) / 1.2f).coerceIn(0f, 1f) else slamProgress
            
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = slamScale
                        scaleY = slamScale
                        alpha = slamAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "HK ORBITAL SATELLITE",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "HK ORBITAL SATELLITE",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp,
                        modifier = Modifier
                            .graphicsLayer {
                                rotationX = 180f
                                alpha = 0.22f
                                translationY = -4f
                            }
                    )
                }
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF34D399), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SATELLITE LOCK: ACTIVE [CHRONOS-B]",
                    color = Color(0xFF34D399),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ALTITUDE: 846.12 km\nORBIT SPEED: 7.58 km/s\nCAMERA: FOCUS-P15",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            val currentLoc = when (activePhase) {
                1 -> "CONTINENT FOCUS (PULL BACK VIEW)"
                2 -> "TRANSIT SWEEP (DAY/NIGHT Sweeper)"
                else -> "IDLE DESCENT (Majestic Angle)"
            }
            Text(
                text = "COORDS: $currentLoc",
                color = Color(0xFFFBBF24),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val description = when (activePhase) {
                1 -> "[0s-3s] DRAMATIC REVEAL: SATELLITE ENTRY & 3D PULL-BACK"
                2 -> "[3s-10s] SYNCHRONIZATION: NETWORK GLOW & TERM-SWEEP"
                else -> "[10s-15s] REST STAGE: MAJESTIC ORBIT & ATMO-HAZE"
            }
            
            Text(
                text = description,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "%05.2fs", timeSec),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                ) {
                    val progressFraction = timeSec / 15f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(Color(0xFF34D399), RoundedCornerShape(2.dp))
                    )
                }
                
                Text(
                    text = "15.00s",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { isPlaying = !isPlaying },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/pause",
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                FilledIconButton(
                    onClick = {
                        timeSec = 0f
                        isPlaying = true
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Restart simulation",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

data class StardustStar(val x: Float, val y: Float, val baseAlpha: Float, val scale: Float)
data class UrbanCity(val name: String, val x: Float, val y: Float)

@Composable
fun CosmicOrbitView(modifier: Modifier = Modifier) {
    var selectedOption by remember { mutableStateOf("satellite") } // "satellite" or "jetstream"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Option togglers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { selectedOption = "satellite" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedOption == "satellite") Color(0xFF34D399) else Color.White.copy(alpha = 0.08f),
                    contentColor = if (selectedOption == "satellite") Color.Black else Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Satellite Orbit 3D",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = { selectedOption = "jetstream" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedOption == "jetstream") Color(0xFF34D399) else Color.White.copy(alpha = 0.08f),
                    contentColor = if (selectedOption == "jetstream") Color.Black else Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Air,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Atmospheric Jet Stream",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f))
        ) {
            if (selectedOption == "satellite") {
                CinematicExpeditionEarthView()
            } else {
                AtmosphericJetStreamView()
            }
        }
    }
}

@Composable
fun AtmosphericJetStreamView() {
    var jetType by remember { mutableStateOf("polar") } // "polar" or "subtropical" or "cyclonic"
    var windSpeedTier by remember { mutableStateOf("gale") } // "breeze" (140 km/h), "gale" (260 km/h), "super" (390 km/h)
    var altitudeSelected by remember { mutableStateOf("tropopause") } // "troposphere" (9km), "tropopause" (12km), "stratosphere" (15km)
    
    var timeSec by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(true) }
    
    val speedMultiplier = when (windSpeedTier) {
        "breeze" -> 0.5f
        "super" -> 2.2f
        else -> 1.0f
    }
    
    val baseWindSpeedValue = when (windSpeedTier) {
        "breeze" -> 138
        "super" -> 392
        else -> 254
    }
    
    // Smooth frame animator
    LaunchedEffect(isPlaying, speedMultiplier) {
        if (isPlaying) {
            val startTime = System.currentTimeMillis() - (timeSec * 1000).toLong()
            while (isPlaying) {
                val elapsed = System.currentTimeMillis() - startTime
                timeSec = (elapsed / 1000f) * speedMultiplier
                androidx.compose.runtime.withFrameMillis { }
            }
        }
    }
    
    // Draw particles
    val particleCount = 75
    val particles = remember(jetType) {
        val random = java.util.Random(1337L + jetType.hashCode())
        List(particleCount) {
            val offsetRatio = random.nextFloat()
            val speedFactor = 0.8f + random.nextFloat() * 0.4f
            val amplitudeFactor = 0.7f + random.nextFloat() * 0.6f
            val yJitter = -30f + random.nextFloat() * 60f
            Triple(offsetRatio, speedFactor, Pair(amplitudeFactor, yJitter))
        }
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF03010C))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h * 0.48f
            
            // Background stardust
            val random = java.util.Random(100)
            for (i in 0 until 40) {
                val sx = random.nextFloat() * w
                val sy = random.nextFloat() * h
                val sa = 0.2f + random.nextFloat() * 0.5f
                drawCircle(
                    color = Color.White.copy(alpha = sa),
                    radius = 2f,
                    center = androidx.compose.ui.geometry.Offset(sx, sy)
                )
            }
            
            // Draw flowing background latitude grid lines
            for (latOffset in listOf(-0.25f, -0.12f, 0f, 0.12f, 0.25f)) {
                val gridY = h * (0.48f + latOffset)
                val gridPath = Path().apply {
                    moveTo(0f, gridY)
                    // curve path
                    quadraticTo(w * 0.5f, gridY + h * 0.04f, w, gridY)
                }
                drawPath(
                    path = gridPath,
                    color = Color.White.copy(alpha = 0.05f),
                    style = Stroke(width = 2f)
                )
            }
            
            // Draw core Jet Stream Path
            val corePath = Path()
            val ptCount = 40
            val colorScheme = when (jetType) {
                "polar" -> listOf(Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFF38BDF8)) // cold, teal, blue
                "subtropical" -> listOf(Color(0xFFF87171), Color(0xFFFBBF24), Color(0xFFF43F5E)) // hot, orange, gold
                else -> listOf(Color(0xFFA78BFA), Color(0xFFEC4899), Color(0xFF818CF8)) // magenta, purple, indigo
            }
            
            val amplitude = when (jetType) {
                "polar" -> h * 0.14f
                "subtropical" -> h * 0.06f
                else -> h * 0.18f // atmospheric vortex
            }
            
            val loops = when (jetType) {
                "polar" -> 3f
                "subtropical" -> 1.5f
                else -> 4f
            }
            
            // Build main wave path
            for (i in 0..ptCount) {
                val fraction = i.toFloat() / ptCount
                val px = fraction * w
                
                val waveFactor = kotlin.math.sin(fraction * loops * 2f * kotlin.math.PI.toFloat() + timeSec * 0.4f)
                val py = centerY + waveFactor * amplitude
                
                if (i == 0) {
                    corePath.moveTo(px, py)
                } else {
                    corePath.lineTo(px, py)
                }
            }
            
            // Highlight halo glow
            drawPath(
                path = corePath,
                brush = Brush.horizontalGradient(colorScheme),
                style = Stroke(width = 24f, cap = StrokeCap.Round),
                alpha = 0.18f
            )
            
            // Core jet stream vector line
            drawPath(
                path = corePath,
                brush = Brush.horizontalGradient(colorScheme),
                style = Stroke(width = 8f, cap = StrokeCap.Round),
                alpha = 0.75f
            )
            
            // Core highlight line (inner white stream core)
            drawPath(
                path = corePath,
                color = Color.White.copy(alpha = 0.45f),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
            
            // Draw flowing wind vector particles along the wave path
            particles.forEach { part ->
                val baseFraction = part.first
                val speedFactor = part.second
                val ampFactor = part.third.first
                val yJitter = part.third.second
                
                // Advance fraction over time
                val currentFraction = (baseFraction + timeSec * 0.12f * speedFactor) % 1.0f
                val px = currentFraction * w
                val waveFactor = kotlin.math.sin(currentFraction * loops * 2f * kotlin.math.PI.toFloat() + timeSec * 0.4f)
                val py = centerY + waveFactor * amplitude * ampFactor + yJitter * (altitudeSelected.hashCode() % 1.0f).coerceIn(0.5f, 1.2f)
                
                // Color fade out towards boundaries
                val fade = if (currentFraction < 0.1f) currentFraction / 0.1f else if (currentFraction > 0.9f) (1f - currentFraction) / 0.1f else 1.0f
                val sizeVal = 4f + (speedFactor * 3f)
                
                drawCircle(
                    color = colorScheme[(baseFraction * colorScheme.size).toInt() % colorScheme.size].copy(alpha = fade * 0.82f),
                    radius = sizeVal,
                    center = androidx.compose.ui.geometry.Offset(px, py)
                )
                // Sparkle glow core
                if (speedFactor > 1.1f) {
                    drawCircle(
                        color = Color.White.copy(alpha = fade * 0.95f),
                        radius = sizeVal * 0.4f,
                        center = androidx.compose.ui.geometry.Offset(px, py)
                    )
                }
            }
            
            // Atmospheric altitude shading at top & bottom edge
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.16f
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                    startY = h * 0.72f,
                    endY = h
                )
            )
        }
        
        // telemetry overlays
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF34D399), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "JET STREAM TRACER: ACTIVE [SWIFT-2]",
                    color = Color(0xFF34D399),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val altitudeIndex = when (altitudeSelected) {
                "troposphere" -> "9,150 m (Sub-Jet Front)"
                "stratosphere" -> "14,800 m (Stratospheric Vortex)"
                else -> "11,500 m (Tropopause Peak Core)"
            }
            Text(
                text = "ALTITUDE: $altitudeIndex\nAZIMUTH REGIME: ONDULATING FLOW\nWAVE COUPLING: STRATOS-B",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            val flowDesc = when (jetType) {
                "polar" -> "POLAR FRONT JET (Cold Air boundary, Waviness 3)"
                "subtropical" -> "SUBTROPICAL JET CORE (Fast thermal wave block)"
                else -> "STORM VORTEX JET (Low barometric cyclonic drift)"
            }
            Text(
                text = "STREAM: $flowDesc",
                color = Color(0xFFFBBF24),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Controls / Options overlay card docked at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Stats readout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VELOCITY STREAM: $baseWindSpeedValue km/h",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "TURB INDEX: CAT-L${if (jetType == "polar") "3 HIGH" else "1 LOW"}",
                    color = if (jetType == "polar") Color(0xFFFBBF24) else Color(0xFF34D399),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Selector row 1: Jet Core type
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("polar", "Polar Jet", Color(0xFF60A5FA)),
                    Triple("subtropical", "Subtropical Jet", Color(0xFFF87171)),
                    Triple("storm", "Storm Vortex", Color(0xFFA78BFA))
                ).forEach { (type, label, col) ->
                    val isSel = jetType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) col.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                            .border(BorderStroke(1.dp, if (isSel) col else Color.White.copy(alpha = 0.08f)), RoundedCornerShape(10.dp))
                            .clickable { jetType = type }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Selector row 2: Altitude setting
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair("troposphere", "9,000 m"),
                    Pair("tropopause", "12,000 m (Core)"),
                    Pair("stratosphere", "15,000 m")
                ).forEach { (alt, label) ->
                    val isSel = altitudeSelected == alt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Color(0xFF34D399).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                            .border(BorderStroke(1.dp, if (isSel) Color(0xFF34D399) else Color.White.copy(alpha = 0.08f)), RoundedCornerShape(10.dp))
                            .clickable { altitudeSelected = alt }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Speed adjustments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { isPlaying = !isPlaying },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    FilledIconButton(
                        onClick = { timeSec = 0f },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Pair("breeze", "0.5x Speed"),
                        Pair("gale", "1.0x Core"),
                        Pair("super", "2.2x Cosmic")
                    ).forEach { (tier, label) ->
                        val isSel = windSpeedTier == tier
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFFFBBF24).copy(alpha = 0.2f) else Color.Transparent)
                                .border(BorderStroke(1.dp, if (isSel) Color(0xFFFBBF24) else Color.Transparent), RoundedCornerShape(8.dp))
                                .clickable { windSpeedTier = tier }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val useFahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
    val windSpeedUnit by viewModel.windSpeedUnit.collectAsStateWithLifecycle()
    val radarOverlayStyle by viewModel.radarOverlay.collectAsStateWithLifecycle()
    val enableNotifications by viewModel.enableNotifications.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Preferences",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Theme Selection
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "App Display Theme",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize the visual appearance (saves automatically)",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf(
                            Triple("dark", "Cosmic Dark", Icons.Rounded.NightsStay),
                            Triple("light", "Aura Light", Icons.Rounded.WbSunny),
                            Triple("system", "System", Icons.Rounded.Settings)
                        )
                        themes.forEach { (themeCode, label, icon) ->
                            val isSelected = themePreference == themeCode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.08f))
                                    .border(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.1f)
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { viewModel.setThemePreference(themeCode) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Temperature Unit Selection
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temperature Scale",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Current: ${if (useFahrenheit) "Fahrenheit (°F)" else "Celsius (°C)"}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "°C",
                            color = if (!useFahrenheit) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = useFahrenheit,
                            onCheckedChange = { viewModel.toggleTempUnit() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF34D399),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                        Text(
                            text = "°F",
                            color = if (useFahrenheit) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Wind Speed Unit Selection
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Wind Speed Unit",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Format velocity values for wind metric cards",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val units = listOf("km/h", "mph", "m/s")
                        units.forEach { unit ->
                            val isSelected = windSpeedUnit == unit
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.08f))
                                    .clickable { viewModel.setWindSpeedUnit(unit) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unit,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Radar Overlay Selection
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Map Satellite & Radar Layer",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose default layer for Windy's interactive map",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val overlays = listOf("radar", "wind", "temp")
                        overlays.forEach { overlay ->
                            val isSelected = radarOverlayStyle == overlay
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.08f))
                                    .clickable { viewModel.setRadarOverlayStyle(overlay) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when(overlay) {
                                        "radar" -> "Map Radar"
                                        "wind" -> "Wind Gusts"
                                        else -> "Temperature"
                                    },
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Alerts Notifications
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Extreme Meteorological Alerts",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Receive notifications on local typhoons / rainstorms",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = enableNotifications,
                        onCheckedChange = { viewModel.toggleNotifications() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFF34D399),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }

        // destructive actions
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDialog = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF991B1B).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear All Favorite Locations",
                        color = Color(0xFFF87171),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Reset My Favorites?", color = Color.White) },
            text = { Text("This will permanently clear all favorited coordinates and starting cities from local database storage.", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllFavorites()
                        showDialog = false
                        Toast.makeText(context, "Favorites database cleared.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear All", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun AccountView(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()

    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var syncInProgress by remember { mutableStateOf(false) }
    var syncCompleted by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Meteorology Cloud",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (!isLoggedIn) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Backup & Live Synchronization",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Register or sign in to save your favorite cities in real-time Cloud storage and access specialized meteorology charts.",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sign In / Register",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Name field
                        Column {
                            Text(
                                text = "Display Name",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            val containerColor = Color.Black.copy(alpha = 0.25f)
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                placeholder = { Text("E.g. John Doe", color = Color.White.copy(alpha = 0.4f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = containerColor,
                                    unfocusedContainerColor = containerColor,
                                    focusedBorderColor = Color(0xFF34D399),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                )
                            )
                        }

                        // Email field
                        Column {
                            Text(
                                text = "Email Address",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            val containerColor = Color.Black.copy(alpha = 0.25f)
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                placeholder = { Text("E.g. john@example.com", color = Color.White.copy(alpha = 0.4f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_email_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = containerColor,
                                    unfocusedContainerColor = containerColor,
                                    focusedBorderColor = Color(0xFF34D399),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (nameInput.trim().isEmpty() || emailInput.trim().isEmpty()) {
                                    Toast.makeText(context, "Please fill in all details.", Toast.LENGTH_SHORT).show()
                                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput.trim()).matches()) {
                                    Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.signInUser(emailInput.trim(), nameInput.trim())
                                    Toast.makeText(context, "Welcome ${nameInput.trim()}!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auth_submit_button")
                        ) {
                            Text(
                                text = "Create Account / Login",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Profile display when logged in
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399)),
                            contentAlignment = Alignment.Center
                        ) {
                            val initials = (userDisplayName ?: "U").take(2).uppercase()
                            Text(
                                text = initials,
                                color = Color.Black,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userDisplayName ?: "Meteorologist",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userEmail ?: "",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFBBF24).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Pro Tier",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Sync Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Synchronized Operations Enabled",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Your HK Weather pro subscription is active. All favorite locations will be backed up directly to real-time sync nodes.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (syncInProgress) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    color = Color(0xFF34D399),
                                    trackColor = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Uploading favorites matrix...",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        } else if (syncCompleted) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cloud Sync complete",
                                    color = Color(0xFF34D399),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    syncInProgress = true
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        syncInProgress = false
                                        syncCompleted = true
                                    }, 2000)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sync Database Backup Now",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Sign out
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.signOutUser()
                            syncCompleted = false
                            Toast.makeText(context, "Signed out.", Toast.LENGTH_SHORT).show()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExitToApp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Out from Cloud Account",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Forecast Tab
            val homeSelected = currentTab == "forecast"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTabSelected("forecast") }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = "Forecast tab",
                    tint = if (homeSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Forecast",
                    color = if (homeSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = if (homeSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            // Radar Tab
            val radarSelected = currentTab == "radar"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTabSelected("radar") }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = "World weather radar map",
                    tint = if (radarSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "World Map",
                    color = if (radarSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = if (radarSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            // Settings Tab
            val settingsSelected = currentTab == "settings"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTabSelected("settings") }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings tab",
                    tint = if (settingsSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Settings",
                    color = if (settingsSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = if (settingsSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            // Account Tab
            val accountSelected = currentTab == "account"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTabSelected("account") }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = "Account tab",
                    tint = if (accountSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Account",
                    color = if (accountSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = if (accountSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AirQualityWidget(
    state: AirQualityUiState,
    modifier: Modifier = Modifier
) {
    var showApiKeyInfoDialog by remember { mutableStateOf(false) }

    if (showApiKeyInfoDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyInfoDialog = false },
            title = {
                Text(
                    text = "API Key Configuration",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This widget fetches real-time localized particulate matter (PM2.5, PM10) and chemical levels from OpenWeatherMap.\n\nCurrently, it's running in Demo/Fallback Mode. To connect live telemetry for any global coordinate:\n\n1. Get a free API AppID from openweathermap.org\n2. Open the Secrets panel in AI Studio\n3. Set a secret named 'OPENWEATHER_API_KEY' with your key.",
                    color = Color.White.copy(alpha = 0.82f),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showApiKeyInfoDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF34D399))
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
        )
    }

    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.55f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.45f)
    val labelColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.6f)
    val textColor = if (isDark) Color.White else Color.Black

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.2.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Air,
                        contentDescription = "Air Quality",
                        tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AIR QUALITY INDEX",
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                when (state) {
                    is AirQualityUiState.Success -> {
                        if (state.isFallback) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                    .clickable { showApiKeyInfoDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Demo Fallback",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = "Info",
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF34D399).copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Live OWM Data",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                is AirQualityUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            color = Color(0xFF34D399),
                            trackColor = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Analyzing particulate levels...",
                            color = labelColor,
                            fontSize = 12.sp
                        )
                    }
                }
                is AirQualityUiState.Error -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Could not fetch air quality metrics: ${state.message}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showApiKeyInfoDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("API Setup Guidance", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                is AirQualityUiState.Success -> {
                    val aqiItem = state.airQuality.list?.firstOrNull()
                    val aqiVal = aqiItem?.main?.aqi ?: 1
                    
                    val (aqiName, aqiColor, description) = getAqiLevelDetails(aqiVal)

                    // Large AQI Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "AQI $aqiVal - $aqiName",
                                color = aqiColor,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = description,
                                color = textColor.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth(0.95f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 5-segment indicator bar
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            for (i in 1..5) {
                                val segmentColor = when (i) {
                                    1 -> Color(0xFF34D399) // Good
                                    2 -> Color(0xFFFBBF24) // Fair
                                    3 -> Color(0xFFF97316) // Moderate
                                    4 -> Color(0xFFEF4444) // Poor
                                    else -> Color(0xFFD946EF) // Very Poor
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(segmentColor)
                                )
                                if (i < 5) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                            }
                        }
                    }

                    // Segment indicator dots tracking the specific current AQI position
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (i in 1..5) {
                            Box(
                                modifier = Modifier
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (i == aqiVal) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDropUp,
                                            contentDescription = null,
                                            tint = aqiColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Detailed Pollutant Species Grid
                    val components = aqiItem?.components
                    if (components != null) {
                        HorizontalDivider(color = cardBorder.copy(alpha = 0.3f), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "POLLUTANT METRICS",
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val species = listOf(
                            Triple("PM2.5", components.pm2_5, "Fine Matter"),
                            Triple("PM10", components.pm10, "Coarse PM"),
                            Triple("O₃", components.o3, "Ozone"),
                            Triple("NO₂", components.no2, "Nitrogen Dioxide"),
                            Triple("CO", components.co, "Carbon Monoxide"),
                            Triple("SO₂", components.so2, "Sulfur Dioxide")
                        )

                        // 2 row x 3 col layout
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in 0 until 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (col in 0 until 3) {
                                        val idx = row * 3 + col
                                        if (idx < species.size) {
                                            val (name, value, labelStr) = species[idx]
                                            PollutantItem(
                                                name = name,
                                                valDouble = value,
                                                descriptor = labelStr,
                                                modifier = Modifier.weight(1f),
                                                textColor = textColor,
                                                labelColor = labelColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun PollutantItem(
    name: String,
    valDouble: Double?,
    descriptor: String,
    textColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val displayVal = if (valDouble != null) {
        if (valDouble >= 100.0) {
            valDouble.toInt().toString()
        } else {
            String.format("%.1f", valDouble)
        }
    } else {
        "N/A"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = name,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$displayVal \u03bcg/m\u00b3",
                color = textColor.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = descriptor,
                color = labelColor,
                fontSize = 9.sp
            )
        }
    }
}

fun getAqiLevelDetails(aqi: Int): Triple<String, Color, String> {
    return when (aqi) {
        1 -> Triple(
            "Good", 
            Color(0xFF34D399), 
            "Air quality is satisfactory, and air pollution poses little or no risk."
        )
        2 -> Triple(
            "Fair", 
            Color(0xFFFBBF24), 
            "Air quality is acceptable; however, there may be risk for highly sensitive people."
        )
        3 -> Triple(
            "Moderate", 
            Color(0xFFF97316), 
            "Members of sensitive groups may experience health effects. General public is less likely impacted."
        )
        4 -> Triple(
            "Poor", 
            Color(0xFFEF4444), 
            "Everyone may begin to experience health effects; sensitive groups may experience more serious. Consider limiting active outside time."
        )
        else -> Triple(
            "Very Poor", 
            Color(0xFFD946EF), 
            "Health alert: everyone may experience more serious health impacts. Run air purifiers indoor where possible."
        )
    }
}
