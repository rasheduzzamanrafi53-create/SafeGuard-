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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun Dashboard(themeMode: Int, onThemeChange: () -> Unit) {
    val context = LocalContext.current
    var bangla by remember { mutableStateOf(false) }
    var protectionOn by remember { mutableStateOf(SafeGuardVpnService.running) }

    // Re-check the real VPN state every second
    LaunchedEffect(Unit) {
        while (true) {
            protectionOn = SafeGuardVpnService.running
            kotlinx.coroutines.delay(1000)
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
        "এটি একটি পরীক্ষামূলক সংস্করণ। VPN চালু হয়, কিন্তু সাইট ব্লকিং এখনো সক্রিয় নয়।"
    else
        "This is a preview version. The VPN starts, but site blocking is not active yet."

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
            StatCard(blockedLabel, "0", Modifier.weight(1f))
            StatCard(totalLabel, "0", Modifier.weight(1f))
        }

        Button(
            onClick = {
                if (protectionOn) {
                    stopVpnService()
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
