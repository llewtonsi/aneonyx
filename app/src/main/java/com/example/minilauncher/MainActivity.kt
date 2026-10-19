package com.example.minilauncher

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import android.graphics.Bitmap
import android.graphics.Canvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────────────────────── Activity ─────────────────────────────

class MainActivity : ComponentActivity() {

    // Bumped every time the Home button is pressed while we're already running.
    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the selected system wallpaper show through the launcher window.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LauncherScreen(lifecycleOwner = this, homeSignal = homeSignal.intValue)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeSignal.intValue++
        }
    }
}

// ───────────────────────────── Theme constants ─────────────────────────────

private val BORDER = Color(0xFF262B35)
private val GREEN = Color(0xFF00FF66)
private val CYAN = Color(0xFF00E5FF)
private val AMBER = Color(0xFFFFCC00)
private val RED = Color(0xFFFF2A6D)
private val DIM = Color(0xFF858B98)
private val TEXT = Color(0xFFE0E0E0)
private val ALPHABET = ('A'..'Z').toList() + '#'
private val COMMAND_HELP = """AVAILABLE COMMANDS
/clear
  Clear the terminal output.
/free
  Request garbage collection for VixLauncher's app memory and refresh its RAM stats.
/time
  Show the current local time.
/sysinfo
  Show device, Android, CPU ABI, and display information.
/pkg <query>
  Open Android App Info for the first matching launcher app.
/clip [clear]
  Show plain-text clipboard contents, or clear the clipboard.
/dnd [minutes]
  Toggle Do Not Disturb. Use Android Quick Settings to set a timed session.
/ls [subfolder]
  List files and folders in the current directory or a relative subfolder.
/pwd
  Print the current directory.
/mkdir <directory> | /rmdir <directory> | /rm <file>
  Create or remove items in shared storage.
/find <query>
  Find matching folders under the current directory.
/grep <pattern> <file>
  Print matching lines from a text file.
/calc <expression>
  Calculate arithmetic expressions with +, -, *, /, %, and parentheses.
/ps | /kill <app>
  Show visible app processes. Android does not let launchers force-stop other apps.
/open <app> | /uninstall <app>
  Launch an app or open Android's uninstall confirmation.
/cd <directory>
  Change directory. Use /cd .. to move up one directory.
/cat <filename>
  Show the first 20 lines of a text file up to 1 MiB.
/history [clear]
  Show the last 20 successful commands, or clear the saved history.
/layout <grid|list>
  Switch the app drawer layout and save your choice.
/help
  Show this command list.

GC requests affect only VixLauncher's app memory. Android manages system RAM and caches."""

private val HELP_COMMAND_TOKENS = listOf(
    "/clear", "/free", "/time", "/sysinfo", "/pkg", "/clip", "/dnd",
    "/ls", "/pwd", "/mkdir", "/rmdir", "/rm", "/find", "/grep", "/calc", "/ps", "/kill", "/open", "/uninstall", "/cd", "/cat", "/history", "/layout", "/help",
)

private const val PREFS_NAME = "vixlauncher_preferences"
private const val PREF_COMMAND_HISTORY = "command_history"
private const val PREF_GRID_LAYOUT = "grid_layout"
private const val MAX_COMMAND_HISTORY = 20
private const val MAX_LISTING_ENTRIES = 200
private const val MAX_TEXT_FILE_BYTES = 1024L * 1024L
private const val TELEMETRY_REFRESH_MS = 5_000L
private const val CPU_REFRESH_MS = 15_000L
private const val BATTERY_REFRESH_MS = 30_000L
private const val DISK_REFRESH_MS = 30_000L

private data class CommandResult(
    val output: String,
    val success: Boolean = true,
    val newPath: String? = null,
    val clearHistory: Boolean = false,
    val newGridLayout: Boolean? = null,
    val clearOutput: Boolean = false,
)

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)

private fun loadWallpaper(ctx: Context): ImageBitmap? = runCatching {
    val drawable = WallpaperManager.getInstance(ctx).drawable ?: return null
    val sourceWidth = drawable.intrinsicWidth.coerceAtLeast(1)
    val sourceHeight = drawable.intrinsicHeight.coerceAtLeast(1)
    val scale = minOf(1f, 2048f / maxOf(sourceWidth, sourceHeight))
    val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).also { canvas ->
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
    }
    bitmap.asImageBitmap()
}.getOrNull()

// ───────────────────────────── Data model ─────────────────────────────

@Immutable
private data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val searchLabel: String = label.lowercase(Locale.ROOT),
    val key: String = "$packageName/$activityName",
)

private data class PromptIdentity(val username: String, val deviceName: String)

private fun promptToken(value: String, fallback: String): String = value
    .trim()
    .replace(Regex("\\s+"), "-")
    .ifEmpty { fallback }

private fun readPromptIdentity(ctx: Context): PromptIdentity {
    val userManager = ctx.getSystemService(UserManager::class.java)
    val profileName = if (Build.VERSION.SDK_INT >= 24) {
        runCatching { userManager?.userName }.getOrNull().orEmpty()
    } else ""

    val configuredDeviceName = if (Build.VERSION.SDK_INT >= 25) {
        runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull().orEmpty()
    } else ""

    return PromptIdentity(
        username = promptToken(profileName, "user"),
        deviceName = promptToken(configuredDeviceName.ifBlank { Build.MODEL }, "android"),
    )
}

