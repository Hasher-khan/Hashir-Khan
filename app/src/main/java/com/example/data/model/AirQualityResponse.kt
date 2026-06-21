package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AirQualityResponse(
    val list: List<AirQualityItem>?
)

@JsonClass(generateAdapter = true)
data class AirQualityItem(
    val main: AirQualityMain,
    val components: AirQualityComponents?,
    val dt: Long?
)

@JsonClass(generateAdapter = true)
data class AirQualityMain(
    val aqi: Int
)

@JsonClass(generateAdapter = true)
data class AirQualityComponents(
    val co: Double? = null,
    val no: Double? = null,
    val no2: Double? = null,
    val o3: Double? = null,
    val so2: Double? = null,
    val pm2_5: Double? = null,
    val pm10: Double? = null,
    val nh3: Double? = null
)
