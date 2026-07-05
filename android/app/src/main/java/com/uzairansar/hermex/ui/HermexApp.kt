package com.uzairansar.hermex.ui

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uzairansar.hermex.AppContainer
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.ui.chat.ChatRoute
import com.uzairansar.hermex.ui.git.GitRoute
import com.uzairansar.hermex.ui.onboarding.OnboardingRoute
import com.uzairansar.hermex.ui.panels.PanelsRoute
import com.uzairansar.hermex.ui.sessions.SessionListRoute
import com.uzairansar.hermex.ui.settings.SettingsRoute
import com.uzairansar.hermex.ui.theme.HermexTheme
import com.uzairansar.hermex.ui.workspace.WorkspaceRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun HermexApp(
    container: AppContainer,
    shortcutIntents: Flow<Intent> = emptyFlow(),
) {
    val navController = rememberNavController()
    val authState by container.authRepository.state.collectAsStateWithLifecycle()
    val themeMode by container.localSettingsRepository.themeMode.collectAsStateWithLifecycle(
        initialValue = com.uzairansar.hermex.data.preferences.AppThemeMode.System,
    )

    LaunchedEffect(navController, shortcutIntents) {
        shortcutIntents.collect { intent ->
            intent.hermexRoute()?.let { route ->
                navController.navigate(route)
            } ?: navController.handleDeepLink(intent)
        }
    }

    HermexTheme(themeMode = themeMode) {
        Scaffold { padding ->
            NavHost(
                navController = navController,
                startDestination = if (authState is AuthState.LoggedIn) "sessions" else "onboarding",
                modifier = Modifier.padding(padding),
            ) {
                composable("onboarding") {
                    OnboardingRoute(
                        authRepository = container.authRepository,
                        onConnected = {
                            val route = if (container.sharedDraftStore.hasPendingDraft()) {
                                ShortcutDestination.shareRoute()
                            } else {
                                "sessions"
                            }
                            navController.navigate(route) {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        },
                    )
                }
                composable(
                    route = "sessions?shortcutAction={shortcutAction}&shortcutNonce={shortcutNonce}",
                    arguments = listOf(
                        navArgument("shortcutAction") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("shortcutNonce") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = ShortcutDestination.SessionsUri },
                        navDeepLink { uriPattern = ShortcutDestination.NewSessionUriPattern },
                    ),
                ) { entry ->
                    val shortcutAction = ShortcutDestination.supportedAction(entry.arguments?.getString("shortcutAction"))
                    SessionListRoute(
                        authState = authState,
                        container = container,
                        shortcutAction = shortcutAction,
                        shortcutNonce = entry.arguments?.getString("shortcutNonce"),
                        onOpenChat = { sessionId -> navController.navigate("chat/$sessionId") },
                        onOpenSharedDraft = { sessionId -> navController.navigate("chat/$sessionId?consumeShare=true") },
                        onOpenPanels = { navController.navigate("panels") },
                        onOpenSettings = { navController.navigate("settings") },
                        onNeedsOnboarding = {
                            navController.navigate("onboarding") {
                                popUpTo("sessions") { inclusive = true }
                            }
                        },
                    )
                }
                composable(
                    route = "chat/{sessionId}?consumeShare={consumeShare}",
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("consumeShare") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server == null) {
                        OnboardingRoute(container.authRepository) {}
                    } else {
                        ChatRoute(
                            sessionId = requireNotNull(entry.arguments?.getString("sessionId")),
                            repository = container.chatRepository(server),
                            sharedDraftStore = container.sharedDraftStore,
                            consumeSharedDraft = entry.arguments?.getBoolean("consumeShare") == true,
                            onBack = { navController.popBackStack() },
                            onOpenWorkspace = {
                                navController.navigate("workspace/${requireNotNull(entry.arguments?.getString("sessionId"))}")
                            },
                            onOpenGit = {
                                navController.navigate("git/${requireNotNull(entry.arguments?.getString("sessionId"))}")
                            },
                        )
                    }
                }
                composable(
                    route = "workspace/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) { entry ->
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server != null) {
                        WorkspaceRoute(
                            sessionId = requireNotNull(entry.arguments?.getString("sessionId")),
                            repository = container.workspaceRepository(server),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    route = "git/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) { entry ->
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server != null) {
                        GitRoute(
                            sessionId = requireNotNull(entry.arguments?.getString("sessionId")),
                            repository = container.gitRepository(server),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    route = "panels",
                    deepLinks = listOf(navDeepLink { uriPattern = ShortcutDestination.PanelsUri }),
                ) {
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server != null) {
                        PanelsRoute(
                            panelsRepository = container.panelsRepository(server),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    route = "settings",
                    deepLinks = listOf(navDeepLink { uriPattern = ShortcutDestination.SettingsUri }),
                ) {
                    val server = (authState as? AuthState.LoggedIn)?.server
                    SettingsRoute(
                        authRepository = container.authRepository,
                        localSettingsRepository = container.localSettingsRepository,
                        panelsRepository = server?.let { container.panelsRepository(it) },
                        authState = authState,
                        onBack = { navController.popBackStack() },
                        onSignedOut = {
                            navController.navigate("onboarding") {
                                popUpTo("sessions") { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun Intent.hermexRoute(): String? {
    val uri = data ?: return null
    if (uri.scheme != "hermes-agent") return null
    return when (uri.host) {
        "share" -> ShortcutDestination.shareRoute()
        "sessions" -> ShortcutDestination.sessionsRoute(uri.getQueryParameter("shortcutAction"))
        "settings" -> "settings"
        "panels" -> "panels"
        else -> null
    }
}

private fun ShortcutDestination.sessionsRoute(action: String? = null): String {
    val supportedAction = supportedAction(action) ?: return "sessions"
    return "sessions?shortcutAction=$supportedAction&shortcutNonce=${System.currentTimeMillis()}"
}

private fun ShortcutDestination.shareRoute(): String =
    sessionsRoute(ShortcutDestination.ShareAction)
