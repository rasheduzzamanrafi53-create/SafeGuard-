package com.safeguard.app

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlockStore.init(this)
        setContent {
            var themeMode by remember { mutableStateOf(0) }
            var bangla by remember { mutableStateOf(false) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            val colors = if (dark) {
                darkColorScheme(primary = Color(0xFF4DB6AC))
            } else {
                lightColorScheme(primary = Color(0xFF00796B))
            }
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(
                        themeMode = themeMode,
                        onThemeChange = { themeMode = (themeMode + 1) % 3 },
                        bangla = bangla,
                        onLangChange = { bangla = !bangla }
                    )
                }
            }
        }
    }
}

private val WORDS = listOf(
    "river", "mango", "tiger", "cloud", "stone", "candle", "window", "garden",
    "pencil", "silver", "market", "bridge", "forest", "orange", "thunder",
    "button", "carpet", "island", "rocket", "violin", "lantern", "pepper",
    "ladder", "magnet", "pillow", "jungle", "anchor", "bottle", "dragon", "saddle"
)

private const val SENTENCE_WORDS = 12
private const val TIME_LIMIT_SECONDS = 120
private const val PAUSE_MINUTES = 10L

private fun randomSentence(count: Int): String =
    List(count) { WORDS.random() }.joinToString(" ")

private fun appLabel(context: Context, pkg: String): String {
    return try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}

@Composable
fun App(
    themeMode: Int,
    onThemeChange: () -> Unit,
    bangla: Boolean,
    onLangChange: () -> Unit
) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(0) } // 0 Home, 1 Blocklist, 2 Settings
    var protectionOn by remember { mutableStateOf(SafeGuardVpnService.running) }
    var blocked by remember { mutableStateOf(SafeGuardVpnService.blockedCount) }
    var showConfirm by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf(0) } // 0 VPN off, 1 pause, 2 remove item
    var pendingType by remember { mutableStateOf("") }
    var pendingValue by remember { mutableStateOf("") }
    var removeTick by remember { mutableStateOf(0) }
    var sentence by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableStateOf(TIME_LIMIT_SECONDS) }

    LaunchedEffect(Unit) {
        while (true) {
            protectionOn = SafeGuardVpnService.running
            blocked = SafeGuardVpnService.blockedCount
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(showConfirm, sentence) {
        if (showConfirm) {
            secondsLeft = TIME_LIMIT_SECONDS
            while (secondsLeft > 0) {
                kotlinx.coroutines.delay(1000)
                secondsLeft -= 1
            }
            showConfirm = false
            typed = ""
        }
    }

    fun startVpnService() {
        context.startForegroundService(Intent(context, SafeGuardVpnService::class.java))
    }

    fun stopVpnService() {
        val intent = Intent(context, SafeGuardVpnService::class.java)
        intent.action = SafeGuardVpnService.ACTION_STOP
        context.startService(intent)
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpnService()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val dialogTitle = if (bangla) "বাক্যটি টাইপ করুন" else "Type this sentence"
    val confirmText = if (bangla) "নিশ্চিত করুন" else "Confirm"
    val cancelText = if (bangla) "বাতিল" else "Cancel"
    val hint = if (bangla) "এখানে টাইপ করুন" else "Type here"
    val timeText = if (bangla) "বাকি সময়: $secondsLeft সেকেন্ড" else "Time left: $secondsLeft s"

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(dialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(timeText, fontSize = 14.sp)
                    Text(sentence, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { new ->
                            if (new.length - typed.length <= 2) typed = new
                        },
                        label = { Text(hint) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (typed.trim() == sentence) {
                            showConfirm = false
                            typed = ""
                            when (pendingAction) {
                                0 -> stopVpnService()
                                1 -> SafeGuardAccessibilityService.pausedUntil =
                                    System.currentTimeMillis() + PAUSE_MINUTES * 60 * 1000
                                else -> {
                                    BlockStore.remove(pendingType, pendingValue)
                                    removeTick += 1
                                }
                            }
                        } else {
                            sentence = randomSentence(SENTENCE_WORDS)
                            typed = ""
                        }
                    }
                ) { Text(confirmText) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false; typed = "" }) { Text(cancelText) }
            }
        )
    }

    fun askSentence(action: Int) {
        pendingAction = action
        sentence = randomSentence(SENTENCE_WORDS)
        typed = ""
        showConfirm = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (screen) {
                0 -> HomeScreen(
                    bangla = bangla,
                    protectionOn = protectionOn,
                    blocked = blocked,
                    onToggle = {
                        if (protectionOn) {
                            askSentence(0)
                        } else {
                            val prepare = VpnService.prepare(context)
                            if (prepare != null) {
                                vpnPermissionLauncher.launch(prepare)
                            } else {
                                startVpnService()
                            }
                        }
                    }
                )
                1 -> BlocklistScreen(
                    bangla = bangla,
                    externalTick = removeTick,
                    onRequestRemove = { type, value ->
                        pendingType = type
                        pendingValue = value
                        askSentence(2)
                    }
                )
                else -> SettingsScreen(
                    bangla = bangla,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onLangChange = onLangChange,
                    onPause = { askSentence(1) }
                )
            }
        }

        NavigationBar {
            NavigationBarItem(
                selected = screen == 0,
                onClick = { screen = 0 },
                icon = { Text(if (screen == 0) "●" else "○") },
                label = { Text(if (bangla) "হোম" else "Home") }
            )
            NavigationBarItem(
                selected = screen == 1,
                onClick = { screen = 1 },
                icon = { Text(if (screen == 1) "●" else "○") },
                label = { Text(if (bangla) "ব্লকলিস্ট" else "Blocklist") }
            )
            NavigationBarItem(
                selected = screen == 2,
                onClick = { screen = 2 },
                icon = { Text(if (screen == 2) "●" else "○") },
                label = { Text(if (bangla) "সেটিংস" else "Settings") }
            )
        }
    }
}

