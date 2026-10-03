package com.personal.budget.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.personal.budget.BudgetApp
import com.personal.budget.MainActivity
import com.personal.budget.R
import com.personal.budget.domain.usecase.Money
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget: leftover this month, top 3 expense categories with what's left, and an
 * Add button that deep-links straight into the add-transaction sheet (budget://add).
 * Reads the same Room database as the app (through BudgetRepository); no separate copy.
 * Responsive: small (leftover + Add), medium (+ top 3 compact), large (+ progress bars).
 */
class BudgetWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as BudgetApp
        val state = runCatching { WidgetState.from(app.container.repository.snapshot.first()) }
            .getOrElse { WidgetState("", 0.0, 0.0, emptyList(), hasData = false) }
        provideContent { WidgetContent(state) }
    }

    companion object {
        val SMALL = DpSize(120.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 220.dp)
    }
}

class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetWidget()
}

/** Brand colours (design/tokens.json v2), day/night following the launcher's theme. */
private object WColors {
    val bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF0A1122))
    val ink = ColorProvider(day = Color(0xFF08204F), night = Color(0xFFF5F8FF))
    val ink2 = ColorProvider(day = Color(0xFF415373), night = Color(0xFFC2CEE2))
    val ink3 = ColorProvider(day = Color(0xFF63718A), night = Color(0xFF91A2BF))
    val accent = ColorProvider(day = Color(0xFF1059FC), night = Color(0xFF4A82FF))
    val onAccent = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF0A1122))
    val good = ColorProvider(day = Color(0xFF15803D), night = Color(0xFF4ADE80))
    val bad = ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFFF8585))
    val track = ColorProvider(day = Color(0xFFEDF1F7), night = Color(0xFF1A2842))
}

private fun addIntent(context: Context) = Intent(Intent.ACTION_VIEW, Uri.parse("budget://add"), context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun WidgetContent(state: WidgetState) {
    val size = LocalSize.current
    val context = androidx.glance.LocalContext.current
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val compact = size.width < WidgetCompactWidth
    val showTop = size.height >= BudgetWidget.MEDIUM.height && size.width >= BudgetWidget.MEDIUM.width
    val showBars = size.height >= BudgetWidget.LARGE.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WColors.bg)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(openApp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.logo_mark), contentDescription = "Budget", modifier = GlanceModifier.size(16.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = ("Leftover · " + state.monthLabel).uppercase(),
                        style = TextStyle(color = WColors.ink3, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
                Text(
                    text = if (state.hasData) Money.format(state.leftover) else "—",
                    style = TextStyle(
                        color = if (state.leftover < 0) WColors.bad else WColors.ink,
                        fontSize = if (compact) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (!compact && state.hasData) {
                    Text(
                        text = "of ${Money.format(state.plannedLeftover)} planned",
                        style = TextStyle(color = WColors.ink2, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
            if (!compact) AddButton(context)
        }
        if (compact) {
            Spacer(GlanceModifier.defaultWeight())
            AddButton(context, fill = true)
        }
        if (showBars && state.netWorthTrend.size >= 2) {
            val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val density = context.resources.displayMetrics.density
            val bmp = SparklineBitmap.render(
                state.netWorthTrend,
                ((size.width.value - 28) * density).toInt(),
                (26 * density).toInt(),
                if (night) 0xFF4A82FF.toInt() else 0xFF1059FC.toInt(),
            )
            if (bmp != null) {
                Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("NET WORTH · 30D", style = TextStyle(color = WColors.ink3, fontSize = 10.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                    state.netWorth?.let { Text(Money.format(it), style = TextStyle(color = WColors.ink2, fontSize = 11.sp)) }
                }
                Image(ImageProvider(bmp), contentDescription = "Net worth over the last 30 days", modifier = GlanceModifier.fillMaxWidth().height(26.dp))
            }
        }
        if (showTop && !compact) {
            Spacer(GlanceModifier.height(10.dp))
            if (state.top.isEmpty()) {
                Text("No expense categories yet", style = TextStyle(color = WColors.ink3, fontSize = 12.sp))
            }
            state.top.forEach { c ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name,
                        style = TextStyle(color = WColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    val over = c.remaining < 0
                    Text(
                        if (over) "${Money.format(-c.remaining)} over" else "${Money.format(c.remaining)} left",
                        style = TextStyle(color = if (over) WColors.bad else WColors.ink2, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
                if (showBars) {
                    LinearProgressIndicator(
                        progress = c.progress,
                        modifier = GlanceModifier.fillMaxWidth().height(5.dp).cornerRadius(3.dp),
                        color = if (c.remaining < 0) WColors.bad else WColors.accent,
                        backgroundColor = WColors.track,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                }
            }
        }
    }
}

private val WidgetCompactWidth = 200.dp

@Composable
private fun AddButton(context: Context, fill: Boolean = false) {
    val base = GlanceModifier
        .background(WColors.accent)
        .cornerRadius(99.dp)
        .clickable(actionStartActivity(addIntent(context)))
        .semantics { contentDescription = "Add transaction" }
    Box(
        modifier = (if (fill) base.fillMaxWidth() else base).height(36.dp).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_widget_plus), contentDescription = null, modifier = GlanceModifier.size(16.dp), colorFilter = androidx.glance.ColorFilter.tint(WColors.onAccent))
            Spacer(GlanceModifier.width(4.dp))
            Text("Add", style = TextStyle(color = WColors.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
    }
}