private fun loadApps(pm: PackageManager): List<AppEntry> {
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(query, 0)
    }
    return infos
        .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
        .sortedBy { it.searchLabel }
}

private fun launchApp(ctx: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { ctx.startActivity(intent) }
}

private fun loadAppIcon(ctx: Context, app: AppEntry): ImageBitmap? = runCatching {
    val drawable = ctx.packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
    val iconSize = 96
    val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val oldBounds = drawable.bounds
    drawable.setBounds(0, 0, iconSize, iconSize)
    drawable.draw(canvas)
    drawable.bounds = oldBounds
    bitmap.asImageBitmap()
}.getOrNull()

private fun openAppInfo(ctx: Context, packageName: String): Boolean {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        ctx.startActivity(intent)
        true
    }.getOrDefault(false)
}

private fun findAppMatch(apps: List<AppEntry>, query: String): AppEntry? {
    val normalized = query.trim().lowercase(Locale.ROOT)
    if (normalized.isEmpty()) return null
    return apps.firstOrNull { it.packageName.equals(normalized, ignoreCase = true) }
        ?: apps.firstOrNull { it.searchLabel == normalized }
        ?: apps.firstOrNull {
            it.searchLabel.contains(normalized) || it.packageName.lowercase(Locale.ROOT).contains(normalized)
        }
}

private fun deviceInfo(ctx: Context): String {
    val display = ctx.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
    val metrics = DisplayMetrics()
    if (display != null) {
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
    }
    val resolution = if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
        "${metrics.widthPixels} × ${metrics.heightPixels}px"
    } else "unavailable"
    val refreshRate = display?.refreshRate?.let { "%.1f Hz".format(Locale.ROOT, it) } ?: "unavailable"
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch", "unknown")
    return """DEVICE  ${Build.MANUFACTURER} ${Build.MODEL}
ANDROID ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
CPU ABI $abi
DISPLAY $resolution @ $refreshRate
KERNEL  ${System.getProperty("os.version", "unknown")}"""
}

private fun clipboardCommand(ctx: Context, argument: String): String {
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    if (argument.equals("clear", ignoreCase = true)) {
        if (Build.VERSION.SDK_INT >= 28) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        return "Clipboard cleared."
    }
    if (argument.isNotBlank()) return "Usage: /clip [clear]"
    val clip = clipboard.primaryClip ?: return "Clipboard is empty or unavailable."
    val text = (0 until clip.itemCount)
        .mapNotNull { clip.getItemAt(it).text?.toString() }
        .joinToString("\n")
    if (text.isEmpty()) return "Clipboard has no plain-text content."
    val maxChars = 1_200
    return if (text.length > maxChars) text.take(maxChars) + "… [truncated]" else text
}

private fun dndCommand(ctx: Context, argument: String): String {
    if (argument.isNotBlank()) {
        val minutes = argument.toIntOrNull()
        if (minutes == null || minutes !in 1..1_440) return "Usage: /dnd [minutes] (1–1440)"
        return "Timed DND can't be safely scheduled here. Use Android Quick Settings to choose a duration."
    }

    val notificationManager = ctx.getSystemService(NotificationManager::class.java)
        ?: return "Do Not Disturb service is unavailable."
    if (!notificationManager.isNotificationPolicyAccessGranted) {
        return try {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            "Grant VixLauncher notification-policy access, then run /dnd again."
        } catch (_: Exception) {
            "Couldn't open notification-policy settings. Open them from Android Settings."
        }
    }

    val currentlyAll = notificationManager.currentInterruptionFilter ==
        NotificationManager.INTERRUPTION_FILTER_ALL
    val target = if (currentlyAll) NotificationManager.INTERRUPTION_FILTER_NONE
    else NotificationManager.INTERRUPTION_FILTER_ALL
    notificationManager.setInterruptionFilter(target)
    return if (currentlyAll) "Do Not Disturb enabled." else "Do Not Disturb disabled."
}

private fun fileRoot(): File = Environment.getExternalStorageDirectory().canonicalFile

private fun resolveExplorerPath(currentPath: String, requested: String): File {
    val root = fileRoot()
    val candidate = if (requested.isBlank()) File(currentPath) else {
        val requestedFile = File(requested)
        if (requestedFile.isAbsolute) requestedFile else File(currentPath, requested)
    }.canonicalFile
    val rootPath = root.path
    require(candidate.path == rootPath || candidate.path.startsWith(rootPath + File.separator)) {
        "Path is outside shared storage."
    }
    return candidate
}

private fun listDirectory(path: File): String {
    if (!path.exists() || !path.isDirectory) return "Directory not found: ${path.path}"
    val entries = path.listFiles() ?: return "Directory unreadable: ${path.path}"
    if (entries.isEmpty()) return "(empty directory)\n${path.path}"
    val shown = entries.take(MAX_LISTING_ENTRIES)
        .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    val lines = shown.map { file -> "${if (file.isDirectory) "[DIR]" else "[FILE]"} ${file.name}" }
    val suffix = if (entries.size > shown.size) "\n… ${entries.size - shown.size} more entries omitted" else ""
    return "${path.path}\n${lines.joinToString("\n")}$suffix"
}

