package ba.vaktija.premium.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import ba.vaktija.premium.MainActivity
import ba.vaktija.premium.R
import ba.vaktija.premium.data.VaktijaRepository
import ba.vaktija.premium.model.VaktijaDay
import ba.vaktija.premium.util.StatusBarCountdownIconGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VaktijaNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: kotlinx.coroutines.Job? = null
    private lateinit var repository: VaktijaRepository
    private var cachedDay: VaktijaDay? = null

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateNotificationTick()
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = VaktijaRepository(applicationContext)
        createNotificationChannel()
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun updateNotificationTick() {
        val day = cachedDay ?: repository.buildOfflineFallback(
            repository.getSelectedLocationId(),
            java.time.LocalDate.now()
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(day))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val locId = repository.getSelectedLocationId()
        val fallbackDay = repository.buildOfflineFallback(locId, java.time.LocalDate.now())
        cachedDay = fallbackDay
        startForeground(NOTIFICATION_ID, buildNotification(fallbackDay))

        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            cachedDay = repository.getPrayerTimes(repository.getSelectedLocationId())
            while (isActive) {
                updateNotificationTick()
                delay(1000L)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(day: VaktijaDay): Notification {
        val status = VaktijaRepository.computeNextPrayer(day)
        val h = status.secondsUntilNext / 3600
        val m = (status.secondsUntilNext % 3600) / 60
        val s = status.secondsUntilNext % 60

        val targetPrefixes = listOf(
            "Zora za",
            "Izlazak sunca za",
            "Podne za",
            "Ikindija za",
            "Akšam za",
            "Jacija za"
        )
        val targetLabel = targetPrefixes[status.nextIndex]
        val countdownTitle = "${targetLabel}: ${h}h ${m}m ${s}s"

        val collapsedView = RemoteViews(packageName, R.layout.notification_ediyanet)
        val expandedView = RemoteViews(packageName, R.layout.notification_ediyanet_expanded)

        populateEbiyanetRemoteViews(collapsedView, day, status, countdownTitle)
        populateExpandedRemoteViews(expandedView, day, status, targetLabel, "${h}h ${m}m ${s}s")

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = StatusBarCountdownIconGenerator.buildStatusBarIcon(
            this,
            status.minutesUntilNextCeil
        )
        val showNowBriefMinutes = repository.isNowBriefEnabled() && (status.minutesUntilNextCeil in 1..60)
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isAod = !powerManager.isInteractive

        val nowBarExtras = android.os.Bundle().apply {
            // Display in Samsung Now Brief circle ONLY when enabled in settings AND <= 60 minutes remain
            putBoolean("android.requestPromotedOngoing", showNowBriefMinutes)
            if (showNowBriefMinutes) {
                putCharSequence("android.shortCriticalText", status.minutesUntilNextCeil.toString())
            }
        }

        // Style "Evening is here" text in the same gold color (#DAB063) as the numbers in the circle
        val goldNowBriefTitle = SpannableString("Evening is here").apply {
            setSpan(
                android.text.style.ForegroundColorSpan(
                    if (isAod) Color.parseColor("#A1844D") else Color.parseColor("#DAB063")
                ),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(if (showNowBriefMinutes) goldNowBriefTitle else countdownTitle)
            .addExtras(nowBarExtras)

        if (showNowBriefMinutes) {
            // Circle stays its navy-blue & gold color, just a bit dimmer when on Always On Display (isAod = true)
            val nowBriefCircleBitmap = StatusBarCountdownIconGenerator.buildNowBriefCircleBitmap(
                status.minutesUntilNextCeil,
                isAod = isAod
            )
            builder.setLargeIcon(nowBriefCircleBitmap)

            if (!isAod) {
                // Active screen: blurry translucent navy-blue background matching the circle (#051632)
                builder.setColor(Color.parseColor("#051632"))
                builder.setColorized(true)
            } else {
                // Always On Display: let Samsung Now Brief use its default background color
                builder.setColorized(false)
            }
        }

        return builder
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun populateExpandedRemoteViews(
        rv: RemoteViews,
        day: VaktijaDay,
        status: VaktijaRepository.Companion.NextPrayerStatus,
        targetLabel: String,
        digitsText: String
    ) {
        rv.setTextViewText(R.id.tv_exp_city, day.locationName)
        rv.setTextViewText(R.id.tv_exp_hijri, day.hijriDate)
        rv.setTextViewText(R.id.tv_exp_target_label, targetLabel)
        rv.setTextViewText(R.id.tv_exp_digits, digitsText)
        rv.setTextViewText(R.id.tv_exp_standard_date, day.gregorianDate)

        val nameIds = intArrayOf(R.id.exp_name_0, R.id.exp_name_1, R.id.exp_name_2, R.id.exp_name_3, R.id.exp_name_4, R.id.exp_name_5)
        val timeIds = intArrayOf(R.id.exp_time_0, R.id.exp_time_1, R.id.exp_time_2, R.id.exp_time_3, R.id.exp_time_4, R.id.exp_time_5)
        val redColor = Color.parseColor("#D81515")
        val blackColor = Color.parseColor("#0C0C0C")

        val expLabels = listOf("Zora", "Izlazak sunca", "Podne", "Ikindija", "Akšam", "Jacija")

        for (i in 0 until 6) {
            val isNext = (i == status.nextIndex)
            rv.setTextViewText(nameIds[i], expLabels[i])
            rv.setTextViewText(timeIds[i], day.vakatTimes.getOrElse(i) { "--:--" })
            rv.setTextColor(nameIds[i], if (isNext) redColor else blackColor)
            rv.setTextColor(timeIds[i], if (isNext) redColor else blackColor)
        }
    }

    private fun populateEbiyanetRemoteViews(
        rv: RemoteViews,
        day: VaktijaDay,
        status: VaktijaRepository.Companion.NextPrayerStatus,
        countdownTitle: String
    ) {
        rv.setTextViewText(R.id.tv_ediyanet_countdown, countdownTitle)

        // Left icon in the notification panel ALWAYS shows the Vaktija.ba app icon (never minutes)
        rv.setImageViewResource(R.id.iv_notif_left_icon, R.drawable.ic_vaktija_launcher)

        val nameIds = intArrayOf(R.id.name_0, R.id.name_1, R.id.name_2, R.id.name_3, R.id.name_4, R.id.name_5)
        val timeIds = intArrayOf(R.id.time_0, R.id.time_1, R.id.time_2, R.id.time_3, R.id.time_4, R.id.time_5)

        val redActive = Color.parseColor("#D61A1A")
        val blackNormal = Color.parseColor("#111111")

        for (i in 0 until 6) {
            val nameStr = VaktijaDay.SHORT_NAMES[i]
            val timeStr = day.vakatTimes.getOrElse(i) { "--:--" }
            val isActive = (i == status.nextIndex)

            val boldName = SpannableString(nameStr).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val styledTime = SpannableString(timeStr).apply {
                if (isActive) {
                    setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            rv.setTextViewText(nameIds[i], boldName)
            rv.setTextViewText(timeIds[i], styledTime)
            rv.setTextColor(nameIds[i], if (isActive) redActive else blackNormal)
            rv.setTextColor(timeIds[i], if (isActive) redActive else blackNormal)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "vaktija_premium_ediyanet_v3"
        const val NOTIFICATION_ID = 1448
    }
}
