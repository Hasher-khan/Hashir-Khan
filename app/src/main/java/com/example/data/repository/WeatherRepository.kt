package com.example.data.repository

import com.example.data.api.WeatherApiService
import com.example.data.database.FavoriteCityDao
import com.example.data.model.FavoriteCity
import com.example.data.model.GeocodingResponse
import com.example.data.model.WeatherResponse
import kotlinx.coroutines.flow.Flow

class WeatherRepository(
    private val favoriteCityDao: FavoriteCityDao,
    private val apiService: WeatherApiService
) {
    val favorites: Flow<List<FavoriteCity>> = favoriteCityDao.getAllFavorites()

    suspend fun searchCity(name: String): GeocodingResponse {
        return apiService.searchCity(
            url = "https://geocoding-api.open-meteo.com/v1/search",
            name = name
        )
    }

    suspend fun getForecast(latitude: Double, longitude: Double): WeatherResponse {
        return apiService.getForecast(
            url = "https://api.open-meteo.com/v1/forecast",
            latitude = latitude,
            longitude = longitude
        )
    }

    suspend fun getAirPollution(latitude: Double, longitude: Double, apiKey: String): com.example.data.model.AirQualityResponse {
        return apiService.getAirPollution(
            url = "https://api.openweathermap.org/data/2.5/air_pollution",
            latitude = latitude,
            longitude = longitude,
            apiKey = apiKey
        )
    }

    suspend fun addFavorite(city: FavoriteCity) {
        favoriteCityDao.insertFavorite(city)
    }

    suspend fun removeFavorite(city: FavoriteCity) {
        favoriteCityDao.deleteFavorite(city)
    }

    suspend fun isFavorite(id: Long): Boolean {
        return favoriteCityDao.isFavorite(id)
    }
}
