package yangfentuozi.batteryrecorder.ui.navigation

import android.net.Uri
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.ui.screens.history.HistoryListScreen
import yangfentuozi.batteryrecorder.ui.screens.history.RecordDetailScreen
import yangfentuozi.batteryrecorder.ui.screens.home.HomeScreen
import yangfentuozi.batteryrecorder.ui.screens.settings.SettingsScreen
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel

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
        startDestination = NavRoute.Home.route,
        modifier = modifier
    ) {
        composable(
            route = NavRoute.Home.route,
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
                    navController.navigate(NavRoute.Settings.route)
                },
                onNavigateToHistoryList = { type ->
                    navController.navigate(NavRoute.HistoryList.createRoute(type.dirName))
                },
                onNavigateToRecordDetail = { type, name ->
                    navController.navigate(
                        NavRoute.RecordDetail.createRoute(
                            type.dirName,
                            Uri.encode(name)
                        )
                    )
                }
            )
        }
        composable(
            route = NavRoute.Settings.route,
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
        composable(
            route = NavRoute.HistoryList.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
            enterTransition = { defaultEnterTransition },
            exitTransition = {
                if (targetState.destination.route == NavRoute.RecordDetail.route) {
                    slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth / 4 }) +
                            fadeOut()
                } else {
                    defaultExitTransition
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == NavRoute.RecordDetail.route) {
                    slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth / 4 }) +
                            fadeIn()
                } else {
                    defaultPopEnterTransition
                }
            },
            popExitTransition = { defaultPopExitTransition }
        ) { backStackEntry ->
            val typeArg = backStackEntry.arguments?.getString("type") ?: RecordType.CHARGE.dirName
            val recordType = if (typeArg == RecordType.DISCHARGE.dirName) {
                RecordType.DISCHARGE
            } else {
                RecordType.CHARGE
            }
            HistoryListScreen(
                recordType = recordType,
                onNavigateToRecordDetail = { type, name ->
                    navController.navigate(
                        NavRoute.RecordDetail.createRoute(type.dirName, Uri.encode(name))
                    )
                }
            )
        }
        composable(
            route = NavRoute.RecordDetail.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            ),
            enterTransition = { defaultEnterTransition },
            exitTransition = { defaultExitTransition },
            popEnterTransition = { defaultPopEnterTransition },
            popExitTransition = { defaultPopExitTransition }
        ) { backStackEntry ->
            val typeArg = backStackEntry.arguments?.getString("type") ?: RecordType.CHARGE.dirName
            val nameArg = backStackEntry.arguments?.getString("name") ?: ""
            val recordType = if (typeArg == RecordType.DISCHARGE.dirName) {
                RecordType.DISCHARGE
            } else {
                RecordType.CHARGE
            }
            RecordDetailScreen(
                recordType = recordType,
                recordName = Uri.decode(nameArg)
            )
        }
    }
}