private fun readTextPreview(path: File): String {
    if (!path.exists() || !path.isFile) return "File not found: ${path.path}"
    if (!path.canRead()) return "File unreadable: ${path.name}"
    if (path.length() > MAX_TEXT_FILE_BYTES) return "File too large; /cat is limited to 1 MiB."
    return try {
        val output = ArrayList<String>(20)
        BufferedReader(InputStreamReader(path.inputStream(), Charsets.UTF_8)).use { reader ->
            repeat(20) {
                val line = reader.readLine() ?: return@repeat
                output += if (line.length > 2_000) line.take(2_000) + "…" else line
            }
        }
        if (output.isEmpty()) "(empty file)" else output.joinToString("\n")
    } catch (_: Exception) {
        "File unreadable: ${path.name}"
    }
}

private class ArithmeticParser(private val source: String) {
    private var i = 0
    fun parse(): Double {
        val value = expression()
        spaces()
        require(i == source.length) { "Unexpected input near '${source.drop(i).take(12)}'" }
        require(value.isFinite()) { "Result is not finite." }
        return value
    }
    private fun spaces() { while (i < source.length && source[i].isWhitespace()) i++ }
    private fun eat(c: Char): Boolean { spaces(); if (i < source.length && source[i] == c) { i++; return true }; return false }
    private fun expression(): Double {
        var n = term()
        while (true) n = when { eat('+') -> n + term(); eat('-') -> n - term(); else -> return n }
    }
    private fun term(): Double {
        var n = unary()
        while (true) n = when {
            eat('*') -> n * unary()
            eat('/') -> { val d = unary(); require(d != 0.0) { "Division by zero." }; n / d }
            eat('%') -> { val d = unary(); require(d != 0.0) { "Division by zero." }; n % d }
            else -> return n
        }
    }
    private fun unary(): Double {
        if (eat('+')) return unary()
        if (eat('-')) return -unary()
        if (eat('(')) { val n = expression(); require(eat(')')) { "Missing )." }; return n }
        spaces(); val start = i
        while (i < source.length && (source[i].isDigit() || source[i] == '.')) i++
        require(i > start) { "Expected a number." }
        return source.substring(start, i).toDoubleOrNull() ?: error("Invalid number.")
    }
}

