package com.uzairansar.hermex

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.uzairansar.hermex.ui.ShortcutDestination

class HermexWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.hermex_widget).apply {
            setOnClickPendingIntent(
                R.id.widget_root,
                launchIntent(context, ShortcutDestination.SessionsUri, REQUEST_OPEN_SESSIONS),
            )
            setOnClickPendingIntent(
                R.id.widget_open_sessions,
                launchIntent(context, ShortcutDestination.SessionsUri, REQUEST_OPEN_SESSIONS),
            )
            setOnClickPendingIntent(
                R.id.widget_new_session,
                launchIntent(context, ShortcutDestination.NewSessionUri, REQUEST_NEW_SESSION),
            )
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun launchIntent(context: Context, uri: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_OPEN_SESSIONS = 100
        const val REQUEST_NEW_SESSION = 101
    }
}
