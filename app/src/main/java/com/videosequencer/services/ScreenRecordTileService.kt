package com.videosequencer.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.videosequencer.MainActivity

class ScreenRecordTileService : TileService() {

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTileState()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val filter = IntentFilter().apply {
            addAction(ScreenRecordService.ACTION_RECORDING_STARTED)
            addAction(ScreenRecordService.ACTION_RECORDING_FINISHED)
            addAction(ScreenRecordService.ACTION_RECORDING_ERROR)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, filter)
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        } catch (e: Exception) { }
    }

    override fun onClick() {
        super.onClick()

        if (ScreenRecordService.isRecording) {
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP_RECORDING
            }
            startService(stopIntent)
            updateTileState()
        } else {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_START_SCREEN_RECORD", true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(mainIntent)
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        if (ScreenRecordService.isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Finish Record"
            tile.subtitle = "Recording active"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Screen Record"
            tile.subtitle = "Tap to record"
        }
        tile.updateTile()
    }
}
