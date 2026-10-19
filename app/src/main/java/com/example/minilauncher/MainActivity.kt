package com.example.minilauncher

import android.app.ActivityManager
import android.app.WallpaperManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import android.graphics.Bitmap
import android.graphics.Canvas
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

private fun openAppInfo(ctx: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
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

private fun readStats(ctx: Context, previous: Stats?, refreshDisk: Boolean): Stats {
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
    val load = runCatching {
        File("/proc/loadavg").readText().substringBefore(' ').toFloat()
    }.getOrNull()

    // Battery (sticky broadcast: passing a null receiver means nothing to register/leak)
    val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val batPct = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    val rxBytes = TrafficStats.getTotalRxBytes()
    val txBytes = TrafficStats.getTotalTxBytes()

    return Stats(
        usedMb, totalMb, ramPct,
        diskStats.first, diskStats.second, diskStats.third,
        cores, load, batPct, charging,
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
private fun TelemetryBanner(lifecycleOwner: LifecycleOwner) {
    val ctx = LocalContext.current.applicationContext
    var stats by remember { mutableStateOf<Stats?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Do not sample system telemetry while the compact panel is closed. When
    // open, sample only while the launcher is visible and at a modest interval.
    LaunchedEffect(lifecycleOwner, expanded) {
        if (expanded) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousSample: Stats? = null
            var lastDiskRefreshMs = 0L
            while (true) {
                val now = SystemClock.elapsedRealtime()
                val refreshDisk = previousSample == null || now - lastDiskRefreshMs >= 30_000L
                val sample = withContext(Dispatchers.IO) {
                    readStats(ctx, previousSample, refreshDisk)
                }
                stats = withNetworkRates(sample, previousSample)
                previousSample = sample
                if (refreshDisk) lastDiskRefreshMs = sample.sampledAtMs
                delay(1_000)
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
            )
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

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
        }
    }

    // A launcher is the root screen: Back never exits, it just clears search/keyboard.
    BackHandler(enabled = true) {
        query = ""
        focus.clearFocus()
    }

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) apps else apps.filter { it.searchLabel.contains(q) }
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
    val currentLetter by remember(filtered, listState) {
        derivedStateOf {
            sectionStarts.lastOrNull { it.second <= listState.firstVisibleItemIndex }?.first
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
        TelemetryBanner(lifecycleOwner)
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
            onQueryChange = { query = it },
            onSearch = { filtered.firstOrNull()?.let { launchApp(ctx, it) } },
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            "// ${filtered.size} APPLICATIONS",
            style = Mono.copy(color = DIM, fontSize = 10.sp),
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                appSections.forEach { (letter, sectionApps) ->
                    stickyHeader(key = "section-$letter") {
                        BasicText(
                            text = "── $letter ──",
                            style = Mono.copy(color = CYAN, fontSize = 10.sp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        )
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
            AlphabetIndex(
                letters = ALPHABET,
                availableLetters = availableLetters,
                selectedLetter = currentLetter,
                onSelect = { letter ->
                    val target = sectionStarts.firstOrNull { it.first == letter }?.second ?: -1
                    if (target >= 0) coroutineScope.launch { listState.scrollToItem(target) }
                },
            )
        }
        }
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
            .width(22.dp)
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
                    fontSize = if (selected) 11.sp else 9.sp,
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
private fun SearchField(
    query: String,
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
                BasicText("└──>", style = Mono.copy(color = GREEN, fontSize = 14.sp))
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
