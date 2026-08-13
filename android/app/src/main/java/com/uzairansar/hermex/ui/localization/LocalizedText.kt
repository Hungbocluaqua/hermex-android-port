package com.uzairansar.hermex.ui.localization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

internal fun Context.localizedString(englishText: String): String {
    val resourceId = AndroidLocalizationCatalog.resourceId(englishText) ?: return englishText
    return getString(resourceId)
}

@Composable
internal fun localizedString(englishText: String): String {
    val resourceId = AndroidLocalizationCatalog.resourceId(englishText) ?: return englishText
    return stringResource(resourceId)
}

@Composable
internal fun localizedStringFormat(englishTemplate: String, vararg arguments: Any?): String {
    val localizedTemplate = localizedString(englishTemplate)
    var argumentIndex = 0
    return IOS_FORMAT_PLACEHOLDER.replace(localizedTemplate) {
        arguments.getOrNull(argumentIndex++)?.toString().orEmpty()
    }
}

@Composable
internal fun localizedPluralString(englishTemplate: String, quantity: Int): String {
    val resourceId = AndroidLocalizationCatalog.pluralResourceId(englishTemplate)
        ?: return localizedStringFormat(englishTemplate, quantity)
    return pluralStringResource(resourceId, quantity, quantity)
}

private val IOS_FORMAT_PLACEHOLDER = Regex("%(?:\\d+\\$)?(?:lld|ld|d|@)")
