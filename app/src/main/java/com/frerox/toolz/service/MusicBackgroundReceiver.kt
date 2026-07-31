package com.frerox.toolz.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MusicBackgroundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("MusicBackground", "Received action: $action")
        
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED || 
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.MY_PACKAGE_REPLACED") {
            
            val serviceIntent = Intent(context, MusicPlayerService::class.java)
            try {
                // Try to start the service to warm it up
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("MusicBackground", "Failed to start MusicPlayerService: ${e.message}")
            }
        }
    }
}
