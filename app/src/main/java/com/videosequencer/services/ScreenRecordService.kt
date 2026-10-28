package com.videosequencer.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.videosequencer.MainActivity
import com.videosequencer.R
import java.io.File
import java.io.FileInputStream

class ScreenRecordService : Service() {

    companion object {
        const val TAG = "ScreenRecordService"
        const val CHANNEL_ID = "ScreenRecordChannel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START_RECORDING = "com.videosequencer.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.videosequencer.ACTION_STOP_RECORDING"

        const val ACTION_RECORDING_STARTED = "com.videosequencer.ACTION_RECORDING_STARTED"
        const val ACTION_RECORDING_FINISHED = "com.videosequencer.ACTION_RECORDING_FINISHED"
        const val ACTION_RECORDING_ERROR = "com.videosequencer.ACTION_RECORDING_ERROR"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA_INTENT = "extra_data_intent"
        const val EXTRA_RECORDED_URI = "extra_recorded_uri"

        var isRecording = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputFile: File? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped")
            if (isRecording) {
                stopAndSaveRecording()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val dataIntent: Intent? = try {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA_INTENT) as? Intent
                } catch (e: Exception) {
                    null
                }

                if (resultCode != 0 && dataIntent != null) {
                    startForegroundServiceWithNotification()
                    startRecording(resultCode, dataIntent)
                } else {
                    Log.e(TAG, "Invalid resultCode or dataIntent")
                    LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_RECORDING_ERROR))
                    stopSelf()
                }
            }
            ACTION_STOP_RECORDING -> {
                stopAndSaveRecording()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification("Screen recording in progress...", isRecording = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(contentText: String, isRecording: Boolean): Notification {
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Editing Screen Recorder")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPendingIntent)
            .setOngoing(isRecording)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isRecording) {
            builder.addAction(R.drawable.ic_close_24, "Finish & Save", stopPendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification during screen recording"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun configureAndPrepareRecorder(
        recorder: MediaRecorder,
        outputFilePath: String,
        width: Int,
        height: Int,
        frameRate: Int,
        bitRate: Int,
        enableAudio: Boolean
    ): Boolean {
        return try {
            recorder.reset()

            var audioSet = false
            if (enableAudio) {
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    audioSet = true
                } catch (e: Exception) {
                    Log.w(TAG, "Audio source MIC failed, proceeding without audio", e)
                }
            }

            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audioSet) {
                try {
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                } catch (e: Exception) {
                    Log.w(TAG, "AAC audio encoder failed", e)
                }
            }

            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(frameRate)
            recorder.setVideoEncodingBitRate(bitRate)
            recorder.setOutputFile(outputFilePath)

            recorder.prepare()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed configuring recorder for ${width}x${height} @ ${frameRate}fps (audio=$enableAudio)", e)
            false
        }
    }

    private fun startRecording(resultCode: Int, dataIntent: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, dataIntent)

            if (mediaProjection == null) {
                Log.e(TAG, "getMediaProjection returned null")
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_RECORDING_ERROR))
                stopSelf()
                return
            }

            mediaProjection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val screenWidth = (metrics.widthPixels / 2) * 2
            val screenHeight = (metrics.heightPixels / 2) * 2
            val density = metrics.densityDpi
            val isPortrait = screenHeight > screenWidth

            val fileName = "ScreenRecord_${System.currentTimeMillis()}.mp4"
            outputFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)
            val outputPath = outputFile!!.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            val recorder = mediaRecorder!!

            val hasMicPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val target4KWidth = if (isPortrait) 2160 else 3840
            val target4KHeight = if (isPortrait) 3840 else 2160

            val configsToTry = mutableListOf<Triple<Int, Int, Pair<Int, Int>>>()

            if (hasMicPermission) {
                configsToTry.add(Triple(target4KWidth, target4KHeight, 60 to 35_000_000))
                configsToTry.add(Triple(screenWidth, screenHeight, 60 to 20_000_000))
                configsToTry.add(Triple(screenWidth, screenHeight, 30 to 12_000_000))
                configsToTry.add(Triple(if (isPortrait) 1080 else 1920, if (isPortrait) 1920 else 1080, 30 to 8_000_000))
            }

            configsToTry.add(Triple(target4KWidth, target4KHeight, 60 to 35_000_000))
            configsToTry.add(Triple(screenWidth, screenHeight, 60 to 20_000_000))
            configsToTry.add(Triple(screenWidth, screenHeight, 30 to 12_000_000))
            configsToTry.add(Triple(if (isPortrait) 1080 else 1920, if (isPortrait) 1920 else 1080, 30 to 8_000_000))

            var preparedWidth = screenWidth
            var preparedHeight = screenHeight
            var success = false

            for (i in configsToTry.indices) {
                val (w, h, fpsAndBitrate) = configsToTry[i]
                val (fps, bitrate) = fpsAndBitrate
                val tryAudio = hasMicPermission && i < (configsToTry.size / 2)
                val evenW = (w / 2) * 2
                val evenH = (h / 2) * 2

                if (configureAndPrepareRecorder(recorder, outputPath, evenW, evenH, fps, bitrate, tryAudio)) {
                    preparedWidth = evenW
                    preparedHeight = evenH
                    success = true
                    Log.d(TAG, "Successfully configured screen recorder: ${evenW}x${evenH} @ ${fps}fps (audio=$tryAudio)")
                    break
                }
            }

            if (!success) {
                Log.e(TAG, "All MediaRecorder configurations failed")
                cleanupRecording()
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_RECORDING_ERROR))
                stopSelf()
                return
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "LibreCutsScreenRecord",
                preparedWidth,
                preparedHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()
            isRecording = true

            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_RECORDING_STARTED))

        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen record", e)
            cleanupRecording()
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_RECORDING_ERROR))
            stopSelf()
        }
    }

    private fun stopAndSaveRecording() {
        if (!isRecording) return
        isRecording = false

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaRecorder (recording may be short)", e)
        }

        cleanupRecording()

        var savedUri: Uri? = null
        val file = outputFile
        if (file != null && file.exists() && file.length() > 0) {
            savedUri = saveVideoToDeviceStorage(file)
        }

        val broadcastIntent = Intent(ACTION_RECORDING_FINISHED).apply {
            if (savedUri != null) {
                putExtra(EXTRA_RECORDED_URI, savedUri)
            }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val savedNotice = createNotification("Screen recording saved to Movies!", isRecording = false)
        notificationManager.notify(NOTIFICATION_ID + 1, savedNotice)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveVideoToDeviceStorage(sourceFile: File): Uri? {
        val resolver = contentResolver
        val filename = "ScreenRecord_${System.currentTimeMillis()}.mp4"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoEditing")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, contentValues)

            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    uri
                } catch (e: Exception) {
                    Log.e(TAG, "Failed copying to MediaStore", e)
                    null
                }
            } else null
        } else {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoEditing")
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, filename)
            try {
                FileInputStream(sourceFile).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                }
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                Uri.fromFile(targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving to public movies directory", e)
                null
            }
        }
    }

    private fun cleanupRecording() {
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering MediaProjection callback", e)
        }

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VirtualDisplay", e)
        }

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection", e)
        }

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopAndSaveRecording()
        }
    }
}