private suspend fun executeLocalCommand(
    ctx: Context,
    input: String,
    apps: List<AppEntry>,
    currentPath: String,
    history: List<String>,
    onMemoryCleanup: () -> Unit,
): CommandResult {
    val command = input.trim().removePrefix("/").trim()
    val name = command.substringBefore(' ').lowercase(Locale.ROOT)
    val argument = command.substringAfter(' ', "").trim()
    return try {
        when (name) {
            "clear" -> if (argument.isNotEmpty()) CommandResult("Usage: /clear", false)
                else CommandResult("", clearOutput = true)
            "free" -> {
                if (argument.isNotEmpty()) CommandResult("Usage: /$name", false)
                else {
                    onMemoryCleanup()
                    CommandResult("App GC requested; system caches are Android-managed.")
                }
            }
            "time" -> if (argument.isNotEmpty()) CommandResult("Usage: /time", false)
                else CommandResult(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
            "help" -> if (argument.isNotEmpty()) CommandResult("Usage: /help", false) else CommandResult(COMMAND_HELP)
            "sysinfo" -> if (argument.isNotEmpty()) CommandResult("Usage: /sysinfo", false) else CommandResult(deviceInfo(ctx))
            "pkg" -> {
                val app = findAppMatch(apps, argument)
                when {
                    argument.isBlank() -> CommandResult("Usage: /pkg <app name or package>", false)
                    app == null -> CommandResult("No matching launcher app found.", false)
                    openAppInfo(ctx, app.packageName) -> CommandResult("Opening App Info: ${app.label} (${app.packageName})")
                    else -> CommandResult("Couldn't open App Info for ${app.packageName}.", false)
                }
            }
            "clip" -> CommandResult(clipboardCommand(ctx, argument), success = argument.isBlank() || argument.equals("clear", true))
            "dnd" -> CommandResult(
                dndCommand(ctx, argument),
                success = argument.isBlank() && runCatching {
                    (ctx.getSystemService(NotificationManager::class.java))?.isNotificationPolicyAccessGranted == true
                }.getOrDefault(false),
            )
            "ls" -> {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val directory = resolveExplorerPath(currentPath, argument)
                        CommandResult(listDirectory(directory), success = directory.isDirectory && directory.canRead())
                    }.getOrElse { CommandResult(it.message ?: "Directory unreadable", false) }
                }
            }
            "pwd" -> if (argument.isNotEmpty()) CommandResult("Usage: /pwd", false) else CommandResult(currentPath)
            "mkdir", "rmdir", "rm" -> {
                if (argument.isBlank()) CommandResult("Usage: /$name <path>", false)
                else withContext(Dispatchers.IO) {
                    runCatching {
                        val target = resolveExplorerPath(currentPath, argument)
                        when (name) {
                            "mkdir" -> if (target.exists()) CommandResult("Already exists: ${target.path}", false)
                                else if (target.mkdirs()) CommandResult("Created directory: ${target.path}") else CommandResult("Could not create directory.", false)
                            "rmdir" -> if (!target.isDirectory) CommandResult("Directory not found: ${target.path}", false)
                                else if (target.delete()) CommandResult("Removed directory: ${target.path}") else CommandResult("Directory must be empty or is not writable.", false)
                            else -> if (!target.isFile) CommandResult("File not found: ${target.path}", false)
                                else if (target.delete()) CommandResult("Removed file: ${target.path}") else CommandResult("Could not remove file.", false)
                        }
                    }.getOrElse { CommandResult(it.message ?: "File operation failed.", false) }
                }
            }
            "find" -> {
                if (argument.isBlank()) CommandResult("Usage: /find <query>", false)
                else withContext(Dispatchers.IO) {
                    runCatching {
                        val base = resolveExplorerPath(currentPath, "")
                        val matches = base.walkTopDown().maxDepth(12).filter { it.isDirectory && it.name.contains(argument, true) }.take(100).map { it.path }.toList()
                        CommandResult(if (matches.isEmpty()) "No matching folders." else matches.joinToString("\n"))
                    }.getOrElse { CommandResult(it.message ?: "Search failed.", false) }
                }
            }
            "grep" -> {
                val pattern = argument.substringBefore(' ')
                val fileArg = argument.substringAfter(' ', "").trim()
                if (pattern.isBlank() || fileArg.isBlank()) CommandResult("Usage: /grep <pattern> <file>", false)
                else withContext(Dispatchers.IO) {
                    runCatching {
                        val file = resolveExplorerPath(currentPath, fileArg)
                        require(file.isFile && file.canRead()) { "File not readable: ${file.path}" }
                        require(file.length() <= MAX_TEXT_FILE_BYTES) { "File too large; limit is 1 MiB." }
                        val matches = file.readLines().take(20_000).mapIndexedNotNull { i, line -> if (line.contains(pattern, true)) "${i + 1}: $line" else null }
                        CommandResult(matches.take(200).joinToString("\n").ifEmpty { "No matches." })
                    }.getOrElse { CommandResult(it.message ?: "Search failed.", false) }
                }
            }
            "calc" -> if (argument.isBlank()) CommandResult("Usage: /calc <expression>", false) else
                runCatching { CommandResult(ArithmeticParser(argument).parse().toString()) }
                    .getOrElse { CommandResult(it.message ?: "Invalid expression.", false) }
            "ps" -> if (argument.isNotBlank()) CommandResult("Usage: /ps", false) else {
                val am = ctx.getSystemService(ActivityManager::class.java)
                @Suppress("DEPRECATION")
                val processes = am?.runningAppProcesses.orEmpty()
                CommandResult(processes.joinToString("\n") { "${it.processName} (pid ${it.pid})" }.ifEmpty { "No app processes are visible to this launcher." })
            }
            "kill" -> {
                val app = findAppMatch(apps, argument)
                if (app == null) CommandResult("Usage: /kill <app> — Android blocks launchers from force-stopping other apps.", false)
                else CommandResult("Android blocks force-stopping other apps from a launcher. Open App Info to stop ${app.label}.", false,
                    newPath = null).also { openAppInfo(ctx, app.packageName) }
            }
            "open" -> {
                val app = findAppMatch(apps, argument)
                if (argument.isBlank()) CommandResult("Usage: /open <app>", false)
                else if (app == null) CommandResult("No matching launcher app found.", false)
                else { launchApp(ctx, app); CommandResult("Opening ${app.label}.") }
            }
            "uninstall" -> {
                val app = findAppMatch(apps, argument)
                if (argument.isBlank()) CommandResult("Usage: /uninstall <app>", false)
                else if (app == null) CommandResult("No matching launcher app found.", false)
                else {
                    val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(intent) }.fold({ CommandResult("Opened uninstall confirmation for ${app.label}.") }, { CommandResult("Couldn't open uninstall confirmation.", false) })
                }
            }
            "cd" -> {
                if (argument.isBlank()) CommandResult("Usage: /cd <directory>", false)
                else withContext(Dispatchers.IO) {
                    runCatching {
                        if (argument == ".." && File(currentPath).canonicalFile == fileRoot()) {
                            return@runCatching CommandResult("Already at shared storage root: ${fileRoot().path}", newPath = fileRoot().path)
                        }
                        val directory = resolveExplorerPath(currentPath, argument)
                        if (!directory.exists() || !directory.isDirectory) CommandResult("Directory not found: ${directory.path}", false)
                        else if (!directory.canRead()) CommandResult("Directory unreadable: ${directory.path}", false)
                        else CommandResult("Current directory: ${directory.path}", newPath = directory.path)
                    }.getOrElse { CommandResult(it.message ?: "Directory not found", false) }
                }
            }
            "cat" -> {
                if (argument.isBlank()) CommandResult("Usage: /cat <filename>", false)
                else withContext(Dispatchers.IO) {
                    runCatching {
                        val file = resolveExplorerPath(currentPath, argument)
                        val output = readTextPreview(file)
                        CommandResult(output, success = file.isFile && file.canRead() && file.length() <= MAX_TEXT_FILE_BYTES)
                    }.getOrElse { CommandResult(it.message ?: "File unreadable", false) }
                }
            }
            "history" -> when {
                argument.equals("clear", ignoreCase = true) -> CommandResult("Command history cleared.", clearHistory = true)
                argument.isNotEmpty() -> CommandResult("Usage: /history [clear]", false)
                history.isEmpty() -> CommandResult("No command history yet.")
                else -> CommandResult(history.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n"))
            }
            "layout" -> when (argument.lowercase(Locale.ROOT)) {
                "grid" -> CommandResult("App drawer layout set to grid.", newGridLayout = true)
                "list" -> CommandResult("App drawer layout set to list.", newGridLayout = false)
                else -> CommandResult("Usage: /layout grid|list", false)
            }
            else -> CommandResult("Unknown command. Type /help", false)
        }
    } catch (_: SecurityException) {
        CommandResult("Permission denied. Check Android settings and try again.", false)
    } catch (_: Exception) {
        CommandResult("Command failed because an Android system service was unavailable.", false)
    }
}

