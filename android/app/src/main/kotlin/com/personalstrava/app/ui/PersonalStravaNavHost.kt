package com.personalstrava.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personalstrava.app.domain.model.ActivityType
import com.personalstrava.app.ui.auth.AuthUiState
import com.personalstrava.app.ui.auth.AuthViewModel
import com.personalstrava.app.ui.auth.SignInScreen
import com.personalstrava.app.ui.home.HomeScreen
import com.personalstrava.app.ui.profile.ProfileScreen
import com.personalstrava.app.ui.record.RecordingScreen
import com.personalstrava.app.ui.summary.ActivitySummaryScreen

/**
 * Home + Recording routes are unchanged from before auth existed; this just
 * adds the sign-in gate in front of them. Recording deliberately never sits
 * behind the gate's *logic* (RecordingRepository/ActivityRecordingService
 * don't touch Supabase at all — see SyncManager for the only thing that
 * does) — the gate only decides which composable is on screen, so a
 * signed-out user can't reach the record buttons because HomeScreen itself
 * isn't reachable yet, not because recording requires auth. That's a
 * product choice (spec section 9 assumes one Google identity end to end)
 * more than a technical one.
 */
private const val ROUTE_HOME = "home"
private const val ARG_ACTIVITY_TYPE = "activityType"
private const val ROUTE_RECORDING = "recording/{$ARG_ACTIVITY_TYPE}"
private const val ARG_ACTIVITY_ID = "activityId"
private const val ROUTE_SUMMARY = "summary/{$ARG_ACTIVITY_ID}"
private const val ROUTE_PROFILE = "profile"

@Composable
fun PersonalStravaNavHost(
    onSignedIn: () -> Unit = {},
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthUiState.SignedIn) onSignedIn()
    }

    when (authState) {
        AuthUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthUiState.SignedOut -> SignInScreen()
        AuthUiState.SignedIn -> {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = ROUTE_HOME) {
                composable(ROUTE_HOME) {
                    HomeScreen(
                        onStartWalking = { navController.navigate("recording/${ActivityType.WALKING.name}") },
                        onStartJogging = { navController.navigate("recording/${ActivityType.JOGGING.name}") },
                        onStartCycling = { navController.navigate("recording/${ActivityType.CYCLING.name}") },
                        onStartMotorcycling = { navController.navigate("recording/${ActivityType.MOTORCYCLING.name}") },
                        onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
                        onOpenActivity = { activityId -> navController.navigate("summary/$activityId") },
                    )
                }
                composable(ROUTE_PROFILE) {
                    ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onSignedOut = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
                    )
                }
                composable(
                    route = ROUTE_RECORDING,
                    arguments = listOf(navArgument(ARG_ACTIVITY_TYPE) { type = NavType.StringType }),
                ) { backStackEntry ->
                    val activityType = ActivityType.valueOf(
                        backStackEntry.arguments?.getString(ARG_ACTIVITY_TYPE) ?: ActivityType.CYCLING.name,
                    )
                    RecordingScreen(
                        activityType = activityType,
                        onFinished = { finishedActivityId ->
                            if (finishedActivityId != null) {
                                // Route through the post-ride summary/share screen instead of
                                // straight back to Home, and drop the recording route from the
                                // back stack so "Done" there (or the system back button) lands
                                // on Home rather than re-showing the finished recording screen.
                                navController.navigate("summary/$finishedActivityId") {
                                    popUpTo(ROUTE_HOME) { inclusive = false }
                                }
                            } else {
                                navController.popBackStack()
                            }
                        },
                    )
                }
                composable(
                    route = ROUTE_SUMMARY,
                    arguments = listOf(navArgument(ARG_ACTIVITY_ID) { type = NavType.StringType }),
                ) { backStackEntry ->
                    val activityId = backStackEntry.arguments?.getString(ARG_ACTIVITY_ID) ?: return@composable
                    ActivitySummaryScreen(
                        activityId = activityId,
                        onDone = {
                            navController.popBackStack(ROUTE_HOME, inclusive = false)
                        },
                    )
                }
            }
        }
    }
}