@Composable
fun HomeScreen(
    bangla: Boolean,
    protectionOn: Boolean,
    blocked: Int,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("SafeGuard", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (protectionOn) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (protectionOn) {
                        if (bangla) "সুরক্ষা চালু" else "Protected"
                    } else {
                        if (bangla) "সুরক্ষা বন্ধ" else "Not protected"
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(blocked.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(if (bangla) "ব্লক করা হয়েছে" else "Blocked", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                if (protectionOn) {
                    if (bangla) "বন্ধ করুন" else "Turn off"
                } else {
                    if (bangla) "চালু করুন" else "Turn on"
                },
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun BlocklistScreen(
    bangla: Boolean,
    externalTick: Int,
    onRequestRemove: (String, String) -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) } // 0 keywords, 1 websites, 2 apps
    var input by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }

    val type = when (tab) {
        0 -> "keywords"
        1 -> "websites"
        else -> "apps"
    }

    val items = remember(tab, tick, externalTick) {
        when (tab) {
            0 -> BlockStore.keywords
            1 -> BlockStore.websites
            else -> BlockStore.apps
        }.sorted()
    }

    if (showPicker) {
        val pm = context.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = remember {
            pm.queryIntentActivities(launch, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != context.packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(if (bangla) "অ্যাপ বেছে নিন" else "Choose an app") },
            text = {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(apps) { item ->
                        TextButton(
                            onClick = {
                                BlockStore.add("apps", item.first)
                                tick += 1
                                showPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(item.second) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(if (bangla) "বন্ধ" else "Close")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (bangla) "ব্লকলিস্ট" else "Blocklist",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = listOf(
                if (bangla) "শব্দ" else "Keywords",
                if (bangla) "ওয়েবসাইট" else "Websites",
                if (bangla) "অ্যাপ" else "Apps"
            )
            for (i in 0..2) {
                if (tab == i) {
                    Button(
                        onClick = { tab = i; input = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text(names[i], fontSize = 12.sp) }
                } else {
                    OutlinedButton(
                        onClick = { tab = i; input = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text(names[i], fontSize = 12.sp) }
                }
            }
        }

        if (tab == 2) {
            Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (bangla) "অ্যাপ যোগ করুন" else "Add app")
            }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = {
                    Text(
                        if (tab == 0) {
                            if (bangla) "শব্দ লিখুন (যেমন kiss)" else "Word (e.g. kiss)"
                        } else {
                            if (bangla) "ডোমেইন (যেমন example.com)" else "Domain (e.g. example.com)"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    BlockStore.add(type, input)
                    input = ""
                    tick += 1
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (bangla) "যোগ করুন" else "Add") }
        }

        Text(
            if (bangla) "মুছতে লম্বা বাক্য টাইপ করতে হবে।" else "Removing needs the long sentence.",
            fontSize = 12.sp
        )

        for (value in items) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (tab == 2) appLabel(context, value) else value,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onRequestRemove(type, value) }) {
                        Text(if (bangla) "মুছুন" else "Remove")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    bangla: Boolean,
    themeMode: Int,
    onThemeChange: () -> Unit,
    onLangChange: () -> Unit,
    onPause: () -> Unit
) {
    val context = LocalContext.current

    val themeLabel = when (themeMode) {
        1 -> if (bangla) "থিম: লাইট" else "Theme: Light"
        2 -> if (bangla) "থিম: ডার্ক" else "Theme: Dark"
        else -> if (bangla) "থিম: সিস্টেম" else "Theme: System"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (bangla) "সেটিংস" else "Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedButton(onClick = onLangChange, modifier = Modifier.fillMaxWidth()) {
            Text(if (bangla) "English" else "বাংলা")
        }

        OutlinedButton(onClick = onThemeChange, modifier = Modifier.fillMaxWidth()) {
            Text(themeLabel)
        }

        OutlinedButton(
            onClick = {
                val admin = ComponentName(context, SafeGuardAdminReceiver::class.java)
                val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                context.startActivity(i)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (bangla) "আনইনস্টল সুরক্ষা চালু করুন" else "Enable Uninstall Protection") }

        OutlinedButton(
            onClick = {
                val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (bangla) "সেটিংস সুরক্ষা চালু করুন" else "Enable Settings Protection") }

        OutlinedButton(
            onClick = {
                val i = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                )
                context.startActivity(i)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (bangla) "বুট কাউন্টডাউনের অনুমতি দিন" else "Allow boot countdown") }

        OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
            Text(if (bangla) "জরুরি বিরতি (১০ মিনিট)" else "Emergency pause (10 min)")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (bangla)
                "বন্ধ, বিরতি বা তালিকা থেকে মুছতে ২ মিনিটের মধ্যে লম্বা বাক্য হুবহু টাইপ করতে হবে। কপি-পেস্ট চলবে না।"
            else
                "Turning off, pausing or removing from the list requires typing a long sentence exactly within 2 minutes. Pasting is blocked.",
            fontSize = 13.sp
        )
    }
}