// ───────────────────────────── Telemetry ─────────────────────────────

private data class Stats(
    val usedMb: Long,
    val totalMb: Long,
    val ramPct: Int,
    val diskUsedMb: Long,
    val diskTotalMb: Long,
    val diskPct: Int,
    val cores: Int,
    val load: Float?,        // 1-min load average, null if the kernel file isn't readable
    val batPct: Int,
    val charging: Boolean,
    val rxBytes: Long,
    val txBytes: Long,
    val sampledAtMs: Long,
    val rxBytesPerSec: Long? = null,
    val txBytesPerSec: Long? = null,
)

private fun readStats(
    ctx: Context,
    previous: Stats?,
    refreshDisk: Boolean,
    refreshCpuMetrics: Boolean,
    refreshBatteryMetrics: Boolean,
): Stats {
    // RAM
    val am = ctx.getSystemService(ActivityManager::class.java)
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val totalMb = mi.totalMem / (1024 * 1024)
    val usedMb = (mi.totalMem - mi.availMem) / (1024 * 1024)
    val ramPct = if (totalMb > 0) (usedMb * 100 / totalMb).toInt() else 0

    // StatFs reports the shared internal data volume without requesting storage
    // access permission. It reflects device storage use, including system data.
    val diskStats = if (!refreshDisk && previous != null) {
        Triple(previous.diskUsedMb, previous.diskTotalMb, previous.diskPct)
    } else {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes / (1024 * 1024)
            val free = stat.availableBytes / (1024 * 1024)
            val used = (total - free).coerceAtLeast(0)
            Triple(used, total, if (total > 0) (used * 100 / total).toInt() else 0)
        }.getOrElse { Triple(0L, 0L, 0) }
    }

    // CPU "state": /proc/stat is blocked for apps on Android 8+, so we report
    // core count and load average (best effort).
    val cores = Runtime.getRuntime().availableProcessors()
    val load = if (!refreshCpuMetrics && previous != null) previous.load else runCatching {
        File("/proc/loadavg").readText().substringBefore(' ').toFloat()
    }.getOrNull()

    // Battery (sticky broadcast: passing a null receiver means nothing to register/leak)
    val batteryStats = if (!refreshBatteryMetrics && previous != null) {
        previous.batPct to previous.charging
    } else {
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batPct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        batPct to charging
    }
    val rxBytes = TrafficStats.getTotalRxBytes()
    val txBytes = TrafficStats.getTotalTxBytes()

    return Stats(
        usedMb, totalMb, ramPct,
        diskStats.first, diskStats.second, diskStats.third,
        cores, load, batteryStats.first, batteryStats.second,
        rxBytes, txBytes, SystemClock.elapsedRealtime(),
    )
}

private fun withNetworkRates(current: Stats, previous: Stats?): Stats {
    if (previous == null) return current
    val elapsed = current.sampledAtMs - previous.sampledAtMs
    if (elapsed <= 0 || current.rxBytes < 0 || current.txBytes < 0 ||
        previous.rxBytes < 0 || previous.txBytes < 0 ||
        current.rxBytes < previous.rxBytes || current.txBytes < previous.txBytes
    ) return current

    fun rate(now: Long, before: Long): Long =
        (((now - before).toDouble() * 1000.0) / elapsed).toLong()

    return current.copy(
        rxBytesPerSec = rate(current.rxBytes, previous.rxBytes),
        txBytesPerSec = rate(current.txBytes, previous.txBytes),
    )
}

