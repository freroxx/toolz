package com.frerox.toolz.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.frerox.toolz.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class ClipboardTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("navigate_to", "clipboard")
        }
        startActivityAndCollapse(intent)
    }
}
