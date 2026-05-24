package com.frerox.toolz.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Modern VpnService boilerplate for Android 14+ (16KB page-size alignment).
 */
class ToolzVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnJob: Job? = null

    companion object {
        private const val TAG = "ToolzVpnService"
        // 16KB alignment for modern Android requirements
        private const val BUFFER_SIZE = 16384 
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_VPN" -> {
                val config = intent.getStringExtra("EXTRA_OVPN_CONFIG")
                startVpn(config)
            }
            "STOP_VPN" -> {
                stopVpn()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn(config: String?) {
        Log.d(TAG, "Starting VPN with config: ${config?.take(20)}...")
        
        // Boilerplate Builder
        val builder = Builder()
            .setSession("ToolzVpn")
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1") // Force secure DNS
            .addDnsServer("8.8.8.8")
            .setMtu(1400) // Lower MTU for better compatibility
            .setBlocking(false)

        try {
            vpnInterface = builder.establish()
            Log.i(TAG, "VPN Interface established")
            
            // Start the processing loop
            runVpnLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
        }
    }

    private fun runVpnLoop() {
        vpnJob = serviceScope.launch {
            val fd = vpnInterface?.fileDescriptor ?: return@launch
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)
            
            // 16KB Aligned Direct ByteBuffer
            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
            
            try {
                while (isActive) {
                    val readBytes = input.read(buffer.array())
                    if (readBytes > 0) {
                        // Simulated packet processing/forwarding
                        // In a real implementation, you would encrypt/decrypt and send to a remote server
                        // For this tool, we are ensuring the loop stays active and handles the stream
                        Log.v(TAG, "Forwarding $readBytes bytes through TUN")
                        
                        // Loop back packets or process them if needed
                        // For now, we just drain the input to keep the interface alive
                        buffer.clear()
                    } else if (readBytes == 0) {
                        delay(10)
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "VPN loop error", e)
            } finally {
                input.close()
                output.close()
            }
        }
    }

    private fun stopVpn() {
        vpnJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        Log.i(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopVpn()
    }
}
