package com.fatih.stopmotion.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.ui.audio.AudioScreen
import com.fatih.stopmotion.ui.camera.CameraScreen
import com.fatih.stopmotion.ui.camera.CaptureMode
import com.fatih.stopmotion.ui.editor.EditorScreen
import com.fatih.stopmotion.ui.erase.EraseScreen
import com.fatih.stopmotion.ui.export.ExportScreen
import com.fatih.stopmotion.ui.frames.FramesScreen
import com.fatih.stopmotion.ui.home.HomeScreen
import com.fatih.stopmotion.ui.imageedit.ImageEditScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_CAMERA = "camera/{projectId}?mode={mode}&target={target}"
private const val ROUTE_FRAMES = "frames/{projectId}"
private const val ROUTE_EDITOR = "editor/{projectId}"
private const val ROUTE_IMAGE_EDIT = "imageedit/{projectId}/{frameId}"
private const val ROUTE_AUDIO = "audio/{projectId}"
private const val ROUTE_EXPORT = "export/{projectId}"
private const val ROUTE_ERASE = "erase/{projectId}/{frameId}"

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = ROUTE_HOME,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(250)) }
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                container = container,
                onOpenProject = { id -> nav.navigate("camera/$id") }
            )
        }
        composable(
            route = ROUTE_CAMERA,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("mode") { type = NavType.StringType; defaultValue = CaptureMode.APPEND },
                navArgument("target") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            val mode = entry.arguments?.getString("mode") ?: CaptureMode.APPEND
            val target = entry.arguments?.getLong("target") ?: -1L
            CameraScreen(
                projectId = id,
                mode = mode,
                target = target,
                container = container,
                onBack = { nav.popBackStack() },
                onOpenFrames = { nav.navigate("frames/$id") },
                onOpenEditor = { nav.navigate("editor/$id") }
            )
        }
        composable(
            route = ROUTE_FRAMES,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            FramesScreen(
                projectId = id,
                container = container,
                onBack = { nav.popBackStack() },
                onCapture = { mode, target -> nav.navigate("camera/$id?mode=$mode&target=$target") },
                onEditImage = { frameId -> nav.navigate("imageedit/$id/$frameId") }
            )
        }
        composable(
            route = ROUTE_EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            EditorScreen(
                projectId = id,
                container = container,
                onBack = { nav.popBackStack() },
                onEditImage = { frameId -> nav.navigate("imageedit/$id/$frameId") },
                onErase = { frameId -> nav.navigate("erase/$id/$frameId") },
                onOpenAudio = { nav.navigate("audio/$id") },
                onExport = { nav.navigate("export/$id") }
            )
        }
        composable(
            route = ROUTE_IMAGE_EDIT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("frameId") { type = NavType.LongType }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            val frameId = entry.arguments?.getLong("frameId") ?: 0L
            ImageEditScreen(
                projectId = id,
                frameId = frameId,
                container = container,
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = ROUTE_ERASE,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("frameId") { type = NavType.LongType }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            val frameId = entry.arguments?.getLong("frameId") ?: 0L
            EraseScreen(
                projectId = id,
                frameId = frameId,
                container = container,
                onBack = { nav.popBackStack() },
                onCapturePlate = { nav.navigate("camera/$id?mode=plate&target=0") }
            )
        }
        composable(
            route = ROUTE_AUDIO,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            AudioScreen(
                projectId = id,
                container = container,
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = ROUTE_EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            ExportScreen(
                projectId = id,
                container = container,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
