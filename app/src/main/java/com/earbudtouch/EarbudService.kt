package com.earbudtouch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast

/**
 * Holds an active MediaSession (so Android sends the earbuds' media-button events to us),
 * turns taps into a pattern string (S = short, L = long, N = next, P = previous)
 * and runs the action assigned to that pattern.
 */
class EarbudService : Service() {
    companion object {
        @Volatile var running = false
        const val CH = "earbud_touch"
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var session: MediaSession
    private var track: AudioTrack? = null
    @Volatile private var silent = false
    private val tokens = StringBuilder()
    private var isDown = false
    private var longFlag = false

    private val fallback = Runnable { if (isDown) { isDown = false; push('L') } }
    private val longRun = Runnable { if (isDown) { longFlag = true; h.postDelayed(fallback, 1500) } }
    private val idleRun = Runnable { commit() }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        running = true
        startFg()
        setupSession()
        startSilence()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Earbud Touch", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setContentTitle("Earbud Touch is active")
            .setContentText("Listening to your earbuds")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)
    }

    @Suppress("DEPRECATION")
    private fun setupSession() {
        session = MediaSession(this, "EarbudTouch")
        session.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean { handle(mediaButtonIntent); return true }
            override fun onPlay() { push('S') }
            override fun onPause() { push('S') }
            override fun onSkipToNext() { push('N') }
            override fun onSkipToPrevious() { push('P') }
        }, h)
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(PlaybackState.STATE_PLAYING, 0L, 1f).build())
        session.isActive = true
    }

    /** Plays endless silence so Android keeps routing media buttons to this app. */
    private fun startSilence() {
        val min = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(min)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        silent = true
        Thread {
            val z = ByteArray(min)
            try { while (silent) t.write(z, 0, z.size) } catch (e: Exception) { }
        }.start()
    }

    private fun handle(i: Intent) {
        val ke: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (ke == null) return
        when (ke.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> tap(ke)
            KeyEvent.KEYCODE_MEDIA_NEXT -> if (ke.action == KeyEvent.ACTION_DOWN) push('N')
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (ke.action == KeyEvent.ACTION_DOWN) push('P')
        }
    }

    private fun tap(ke: KeyEvent) {
        if (ke.action == KeyEvent.ACTION_DOWN) {
            if (ke.repeatCount == 0) {
                if (!isDown) {
                    isDown = true; longFlag = false
                    h.removeCallbacks(longRun)
                    h.postDelayed(longRun, Store.longMs(this).toLong())
                }
            } else longFlag = true
        } else if (ke.action == KeyEvent.ACTION_UP) {
            if (isDown) {
                h.removeCallbacks(longRun); h.removeCallbacks(fallback)
                isDown = false
                val d = ke.eventTime - ke.downTime
                push(if (longFlag || d >= Store.longMs(this)) 'L' else 'S')
            } else push('S')
        }
    }

    private fun push(c: Char) {
        tokens.append(c)
        Bus.logger?.invoke("Pattern so far: $tokens")
        h.removeCallbacks(idleRun)
        h.postDelayed(idleRun, Store.idleMs(this).toLong())
    }

    private fun commit() {
        val p = tokens.toString()
        tokens.setLength(0)
        if (p.isEmpty()) return
        val rec = Bus.recorder
        if (rec != null) { rec(p); Bus.logger?.invoke("Recorded: $p"); return }
        val a = Store.find(this, p)
        if (a != null) { Bus.logger?.invoke("$p  ->  ${a.label}"); execAct(a) }
        else if (p == "S" && Store.pass(this)) { Bus.logger?.invoke("S  ->  play/pause (passed through)"); media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        else Bus.logger?.invoke("$p  ->  (no action assigned)")
    }

    private fun execAct(a: Act) {
        when (a.t) {
            "APP" -> openApp(a.v)
            "TYPE" -> if (TypeService.instance?.insert(a.v) != true) toast("Turn on the typing service and focus a text field")
            "BACK" -> if (TypeService.instance?.back() != true) toast("Turn on the typing service and focus a text field")
            "MEDIA" -> media(a.v.toInt())
        }
    }

    /**
     * Opens the app like a tap on its icon. The accessibility service is allowed by Android to start
     * activities from the background, so no "display over other apps" permission is needed.
     * If it is off, a high-priority notification is posted instead (tap it to open the app).
     */
    private fun openApp(pkg: String) {
        val li = packageManager.getLaunchIntentForPackage(pkg)
        if (li == null) { toast("App not found"); return }
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val svc = TypeService.instance
        if (svc != null) {
            try { svc.startActivity(li); return } catch (e: Exception) { }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("earbud_open", "Open app", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(this, pkg.hashCode(), li, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
        val n = Notification.Builder(this, "earbud_open")
            .setContentTitle("Tap to open $label")
            .setContentText("Turn on Earbud Touch in Accessibility to open apps automatically")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true).build()
        nm.notify(2, n)
        toast("Turn on Earbud Touch in Accessibility to open apps automatically")
    }

    /** Sends a media key to whatever player is active, by briefly stepping aside. */
    private fun media(code: Int) {
        val am = getSystemService(AudioManager::class.java)
        session.isActive = false
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        h.postDelayed({ session.isActive = true }, 500)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        silent = false
        h.removeCallbacksAndMessages(null)
        try { track?.stop(); track?.release() } catch (e: Exception) { }
        try { session.isActive = false; session.release() } catch (e: Exception) { }
        running = false
        super.onDestroy()
    }
}
