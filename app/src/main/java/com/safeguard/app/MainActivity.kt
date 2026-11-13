package com.safeguard.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
        setContent {
            var themeMode by remember { mutableStateOf(0) } // 0=System 1=Light 2=Dark
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
                    Dashboard(
                        themeMode = themeMode,
                        onThemeChange = { themeMode = (themeMode + 1) % 3 }
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

private fun randomSentence(count: Int): String =
    List(count) { WORDS.random() }.joinToString(" ")

@Composable
fun Dashboard(themeMode: Int, onThemeChange: () -> Unit) {
    val context = LocalContext.current
    var bangla by remember { mutableStateOf(false) }
    var protectionOn by remember { mutableStateOf(SafeGuardVpnService.running) }
    var showConfirm by remember { mutableStateOf(false) }
    var sentence by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableStateOf(TIME_LIMIT_SECONDS) }

    LaunchedEffect(Unit) {
        while (true) {
            protectionOn = SafeGuardVpnService.running
            kotlinx.coroutines.delay(1000)
        }
    }

    // Countdown while the dialog is open
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
        val intent = Intent(context, SafeGuardVpnService::class.java)
        context.startForegroundService(intent)
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

    val title = if (bangla) "সেইফগার্ড" else "SafeGuard"
    val statusOn = if (bangla) "VPN চালু" else "VPN ON"
    val statusOff = if (bangla) "VPN বন্ধ" else "VPN OFF"
    val blockedLabel = if (bangla) "আজ ব্লক করা হয়েছে" else "Blocked today"
    val totalLabel = if (bangla) "মোট ব্লক" else "Total blocked"
    val toggleOn = if (bangla) "VPN চালু করুন" else "Turn VPN ON"
    val toggleOff = if (bangla) "VPN বন্ধ করুন" else "Turn VPN OFF"
    val langButton = if (bangla) "English" else "বাংলা"
    val themeLabel = when (themeMode) {
        1 -> if (bangla) "থিম: লাইট" else "Theme: Light"
        2 -> if (bangla) "থিম: ডার্ক" else "Theme: Dark"
        else -> if (bangla) "থিম: সিস্টেম" else "Theme: System"
    }
    val note = if (bangla)
        "VPN বন্ধ করতে ২ মিনিটের মধ্যে লম্বা বাক্য হুবহু টাইপ করতে হবে। কপি-পেস্ট চলবে না।"
    else
        "To turn the VPN off you must type a long sentence exactly within 2 minutes. Pasting is blocked."
    val dialogTitle = if (bangla) "বাক্যটি টাইপ করুন" else "Type this sentence"
    val confirmText = if (bangla) "বন্ধ করুন" else "Turn off"
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
                    // Plain Text (not selectable) so it cannot be copied
                    Text(sentence, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { new ->
                            // Reject pastes: only 1-2 characters may be added at once
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
                            stopVpnService()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { bangla = !bangla }) { Text(langButton) }
        }

        OutlinedButton(
            onClick = onThemeChange,
            modifier = Modifier.fillMaxWidth()
        ) { Text(themeLabel) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (protectionOn) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (protectionOn) statusOn else statusOff,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(blockedLabel, SafeGuardVpnService.blockedCount.toString(), Modifier.weight(1f))
            StatCard(totalLabel, SafeGuardVpnService.blockedCount.toString(), Modifier.weight(1f))
        }

        Button(
            onClick = {
                if (protectionOn) {
                    sentence = randomSentence(SENTENCE_WORDS)
                    typed = ""
                    showConfirm = true
                } else {
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) {
                        vpnPermissionLauncher.launch(prepare)
                    } else {
                        startVpnService()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (protectionOn) toggleOff else toggleOn)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(note, fontSize = 13.sp)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 13.sp)
        }
    }
}
