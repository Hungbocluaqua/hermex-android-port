package com.uzairansar.hermex.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.uzairansar.hermex.ui.theme.LocalHermexHapticsEnabled
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
    val hapticsEnabled by container.localSettingsRepository.hapticsEnabled.collectAsStateWithLifecycle(
        initialValue = true,
    )
    val localHeaderLogoColorHex by container.localSettingsRepository.headerLogoColorHex.collectAsStateWithLifecycle(
        initialValue = "#FFD700",
    )
    val activeAccount = (authState as? AuthState.LoggedIn)?.account
    val headerLogoColorHex = activeAccount?.headerLogoColorHex ?: localHeaderLogoColorHex

    LaunchedEffect(
        activeAccount?.id,
        activeAccount?.displayName,
        activeAccount?.initials,
        activeAccount?.headerLogoColorHex,
    ) {
        activeAccount ?: return@LaunchedEffect
        container.localSettingsRepository.setSessionIdentityDisplayName(activeAccount.displayName)
        container.localSettingsRepository.setSessionIdentityInitials(activeAccount.initials)
        container.localSettingsRepository.setHeaderLogoColorHex(activeAccount.headerLogoColorHex)
    }

    LaunchedEffect(navController, shortcutIntents) {
        shortcutIntents.collect { intent ->
            intent.hermexRoute()?.let { route ->
                navController.navigate(route)
            } ?: navController.handleDeepLink(intent)
        }
    }

    HermexTheme(themeMode = themeMode) {
        CompositionLocalProvider(LocalHermexHapticsEnabled provides hapticsEnabled) {
            NavHost(
                navController = navController,
                startDestination = if (authState is AuthState.LoggedIn) "sessions" else "onboarding",
                modifier = Modifier.fillMaxSize(),
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
                    route = "sessions?shortcutAction={shortcutAction}&shortcutNonce={shortcutNonce}&shortcutProfile={shortcutProfile}&showArchived={showArchived}",
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
                        navArgument("shortcutProfile") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("showArchived") {
                            type = NavType.BoolType
                            defaultValue = false
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
                        shortcutProfile = entry.arguments?.getString("shortcutProfile"),
                        initialArchived = entry.arguments?.getBoolean("showArchived") == true,
                        onOpenChat = { sessionId -> navController.navigate("chat/$sessionId") },
                        onOpenVoiceChat = { sessionId -> navController.navigate("chat/$sessionId?autoStartVoice=true") },
                        onOpenSharedDraft = { sessionId -> navController.navigate("chat/$sessionId?consumeShare=true") },
                        onOpenPanels = { navController.navigate("panels") },
                        onOpenPanel = { section -> navController.navigate("panels?section=$section") },
                        onOpenSettings = { navController.navigate("settings") },
                        onNeedsOnboarding = {
                            navController.navigate("onboarding") {
                                popUpTo("sessions") { inclusive = true }
                            }
                        },
                    )
                }
                composable(
                    route = "chat/{sessionId}?consumeShare={consumeShare}&autoStartVoice={autoStartVoice}",
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("consumeShare") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument("autoStartVoice") {
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
                            gitRepository = container.gitRepository(server),
                            localSettingsRepository = container.localSettingsRepository,
                            activeHeaderColorHex = headerLogoColorHex,
                            sharedDraftStore = container.sharedDraftStore,
                            consumeSharedDraft = entry.arguments?.getBoolean("consumeShare") == true,
                            autoStartVoice = entry.arguments?.getBoolean("autoStartVoice") == true,
                            onOpenChat = { sessionId -> navController.navigate("chat/$sessionId") },
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
                    route = "panels?section={section}",
                    arguments = listOf(
                        navArgument("section") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = ShortcutDestination.PanelsUri }),
                ) { entry ->
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server != null) {
                        PanelsRoute(
                            panelsRepository = container.panelsRepository(server),
                            initialSection = entry.arguments?.getString("section"),
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
                        cacheMaintenanceRepository = container.cacheMaintenanceRepository,
                        panelsRepository = server?.let { container.panelsRepository(it) },
                        authState = authState,
                        onBack = { navController.popBackStack() },
                        onOpenArchivedSessions = { navController.navigate("sessions?showArchived=true") },
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
        "new-chat" -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewSessionAction)
        "new-chat-voice" -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewVoiceSessionAction)
        "new-chat-profile" -> uri.getQueryParameter("profile")
            ?.takeIf { it.isNotBlank() }
            ?.let { profile -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewProfileSessionAction, profile) }
            ?: ShortcutDestination.sessionsRoute(ShortcutDestination.NewSessionAction)
        "settings" -> "settings"
        "panels" -> uri.getQueryParameter("section")?.takeIf { it.isNotBlank() }?.let { "panels?section=${Uri.encode(it)}" } ?: "panels"
        else -> null
    }
}

private fun ShortcutDestination.sessionsRoute(action: String? = null, profile: String? = null): String {
    val supportedAction = supportedAction(action) ?: return "sessions"
    val profileQuery = profile?.takeIf { it.isNotBlank() }?.let { "&shortcutProfile=${Uri.encode(it)}" }.orEmpty()
    return "sessions?shortcutAction=$supportedAction&shortcutNonce=${System.currentTimeMillis()}$profileQuery"
}

private fun ShortcutDestination.shareRoute(): String =
    sessionsRoute(ShortcutDestination.ShareAction)