private fun formatRate(bytesPerSec: Long?): String {
    if (bytesPerSec == null) return "n/a"
    return when {
        bytesPerSec >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSec / (1024f * 1024f))
        bytesPerSec >= 1024L -> "%.0f KB/s".format(bytesPerSec / 1024f)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatStorage(mb: Long): String =
    if (mb >= 1024) "%.1fG".format(mb / 1024f) else "${mb}M"

private fun bar(pct: Int, width: Int = 12): String {
    val filled = (pct.coerceIn(0, 100) * width + 50) / 100
    return "█".repeat(filled) + "░".repeat(width - filled)
}

private fun loadColor(pct: Int) = when {
    pct >= 85 -> RED
    pct >= 60 -> AMBER
    else -> GREEN
}

@Composable
private fun TelemetryBanner(
    lifecycleOwner: LifecycleOwner,
    refreshSignal: Int,
    onMemoryCleanup: () -> Unit,
) {
    val ctx = LocalContext.current.applicationContext
    var stats by remember { mutableStateOf<Stats?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var cleanupFeedback by remember { mutableStateOf(false) }

    // Do not sample system telemetry while the compact panel is closed. When
    // open, sample only while the launcher is visible. Fast readings use a modest
    // interval; disk, battery, and CPU load are sampled less often.
    LaunchedEffect(lifecycleOwner, expanded, refreshSignal) {
        if (expanded) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousSample: Stats? = null
            var lastDiskRefreshMs = 0L
            var lastCpuRefreshMs = 0L
            var lastBatteryRefreshMs = 0L
            while (true) {
                val now = SystemClock.elapsedRealtime()
                val refreshDisk = previousSample == null || now - lastDiskRefreshMs >= DISK_REFRESH_MS
                val refreshCpuMetrics = previousSample == null || now - lastCpuRefreshMs >= CPU_REFRESH_MS
                val refreshBatteryMetrics = previousSample == null || now - lastBatteryRefreshMs >= BATTERY_REFRESH_MS
                val sample = withContext(Dispatchers.IO) {
                    readStats(ctx, previousSample, refreshDisk, refreshCpuMetrics, refreshBatteryMetrics)
                }
                stats = withNetworkRates(sample, previousSample)
                previousSample = sample
                if (refreshDisk) lastDiskRefreshMs = sample.sampledAtMs
                if (refreshCpuMetrics) lastCpuRefreshMs = sample.sampledAtMs
                if (refreshBatteryMetrics) lastBatteryRefreshMs = sample.sampledAtMs
                delay(TELEMETRY_REFRESH_MS)
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(1.dp, BORDER)
            .background(Color.Transparent)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("◈", style = Mono.copy(color = CYAN, fontSize = 15.sp))
            Spacer(Modifier.width(8.dp))
            BasicText(
                "SYSTEM",
                style = Mono.copy(color = GREEN),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                if (expanded) "● LIVE" else "○ IDLE",
                style = Mono.copy(color = if (expanded) GREEN else DIM, fontSize = 10.sp),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(if (expanded) "[−]" else "[+]", style = Mono.copy(color = CYAN))
        }

        if (!expanded) {
            return@Column
        }

        Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
            BasicText(
                "┌─ INFO ──────────────────────────────",
                style = Mono.copy(color = DIM, fontSize = 10.sp),
            )
            val s = stats
            if (s == null) {
                BasicText("sampling…", style = Mono.copy(color = DIM))
                return@Column
            }

            val batColor = when {
                s.charging -> GREEN
                s.batPct <= 15 -> RED
                s.batPct <= 30 -> AMBER
                else -> GREEN
            }
            BasicText(
                "BAT [${bar(s.batPct)}] ${s.batPct}%${if (s.charging) " CHG⚡" else ""}",
                style = Mono.copy(color = batColor),
            )
            BasicText(
                "RAM [${bar(s.ramPct)}] ${s.usedMb}/${s.totalMb}MB ${s.ramPct}%",
                style = Mono.copy(color = loadColor(s.ramPct)),
                modifier = Modifier.clickable {
                    cleanupFeedback = true
                    onMemoryCleanup()
                },
            )
            if (cleanupFeedback) {
                BasicText(
                    "GC requested • refreshing app stats",
                    style = Mono.copy(color = DIM, fontSize = 10.sp),
                )
                LaunchedEffect(cleanupFeedback) {
                    delay(2_000)
                    cleanupFeedback = false
                }
            }
            BasicText(
                "DISK [${bar(s.diskPct)}] ${formatStorage(s.diskTotalMb - s.diskUsedMb)} free / ${formatStorage(s.diskTotalMb)}",
                style = Mono.copy(color = loadColor(s.diskPct)),
            )

            val cpuPct = s.load?.let { ((it / s.cores) * 100).toInt().coerceIn(0, 100) }
            val loadText = s.load?.let { "%.2f".format(it) } ?: "n/a"
            BasicText(
                "CPU [${bar(cpuPct ?: 0)}] LOAD $loadText ${s.cores}c",
                style = Mono.copy(color = if (cpuPct == null) DIM else loadColor(cpuPct)),
            )
            BasicText(
                "NET ↓IN ${formatRate(s.rxBytesPerSec)} ↑OUT ${formatRate(s.txBytesPerSec)}",
                style = Mono.copy(color = CYAN),
            )
        }
    }
}

// ───────────────────────────── Main screen ─────────────────────────────

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LauncherScreen(lifecycleOwner: LifecycleOwner, homeSignal: Int) {
    val ctx = LocalContext.current
    val pm = remember(ctx) { ctx.packageManager }
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val prompt = remember(ctx) { readPromptIdentity(ctx) }
    var wallpaper by remember(ctx) { mutableStateOf<ImageBitmap?>(null) }
    var statsRefreshSignal by remember { mutableIntStateOf(0) }

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var commandOutput by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val preferences = remember(ctx) { ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var currentPath by rememberSaveable { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var commandHistory by remember(ctx) {
        mutableStateOf<List<String>>(
            preferences.getString(PREF_COMMAND_HISTORY, "").orEmpty()
                .split('\n').filter { it.isNotBlank() }.takeLast(MAX_COMMAND_HISTORY),
        )
    }
    var isGridLayout by remember(ctx) { mutableStateOf(preferences.getBoolean(PREF_GRID_LAYOUT, false)) }

    val onMemoryCleanup: () -> Unit = {
        coroutineScope.launch {
            // Request collection for this app's managed heap only. Android owns
            // process and system caches; this does not clear device-wide memory.
            withContext(Dispatchers.Default) { Runtime.getRuntime().gc() }
            statsRefreshSignal++
        }
    }

    // (Re)load the app list off the main thread. Restarting cancels any stale load.
    LaunchedEffect(reloadTick) {
        val loaded = withContext(Dispatchers.IO) { loadApps(pm) }
        apps = loaded
    }

    // Decode and rasterize the wallpaper away from the UI thread.
    LaunchedEffect(ctx) {
        wallpaper = withContext(Dispatchers.IO) { loadWallpaper(ctx) }
    }

    // Keep the list fresh when apps are installed / updated / removed.
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { reloadTick++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    // Home pressed while already on the launcher → reset search and scroll to top.
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            query = ""
            focus.clearFocus()
            listState.scrollToItem(0)
            gridState.scrollToItem(0)
        }
    }

    // A launcher is the root screen: Back never exits, it just clears search/keyboard.
    BackHandler(enabled = true) {
        query = ""
        focus.clearFocus()
    }

    val commandMode = query.trimStart().startsWith("/")
    val filtered = remember(apps, query, commandMode) {
        val q = query.trim().lowercase(Locale.ROOT)
        when {
            commandMode -> emptyList()
            q.isEmpty() -> apps
            else -> apps.filter { it.searchLabel.contains(q) }
        }
    }
    val appSections = remember(filtered) {
        filtered.groupBy { app -> app.label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#' }
            .toList()
    }
    val availableLetters = remember(appSections) { appSections.map { it.first } }
    val sectionStarts = remember(appSections) {
        buildList {
            var start = 0
            appSections.forEach { (letter, entries) ->
                add(letter to start)
                start += entries.size + 1 // one sticky letter heading per section
            }
        }
    }
    val currentLetter by remember(filtered, listState, gridState, isGridLayout) {
        derivedStateOf {
            val visibleIndex = if (isGridLayout) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
            sectionStarts.lastOrNull { it.second <= visibleIndex }?.first
                ?: sectionStarts.firstOrNull()?.first ?: '#'
        }
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        wallpaper?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp),
            )
        }
        Column(
        Modifier
            .fillMaxSize()
            .background(Color(0x9908090C))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        TelemetryBanner(
            lifecycleOwner = lifecycleOwner,
            refreshSignal = statsRefreshSignal,
            onMemoryCleanup = onMemoryCleanup,
        )
        Spacer(Modifier.height(16.dp))

        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TEXT)) { append("┌──") }
                withStyle(SpanStyle(color = GREEN)) { append(prompt.username) }
                withStyle(SpanStyle(color = DIM)) { append("@${prompt.deviceName} in ") }
                withStyle(SpanStyle(color = CYAN)) { append("VixLauncher") }
            },
            style = Mono.copy(fontSize = 14.sp),
        )

        SearchField(
            query = query,
            commandMode = commandMode,
            onQueryChange = {
                query = it
            },
            onSearch = {
                if (commandMode) {
                    val entered = query.trim()
                    coroutineScope.launch {
                        val result = executeLocalCommand(
                            ctx = ctx,
                            input = entered,
                            apps = apps,
                            currentPath = currentPath,
                            history = commandHistory,
                            onMemoryCleanup = onMemoryCleanup,
                        )
                        result.newPath?.let { currentPath = it }
                        result.newGridLayout?.let { grid ->
                            isGridLayout = grid
                            preferences.edit().putBoolean(PREF_GRID_LAYOUT, grid).apply()
                        }
                        if (result.clearHistory) {
                            commandHistory = emptyList()
                            preferences.edit().remove(PREF_COMMAND_HISTORY).apply()
                        } else if (result.success) {
                            commandHistory = (commandHistory + entered).takeLast(MAX_COMMAND_HISTORY)
                            preferences.edit().putString(PREF_COMMAND_HISTORY, commandHistory.joinToString("\n")).apply()
                        }
                        if (query.trim() == entered) {
                            query = "/"
                            commandOutput = if (result.clearOutput) emptyList() else {
                                listOf(entered to result.output.ifBlank { "(no output)" }) + commandOutput
                            }
                        }
                    }
                } else {
                    filtered.firstOrNull()?.let { launchApp(ctx, it) }
                }
            },
        )
        if (commandMode) {
            commandOutput.takeIf { it.isNotEmpty() }?.let { output ->
                Box(Modifier.weight(1f).fillMaxWidth()) { TerminalOutput(output) }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            BasicText(
                "// ${filtered.size} APPLICATIONS",
                style = Mono.copy(color = DIM, fontSize = 10.sp),
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (isGridLayout) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        appSections.forEach { (letter, sectionApps) ->
                            item(key = "section-$letter", span = { GridItemSpan(maxLineSpan) }) {
                                AlphabetSectionHeader(letter)
                            }
                            gridItems(
                                items = sectionApps,
                                key = { it.key },
                                span = { GridItemSpan(1) },
                                contentType = { "app-grid-cell" },
                            ) { app ->
                                AppGridCell(
                                    app = app,
                                    onClick = { launchApp(ctx, app) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        openAppInfo(ctx, app.packageName)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        appSections.forEach { (letter, sectionApps) ->
                            stickyHeader(key = "section-$letter") {
                                AlphabetSectionHeader(letter)
                            }
                            items(
                                items = sectionApps,
                                key = { it.key },
                                contentType = { "app-row" },
                            ) { app ->
                                AppRow(
                                    app = app,
                                    onClick = { launchApp(ctx, app) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        openAppInfo(ctx, app.packageName)
                                    },
                                )
                            }
                        }
                    }
                }
            AlphabetIndex(
                letters = ALPHABET,
                availableLetters = availableLetters,
                selectedLetter = currentLetter,
                onSelect = { letter ->
                    val target = sectionStarts.firstOrNull { it.first == letter }?.second ?: -1
                    if (target >= 0) coroutineScope.launch {
                        if (isGridLayout) gridState.scrollToItem(target) else listState.scrollToItem(target)
                    }
                },
            )
            }
        }
        }
    }
}

