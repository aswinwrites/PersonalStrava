package com.personalstrava.app.weather

/** Maps Open-Meteo's WMO weather codes (https://open-meteo.com/en/docs, "weathercode") to a
 *  compact emoji + label — matches the emoji-forward style the record buttons already use, and
 *  needs no icon asset pack for something this small. */
object WeatherCodes {
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to "Clear"
        1 -> "🌤️" to "Mostly clear"
        2 -> "⛅" to "Partly cloudy"
        3 -> "☁️" to "Overcast"
        45, 48 -> "🌫️" to "Fog"
        51, 53, 55 -> "🌦️" to "Drizzle"
        56, 57 -> "🌧️" to "Freezing drizzle"
        61, 63, 65 -> "🌧️" to "Rain"
        66, 67 -> "🌧️" to "Freezing rain"
        71, 73, 75, 77 -> "❄️" to "Snow"
        80, 81, 82 -> "🌦️" to "Showers"
        85, 86 -> "🌨️" to "Snow showers"
        95 -> "⛈️" to "Storm"
        96, 99 -> "⛈️" to "Storm + hail"
        else -> "🌡️" to "—"
    }
}
