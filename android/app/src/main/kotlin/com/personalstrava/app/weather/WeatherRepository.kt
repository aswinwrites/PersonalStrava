package com.personalstrava.app.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * A 4-day forecast (today + D+3, per the Home screen ask) from Open-Meteo — no API key needed,
 * generous free tier, good enough for "should I bring a jacket before I start a ride" rather than
 * anything the app treats as authoritative. Location comes from the device's last known fix
 * (FusedLocationProviderClient), reusing the coarse/fine location permission the recording flow
 * already asks for elsewhere — this never requests it itself, only reads whatever's already
 * granted (see HomeScreen for the permission prompt this depends on).
 */
class WeatherRepository(private val context: Context) {

    data class DayForecast(val date: LocalDate, val highC: Int, val lowC: Int, val code: Int)

    suspend fun fetchForecast(): List<DayForecast>? = withContext(Dispatchers.IO) {
        val location = lastKnownLocation() ?: return@withContext null
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}&longitude=${location.longitude}" +
            "&daily=weathercode,temperature_2m_max,temperature_2m_min" +
            "&forecast_days=4&timezone=auto"

        val body = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                inputStream.bufferedReader().use { it.readText() }
            }
        }.getOrNull() ?: return@withContext null

        runCatching { parseForecast(body) }.getOrNull()
    }

    private fun parseForecast(body: String): List<DayForecast> {
        val daily = JSONObject(body).getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weathercode")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        return (0 until times.length()).map { i ->
            DayForecast(
                date = LocalDate.parse(times.getString(i)),
                highC = highs.getDouble(i).roundToInt(),
                lowC = lows.getDouble(i).roundToInt(),
                code = codes.getInt(i),
            )
        }
    }

    @Suppress("MissingPermission")
    private suspend fun lastKnownLocation(): Location? {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        return suspendCancellableCoroutine { cont ->
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { location -> if (cont.isActive) cont.resume(location) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }
}