@Composable
private fun AlphabetSectionHeader(letter: Char) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f).height(1.dp).background(CYAN.copy(alpha = 0.65f)))
        BasicText(
            text = letter.toString(),
            style = Mono.copy(color = CYAN, fontSize = 11.sp),
            modifier = Modifier.padding(horizontal = 9.dp),
        )
        Spacer(Modifier.weight(1f).height(1.dp).background(CYAN.copy(alpha = 0.65f)))
    }
}

@Composable
private fun AlphabetIndex(
    letters: List<Char>,
    availableLetters: List<Char>,
    selectedLetter: Char,
    onSelect: (Char) -> Unit,
) {
    val latestOnSelect = rememberUpdatedState(onSelect)
    val latestAvailableLetters = rememberUpdatedState(availableLetters)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .offset(x = 6.dp)
            .pointerInput(letters) {
                fun selectAt(y: Float) {
                    if (letters.isEmpty() || size.height == 0) return
                    val fraction = (y / size.height).coerceIn(0f, 0.9999f)
                    val targetIndex = (fraction * letters.size).toInt()
                    val nearest = latestAvailableLetters.value.minByOrNull {
                        kotlin.math.abs(letters.indexOf(it) - targetIndex)
                    }
                    if (nearest != null) latestOnSelect.value(nearest)
                }
                detectVerticalDragGestures(
                    onDragStart = { selectAt(it.y) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        selectAt(change.position.y)
                    },
                )
            }
            .pointerInput(letters) {
                detectTapGestures { position ->
                    if (letters.isNotEmpty() && latestAvailableLetters.value.isNotEmpty() && size.height > 0) {
                        val fraction = (position.y / size.height).coerceIn(0f, 0.9999f)
                        val targetIndex = (fraction * letters.size).toInt()
                        val nearest = latestAvailableLetters.value.minByOrNull {
                            kotlin.math.abs(letters.indexOf(it) - targetIndex)
                        }
                        if (nearest != null) latestOnSelect.value(nearest)
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val selected = letter == selectedLetter
            BasicText(
                text = letter.toString(),
                style = Mono.copy(
                    color = when {
                        selected -> GREEN
                        letter in availableLetters -> DIM
                        else -> DIM.copy(alpha = 0.28f)
                    },
                    fontSize = if (selected) 12.sp else 10.sp,
                    lineHeight = 14.sp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            )
        }
    }
}

@Composable
private fun TerminalOutput(output: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 8.dp)
            .border(1.dp, BORDER)
            .background(Color(0x66101318))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        output.forEachIndexed { index, (command, result) ->
            BasicText(
                "┌─ ${if (index == 0) "LATEST" else "PREVIOUS"} COMMAND: $command ─",
                style = Mono.copy(color = DIM, fontSize = 10.sp),
            )
            BasicText(terminalAnnotatedText(result), style = Mono.copy(color = CYAN, fontSize = 11.sp))
            if (index != output.lastIndex) Spacer(Modifier.height(7.dp))
        }
    }
}

