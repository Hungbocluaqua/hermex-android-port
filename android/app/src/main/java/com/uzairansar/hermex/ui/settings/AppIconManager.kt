package com.uzairansar.hermex.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.uzairansar.hermex.R

internal enum class AppIconChoice(
    val title: String,
    val subtitle: String,
    @get:DrawableRes val previewDrawableRes: Int,
    private val aliasName: String,
    internal val enabledByDefault: Boolean = false,
) {
    Default(
        title = "Default",
        subtitle = "Matches device appearance",
        previewDrawableRes = R.drawable.hermex_app_icon_default,
        aliasName = "AppIconDefault",
        enabledByDefault = true,
    ),
    Light(
        title = "Light",
        subtitle = "Always use the light icon",
        previewDrawableRes = R.drawable.hermex_app_icon_light,
        aliasName = "AppIconLight",
    ),
    Dark(
        title = "Dark",
        subtitle = "Always use the dark icon",
        previewDrawableRes = R.drawable.hermex_app_icon,
        aliasName = "AppIconDark",
    ),
    GradientLight(
        title = "Gradient Light",
        subtitle = "Always use the gradient light icon",
        previewDrawableRes = R.drawable.hermex_app_icon_gradient_light,
        aliasName = "AppIconGradientLight",
    ),
    GradientDark(
        title = "Gradient Dark",
        subtitle = "Always use the gradient dark icon",
        previewDrawableRes = R.drawable.hermex_app_icon_gradient_dark,
        aliasName = "AppIconGradientDark",
    ),
    MonochromeLight(
        title = "Monochrome Light",
        subtitle = "Always use the monochrome light icon",
        previewDrawableRes = R.drawable.hermex_app_icon_monochrome_light,
        aliasName = "AppIconMonochromeLight",
    ),
    MonochromeDark(
        title = "Monochrome Dark",
        subtitle = "Always use the monochrome dark icon",
        previewDrawableRes = R.drawable.hermex_app_icon_monochrome_dark,
        aliasName = "AppIconMonochromeDark",
    ),
    Disco(
        title = "Disco",
        subtitle = "Always use the disco icon",
        previewDrawableRes = R.drawable.hermex_app_icon_disco,
        aliasName = "AppIconDisco",
    );

    internal fun componentName(packageName: String): ComponentName =
        ComponentName(packageName, "$packageName.launcher.$aliasName")
}

internal enum class AppIconComponentState {
    Default,
    Enabled,
    Disabled,
}

internal data class AppIconComponentChange(
    val choice: AppIconChoice,
    val state: AppIconComponentState,
)

internal object AppIconSelectionPolicy {
    fun enabledChoices(states: Map<AppIconChoice, AppIconComponentState>): Set<AppIconChoice> =
        AppIconChoice.entries.filterTo(linkedSetOf()) { choice ->
            when (states[choice] ?: AppIconComponentState.Default) {
                AppIconComponentState.Enabled -> true
                AppIconComponentState.Disabled -> false
                AppIconComponentState.Default -> choice.enabledByDefault
            }
        }

    fun preferredChoice(states: Map<AppIconChoice, AppIconComponentState>): AppIconChoice {
        val enabled = enabledChoices(states)
        return when {
            enabled.size == 1 -> enabled.single()
            AppIconChoice.Default in enabled -> AppIconChoice.Default
            enabled.isNotEmpty() -> AppIconChoice.entries.first { it in enabled }
            else -> AppIconChoice.Default
        }
    }

    fun isExactSelection(
        states: Map<AppIconChoice, AppIconComponentState>,
        choice: AppIconChoice,
    ): Boolean = enabledChoices(states) == setOf(choice)

    fun changesFor(
        states: Map<AppIconChoice, AppIconComponentState>,
        choice: AppIconChoice,
    ): List<AppIconComponentChange> {
        if (isExactSelection(states, choice)) return emptyList()

        return buildList {
            if (choice !in enabledChoices(states)) {
                add(AppIconComponentChange(choice, AppIconComponentState.Enabled))
            }
            AppIconChoice.entries
                .filter { it != choice && it in enabledChoices(states) }
                .forEach { add(AppIconComponentChange(it, AppIconComponentState.Disabled)) }
        }
    }
}

internal interface AppIconComponentController {
    fun state(choice: AppIconChoice): AppIconComponentState
    fun setState(choice: AppIconChoice, state: AppIconComponentState)
}

internal class PackageManagerAppIconComponentController(
    context: Context,
) : AppIconComponentController {
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    override fun state(choice: AppIconChoice): AppIconComponentState =
        when (packageManager.getComponentEnabledSetting(choice.componentName(packageName))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> AppIconComponentState.Enabled
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> AppIconComponentState.Disabled
            else -> AppIconComponentState.Default
        }

    override fun setState(choice: AppIconChoice, state: AppIconComponentState) {
        val packageManagerState = when (state) {
            AppIconComponentState.Default -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            AppIconComponentState.Enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            AppIconComponentState.Disabled -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
            choice.componentName(packageName),
            packageManagerState,
            PackageManager.DONT_KILL_APP,
        )
    }
}

internal class AppIconManager(
    private val controller: AppIconComponentController,
) {
    constructor(context: Context) : this(PackageManagerAppIconComponentController(context.applicationContext))

    fun currentChoice(): AppIconChoice = AppIconSelectionPolicy.preferredChoice(snapshot())

    fun ensureValidSelection(): Result<AppIconChoice> {
        val states = runCatching(::snapshot).getOrElse { return Result.failure(it) }
        return setChoice(AppIconSelectionPolicy.preferredChoice(states), states)
    }

    fun setChoice(choice: AppIconChoice): Result<AppIconChoice> {
        val states = runCatching(::snapshot).getOrElse { return Result.failure(it) }
        return setChoice(choice, states)
    }

    private fun setChoice(
        choice: AppIconChoice,
        before: Map<AppIconChoice, AppIconComponentState>,
    ): Result<AppIconChoice> {
        return try {
            applyChanges(AppIconSelectionPolicy.changesFor(before, choice))
            check(AppIconSelectionPolicy.isExactSelection(snapshot(), choice)) {
                "Launcher icon selection did not settle on ${choice.title}."
            }
            Result.success(choice)
        } catch (failure: Throwable) {
            val rollbackChoice = AppIconSelectionPolicy.preferredChoice(before)
            runCatching {
                val current = snapshot()
                applyChanges(AppIconSelectionPolicy.changesFor(current, rollbackChoice))
                check(AppIconSelectionPolicy.isExactSelection(snapshot(), rollbackChoice))
            }.exceptionOrNull()?.let(failure::addSuppressed)
            Result.failure(failure)
        }
    }

    private fun snapshot(): Map<AppIconChoice, AppIconComponentState> =
        AppIconChoice.entries.associateWith(controller::state)

    private fun applyChanges(changes: List<AppIconComponentChange>) {
        changes.forEach { change -> controller.setState(change.choice, change.state) }
    }
}
