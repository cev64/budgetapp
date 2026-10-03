package com.personal.budget.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (ISC licence, android/licenses/ISC-Lucide.txt), the same set the web app uses
 * via lucide-react. Generated from lucide-static 1.50.0 SVGs: every element is converted to
 * path data and drawn as a 1.75px round-capped stroke on a 24x24 grid (docs/UI_ANATOMY.md).
 * Tint with Icon(tint = …).
 */
object Lucide {
    val Sheet: ImageVector by lazy { icon("sheet", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z", "M3 9L21 9", "M3 15L21 15", "M9 9L9 21", "M15 9L15 21") }
    val House: ImageVector by lazy { icon("house", "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    val CalendarDays: ImageVector by lazy { icon("calendar-days", "M8 2v3", "M16 2v3", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z", "M3 9h18", "M8 13h.01", "M12 13h.01", "M16 13h.01", "M8 17h.01", "M12 17h.01", "M16 17h.01") }
    val ChartColumn: ImageVector by lazy { icon("chart-column", "M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3") }
    val Landmark: ImageVector by lazy { icon("landmark", "M10 18v-7", "M11.119 2.205a2 2 0 0 1 1.762 0l7.84 3.846A.5.5 0 0 1 20.5 7h-17a.5.5 0 0 1-.22-.949z", "M14 18v-7", "M18 18v-7", "M3 22h18", "M6 18v-7") }
    val Settings: ImageVector by lazy { icon("settings", "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    val Plus: ImageVector by lazy { icon("plus", "M5 12h14", "M12 5v14") }
    val Lock: ImageVector by lazy { icon("lock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z", "M7 11V7a5 5 0 0 1 10 0v4") }
    val LockOpen: ImageVector by lazy { icon("lock-open", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z", "M7 11V7a5 5 0 0 1 9.9-1") }
    val RefreshCw: ImageVector by lazy { icon("refresh-cw", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5") }
    val ChevronLeft: ImageVector by lazy { icon("chevron-left", "m15 18-6-6 6-6") }
    val ChevronRight: ImageVector by lazy { icon("chevron-right", "m9 18 6-6-6-6") }
    val List: ImageVector by lazy { icon("list", "M3 5h.01", "M3 12h.01", "M3 19h.01", "M8 5h13", "M8 12h13", "M8 19h13") }
    val Pencil: ImageVector by lazy { icon("pencil", "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4") }
    val X: ImageVector by lazy { icon("x", "M18 6 6 18", "m6 6 12 12") }
    val Trash2: ImageVector by lazy { icon("trash-2", "M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2") }
    val Check: ImageVector by lazy { icon("check", "M20 6 9 17l-5-5") }
    val Copy: ImageVector by lazy { icon("copy", "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2") }
    val ChevronUp: ImageVector by lazy { icon("chevron-up", "m18 15-6-6-6 6") }
    val ChevronDown: ImageVector by lazy { icon("chevron-down", "m6 9 6 6 6-6") }
    val Archive: ImageVector by lazy { icon("archive", "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", "M10 12h4") }
    val ArchiveRestore: ImageVector by lazy { icon("archive-restore", "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M4 8v11a2 2 0 0 0 2 2h2", "M20 8v11a2 2 0 0 1-2 2h-2", "m9 15 3-3 3 3", "M12 12v9") }
    val CloudOff: ImageVector by lazy { icon("cloud-off", "M10.94 5.274A7 7 0 0 1 15.71 10h1.79a4.5 4.5 0 0 1 4.222 6.057", "M18.796 18.81A4.5 4.5 0 0 1 17.5 19H9A7 7 0 0 1 5.79 5.78", "m2 2 20 20") }
    val CircleAlert: ImageVector by lazy { icon("circle-alert", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M12 8L12 12", "M12 16L12.01 16") }
    val LogOut: ImageVector by lazy { icon("log-out", "m16 17 5-5-5-5", "M21 12H9", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4") }
    val Download: ImageVector by lazy { icon("download", "M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5") }
    val Upload: ImageVector by lazy { icon("upload", "M12 3v12", "m17 8-5-5-5 5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4") }
    val Sun: ImageVector by lazy { icon("sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41") }
    val Moon: ImageVector by lazy { icon("moon", "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401") }
    val Info: ImageVector by lazy { icon("info", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M12 16v-4", "M12 8h.01") }
    val Minus: ImageVector by lazy { icon("minus", "M5 12h14") }
    val Wallet: ImageVector by lazy { icon("wallet", "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1", "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4") }
    val Repeat: ImageVector by lazy { icon("repeat", "m17 2 4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "m7 22-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3") }
    val ArrowUp: ImageVector by lazy { icon("arrow-up", "m5 12 7-7 7 7", "M12 19V5") }
    val ArrowDown: ImageVector by lazy { icon("arrow-down", "M12 5v14", "m19 12-7 7-7-7") }
    val Search: ImageVector by lazy { icon("search", "m21 21-4.34-4.34", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0") }
    val HandCoins: ImageVector by lazy { icon("hand-coins", "M11 15h2a2 2 0 1 0 0-4h-3c-.6 0-1.1.2-1.4.6L3 17", "m7 21 1.6-1.4c.3-.4.8-.6 1.4-.6h4c1.1 0 2.1-.4 2.8-1.2l4.6-4.4a2 2 0 0 0-2.75-2.91l-4.2 3.9", "m2 16 6 6", "M13.1 9a2.9 2.9 0 1 0 5.8 0a2.9 2.9 0 1 0 -5.8 0", "M3 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    val PiggyBank: ImageVector by lazy { icon("piggy-bank", "M11 17h3v2a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1v-3a3.16 3.16 0 0 0 2-2h1a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1h-1a5 5 0 0 0-2-4V3a4 4 0 0 0-3.2 1.6l-.3.4H11a6 6 0 0 0-6 6v1a5 5 0 0 0 2 4v3a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1z", "M16 10h.01", "M2 8v1a2 2 0 0 0 2 2h1") }
    val Undo2: ImageVector by lazy { icon("undo-2", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11") }
    val GripVertical: ImageVector by lazy { icon("grip-vertical", "M8 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M8 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M8 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M14 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M14 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M14 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }
    val CircleCheck: ImageVector by lazy { icon("circle-check", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "m16 9-5.5 5.5L8 12") }
    val Circle: ImageVector by lazy { icon("circle", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0") }
    val CloudCheck: ImageVector by lazy { icon("cloud-check", "m17 15-5.5 5.5L9 18", "M5.516 16.07A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 3.501 7.327") }
    val Monitor: ImageVector by lazy { icon("monitor", "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", "M8 21L16 21", "M12 17L12 21") }
    val User: ImageVector by lazy { icon("user", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0") }
    val Mail: ImageVector by lazy { icon("mail", "m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7", "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z") }
    val Sparkles: ImageVector by lazy { icon("sparkles", "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z", "M20 2v4", "M22 4h-4", "M2 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0") }
    val FileDown: ImageVector by lazy { icon("file-down", "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z", "M14 2v5a1 1 0 0 0 1 1h5", "M12 18v-6", "m9 15 3 3 3-3") }
    val FileUp: ImageVector by lazy { icon("file-up", "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z", "M14 2v5a1 1 0 0 0 1 1h5", "M12 12v6", "m15 15-3-3-3 3") }
    val Receipt: ImageVector by lazy { icon("receipt", "M12 17V7", "M16 8h-6a2 2 0 0 0 0 4h4a2 2 0 0 1 0 4H8", "M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z") }
    val ChefHat: ImageVector by lazy { icon("chef-hat", "M17 21a1 1 0 0 0 1-1v-5.35c0-.457.316-.844.727-1.041a4 4 0 0 0-2.134-7.589 5 5 0 0 0-9.186 0 4 4 0 0 0-2.134 7.588c.411.198.727.585.727 1.041V20a1 1 0 0 0 1 1Z", "M6 17h12") }
    val Clock: ImageVector by lazy { icon("clock", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M12 6v6l4 2") }
    val Calendar: ImageVector by lazy { icon("calendar", "M8 2v3", "M16 2v3", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z", "M3 9h18") }
    val Link: ImageVector by lazy { icon("link", "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71") }
    val EyeOff: ImageVector by lazy { icon("eye-off", "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20") }
    val Eye: ImageVector by lazy { icon("eye", "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    val RotateCcw: ImageVector by lazy { icon("rotate-ccw", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5") }
    val EllipsisVertical: ImageVector by lazy { icon("ellipsis-vertical", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }
}

private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.75f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()