private fun terminalAnnotatedText(output: String) = buildAnnotatedString {
    var index = 0
    while (index < output.length) {
        val command = HELP_COMMAND_TOKENS.firstOrNull { token ->
            output.startsWith(token, index) &&
                (index + token.length == output.length ||
                    !output[index + token.length].isLetterOrDigit() && output[index + token.length] != '_')
        }
        if (command == null) {
            append(output[index])
            index++
        } else {
            withStyle(SpanStyle(color = GREEN, fontWeight = FontWeight.Bold)) { append(command) }
            index += command.length
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    commandMode: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = Mono.copy(color = TEXT, fontSize = 15.sp),
        cursorBrush = SolidColor(GREEN),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = if (commandMode) "└─CMD>" else "└──>",
                    style = Mono.copy(color = if (commandMode) CYAN else GREEN, fontSize = 14.sp),
                )
                Spacer(Modifier.width(7.dp))
                inner()
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("›", style = Mono.copy(color = GREEN, fontSize = 16.sp))
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = app.label,
            maxLines = 1,
            style = TextStyle(color = TEXT, fontSize = 16.sp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridCell(
    app: AppEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var icon by remember(app.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(context, app.key) {
        icon = withContext(Dispatchers.IO) { loadAppIcon(context, app) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.let {
            Image(
                bitmap = it,
                contentDescription = "${app.label} icon",
                modifier = Modifier.size(42.dp),
            )
        } ?: Spacer(Modifier.size(42.dp))
        BasicText(
            text = app.label,
            maxLines = 1,
            style = TextStyle(color = TEXT, fontSize = 12.sp),
        )
    }
}
