package com.safeguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SafeGuardVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private var pool: ExecutorService? = null
    @Volatile private var active = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (tunnel != null) return
        val pfd = Builder()
            .setSession("SafeGuard")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.1")
            .addRoute("10.0.0.1", 32)
            .establish() ?: return
        tunnel = pfd
        active = true
        running = true
        pool = Executors.newFixedThreadPool(8)
        Thread { runLoop(pfd) }.start()
    }

    private fun runLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (active) {
                val len = input.read(buf)
                if (len < 0) break
                if (len == 0) continue
                handlePacket(buf.copyOf(len), output)
            }
        } catch (e: Exception) {
            // tunnel closed
        }
    }

    private fun handlePacket(p: ByteArray, out: FileOutputStream) {
        if (p.size < 28) return
        if ((p[0].toInt() shr 4) != 4) return
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p[9].toInt() != 17) return
        if (p.size < ihl + 8) return
        val srcPort = u16(p, ihl)
        val dstPort = u16(p, ihl + 2)
        if (dstPort != 53) return
        val dnsStart = ihl + 8
        if (p.size - dnsStart < 12) return
        val dns = p.copyOfRange(dnsStart, p.size)
        val srcIp = p.copyOfRange(12, 16)
        val dstIp = p.copyOfRange(16, 20)

        val q = parseQuestion(dns) ?: return
        val name = q.first
        val qEnd = q.second

        if (isBlocked(name)) {
            blockedCount++
            val reply = nxdomain(dns, qEnd)
            write(out, buildPacket(dstIp, srcIp, 53, srcPort, reply))
        } else {
            pool?.execute { forward(dns, dstIp, srcIp, srcPort, out) }
        }
    }

    private fun forward(
        dns: ByteArray, dstIp: ByteArray, srcIp: ByteArray, srcPort: Int, out: FileOutputStream
    ) {
        for (server in UPSTREAM_SERVERS) {
            val socket = DatagramSocket()
            try {
                protect(socket)
                socket.soTimeout = 2000
                val addr = InetAddress.getByName(server)
                socket.send(DatagramPacket(dns, dns.size, addr, 53))
                val rb = ByteArray(2048)
                val rp = DatagramPacket(rb, rb.size)
                socket.receive(rp)
                val reply = rb.copyOf(rp.length)
                write(out, buildPacket(dstIp, srcIp, 53, srcPort, reply))
                return
            } catch (e: Exception) {
                // try the next server
            } finally {
                socket.close()
            }
        }
    }

    private fun write(out: FileOutputStream, packet: ByteArray) {
        try {
            synchronized(out) { out.write(packet) }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun parseQuestion(dns: ByteArray): Pair<String, Int>? {
        var i = 12
        val sb = StringBuilder()
        while (true) {
            if (i >= dns.size) return null
            val len = dns[i].toInt() and 0xFF
            if (len == 0) {
                i++
                break
            }
            if ((len and 0xC0) != 0) return null
            if (i + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, i + 1, len, Charsets.ISO_8859_1))
            i += len + 1
        }
        val qEnd = i + 4
        if (qEnd > dns.size) return null
        return Pair(sb.toString().lowercase(), qEnd)
    }

    private fun nxdomain(dns: ByteArray, qEnd: Int): ByteArray {
        val r = dns.copyOfRange(0, qEnd)
        r[2] = 0x81.toByte()
        r[3] = 0x83.toByte()
        r[4] = 0
        r[5] = 1
        for (k in 6..11) r[k] = 0
        return r
    }

    private fun isBlocked(domain: String): Boolean {
        if (WHITELIST.any { domain == it || domain.endsWith(".$it") }) return false
        for (kw in KEYWORDS) {
            if (domain.contains(kw)) return true
        }
        var d = domain
        while (true) {
            if (BLOCKED_DOMAINS.contains(d)) return true
            val dot = d.indexOf('.')
            if (dot < 0) return false
            d = d.substring(dot + 1)
        }
    }

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun buildPacket(
        srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray
    ): ByteArray {
        val total = 20 + 8 + payload.size
        val pkt = ByteArray(total)
        pkt[0] = 0x45
        pkt[2] = (total shr 8).toByte()
        pkt[3] = total.toByte()
        pkt[6] = 0x40
        pkt[8] = 64
        pkt[9] = 17
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(dstIp, 0, pkt, 16, 4)
        var sum = 0
        var k = 0
        while (k < 20) {
            sum += u16(pkt, k)
            k += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val cs = sum.inv() and 0xFFFF
        pkt[10] = (cs shr 8).toByte()
        pkt[11] = cs.toByte()
        pkt[20] = (srcPort shr 8).toByte()
        pkt[21] = srcPort.toByte()
        pkt[22] = (dstPort shr 8).toByte()
        pkt[23] = dstPort.toByte()
        val udpLen = 8 + payload.size
        pkt[24] = (udpLen shr 8).toByte()
        pkt[25] = udpLen.toByte()
        System.arraycopy(payload, 0, pkt, 28, payload.size)
        return pkt
    }

    private fun stopVpn() {
        active = false
        pool?.shutdownNow()
        pool = null
        tunnel?.close()
        tunnel = null
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        active = false
        pool?.shutdownNow()
        tunnel?.close()
        tunnel = null
        running = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "SafeGuard protection",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeGuard")
            .setContentText("Protection is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.safeguard.app.STOP"
        private const val CHANNEL_ID = "safeguard_protection"
        private const val NOTIFICATION_ID = 1
        private val UPSTREAM_SERVERS = listOf("1.1.1.1", "8.8.8.8")

        @Volatile
        var running: Boolean = false

        @Volatile
        var blockedCount: Int = 0

        private val KEYWORDS = listOf("porn", "hentai", "xvideos", "xnxx", "xhamster")

        private val WHITELIST = setOf(
            "google.com", "youtube.com", "wikipedia.org", "facebook.com",
            "github.com", "microsoft.com", "apple.com", "amazon.com"
        )

        private val BLOCKED_DOMAINS = setOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
            "youporn.com", "spankbang.com", "eporner.com", "tube8.com", "beeg.com",
            "brazzers.com", "chaturbate.com", "stripchat.com", "bongacams.com",
            "livejasmin.com", "cam4.com", "motherless.com", "thumbzilla.com",
            "tnaflix.com", "hclips.com", "txxx.com", "nhentai.net", "e-hentai.org",
            "onlyfans.com", "fapello.com", "erome.com", "sxyprn.com", "4tube.com"
        )
    }
}
