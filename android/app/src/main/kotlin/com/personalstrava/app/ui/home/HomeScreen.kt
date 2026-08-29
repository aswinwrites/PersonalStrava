package com.personalstrava.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.weather.WeatherCodes
import com.personalstrava.app.weather.WeatherRepository
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The primary screen (spec section 10/38). Today's steps up top, then the
 * two record buttons with nothing else competing for tap priority, then
 * recent activity. Starting a ride from here is a single tap.
 */
@Composable
fun HomeScreen(
    onStartWalking: () -> Unit = {},
    onStartJogging: () -> Unit = {},
    onStartCycling: () -> Unit = {},
    onStartMotorcycling: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenActivity: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var hasLocationForWeather by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val weatherLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocationForWeather = granted
        if (granted) viewModel.loadWeather()
    }

    // Weather is genuinely optional context, not something worth its own permission prompt on
    // first launch — this quietly uses location if it's already granted (from the recording
    // flow), and otherwise shows a small opt-in prompt rather than interrupting Home.
    LaunchedEffect(hasLocationForWeather) {
        if (hasLocationForWeather && state.weather == null) viewModel.loadWeather()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = greeting(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                IconButton(onClick = onOpenProfile, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Person, contentDescription = "Profile")
                }
            }
        }
        item {
            Text(
                text = state.todaySteps?.toString() ?: "—",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "STEPS TODAY", fontSize = 12.sp)
        }
        item {
            Text(text = state.quote, fontSize = 13.sp, fontStyle = FontStyle.Italic)
        }
        item {
            if (hasLocationForWeather) {
                if (state.weather != null) WeatherStrip(state.weather!!)
            } else {
                Text(
                    text = "Enable location to see the forecast before you head out",
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { weatherLocationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                )
            }
        }
        item {
            Button(
                onClick = onStartWalking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("\uD83D\uDEB6 START WALKING")
            }
        }
        item {
            Button(
                onClick = onStartJogging,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("\uD83C\uDFC3 START JOGGING")
            }
        }
        item {
            Button(
                onClick = onStartCycling,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("\uD83D\uDEB4 START CYCLING")
            }
        }
        item {
            Button(
                onClick = onStartMotorcycling,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\uD83C\uDFCD\uFE0F START MOTORCYCLE")
            }
        }
        item {
            Text(text = "Recent activities", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        }
        if (state.recentActivities.isEmpty()) {
            item { Text("Nothing recorded yet — start a ride above.", fontSize = 13.sp) }
        } else {
            items(state.recentActivities) { activity ->
                RecentActivityRow(activity, onClick = { onOpenActivity(activity.id) })
            }
        }
    }
}

@Composable
private fun WeatherStrip(forecast: List<WeatherRepository.DayForecast>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(forecast) { day -> WeatherDayCard(day) }
    }
}

@Composable
private fun WeatherDayCard(day: WeatherRepository.DayForecast) {
    val (emoji, label) = WeatherCodes.describe(day.code)
    val isToday = day.date == java.time.LocalDate.now()
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isToday) "TODAY" else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = "${day.highC}°", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(text = "${day.lowC}°", fontSize = 11.sp)
        Text(text = label, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun RecentActivityRow(activity: ActivityEntity, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(text = activity.title ?: activity.activityType, fontWeight = FontWeight.Medium)
        Text(text = "${"%.1f".format(activity.distanceMeters / 1000)} km", fontSize = 12.sp)
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 0..11 -> "GOOD MORNING"
    in 12..17 -> "GOOD AFTERNOON"
    else -> "GOOD EVENING"
}
