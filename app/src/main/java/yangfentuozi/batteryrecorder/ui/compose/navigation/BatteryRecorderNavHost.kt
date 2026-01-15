package yangfentuozi.batteryrecorder.ui.compose.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import yangfentuozi.batteryrecorder.ui.compose.srceens.home.HomeScreen
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.SettingsScreen
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.SettingsViewModel

private const val ANIMATION_DURATION = 300
private const val SCALE_FACTOR = 0.95f

private val animationSpec = tween<Float>(
    durationMillis = ANIMATION_DURATION,
    easing = FastOutSlowInEasing
)

private val defaultEnterTransition: EnterTransition = scaleIn(
    initialScale = SCALE_FACTOR,
    animationSpec = animationSpec
) + fadeIn(animationSpec = animationSpec)

private val defaultExitTransition: ExitTransition = fadeOut(animationSpec = animationSpec)

private val defaultPopEnterTransition: EnterTransition = fadeIn(animationSpec = animationSpec)

private val defaultPopExitTransition: ExitTransition = scaleOut(
    targetScale = SCALE_FACTOR,
    animationSpec = animationSpec
) + fadeOut(animationSpec = animationSpec)

@Composable
fun BatteryRecorderNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(
            route = Screen.Home.route,
            exitTransition = {
                // 从 MainPage 到 EditPage 时，MainPage 的退出动画
                slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth / 4 }) +
                        fadeOut()
            },
            // --- MainPage 的 popEnterTransition ---
            popEnterTransition = {
                // 当从 EditPage 返回 MainPage 时，MainPage 的进入动画
                slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth / 4 }) +
                        fadeIn()
            },

            enterTransition = { null },
//            exitTransition = { defaultExitTransition },
//            popEnterTransition = { defaultPopEnterTransition },
            popExitTransition = { null }
        ) {
            HomeScreen(
                viewModel = mainViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(
            route = Screen.Settings.route,
            enterTransition = { defaultEnterTransition },
            exitTransition = { defaultExitTransition },
            popEnterTransition = { defaultPopEnterTransition },
            popExitTransition = { defaultPopExitTransition }
        ) {
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
