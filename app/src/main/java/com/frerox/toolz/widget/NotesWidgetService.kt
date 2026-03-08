package com.frerox.toolz.widget

import android.content.Intent
import android.widget.RemoteViewsService

class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NotesRemoteViewsFactory(this.applicationContext)
    }
}
