package ba.vaktija.nova.service

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
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ba.vaktija.nova.MainActivity
import ba.vaktija.nova.R
import ba.vaktija.nova.data.VaktijaRepository
import ba.vaktija.nova.model.VaktijaDay
import ba.vaktija.nova.util.StatusBarCountdownIconGenerator
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
    private var lastLoadedDate: java.time.LocalDate? = null
    private var isDeviceOnLockScreen = false

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> isDeviceOnLockScreen = true
                Intent.ACTION_SCREEN_ON -> {
                    // Keep isDeviceOnLockScreen = true when waking the screen onto the Lock Screen!
                    // It only clears when ACTION_USER_PRESENT fires after unlocking past the Lock Screen.
                }
                Intent.ACTION_USER_PRESENT -> isDeviceOnLockScreen = false
            }
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
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun checkIsLockedOrScreenOff(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (!pm.isInteractive || km.isKeyguardLocked) {
            isDeviceOnLockScreen = true
        }
        return !pm.isInteractive || km.isKeyguardLocked || isDeviceOnLockScreen
    }

    private fun getDynamicNowBriefGreeting(): String {
        val hour = java.time.LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "Morning brief"
            in 12..16 -> "Afternoon brief"
            in 17..20 -> "Evening is here"
            else -> "Evening brief"
        }
    }

    private fun updateNotificationTick() {
        val today = java.time.LocalDate.now()
        val day = cachedDay ?: repository.buildOfflineFallback(
            repository.getSelectedLocationId(),
            today
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(day))

        val rawStatus = VaktijaRepository.computeNextPrayer(day)
        val status = if (repository.isTestModeEnabled()) {
            val elapsed = ((System.currentTimeMillis() / 1000L) - repository.getTestModeStartEpochSec()).coerceAtLeast(0L)
            val remSec = (59L * 60L - elapsed).coerceAtLeast(1L)
            val remMins = ((remSec + 59L) / 60L).toInt()
            rawStatus.copy(secondsUntilNext = remSec, minutesUntilNextCeil = remMins)
        } else {
            rawStatus
        }

        val showNowBriefMinutes = repository.isNowBriefEnabled() &&
            ((status.minutesUntilNextCeil in 1..60) || repository.isTestModeEnabled())

        // Activate Now Brief on Lock Screen / AOD AND whenever Test mode is enabled!
        if (showNowBriefMinutes && (checkIsLockedOrScreenOff() || repository.isTestModeEnabled())) {
            nm.notify(NOW_BRIEF_NOTIFICATION_ID, buildPromotedNowBriefNotification(status))
        } else {
            nm.cancel(NOW_BRIEF_NOTIFICATION_ID)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val locId = repository.getSelectedLocationId()
        val today = java.time.LocalDate.now()
        val fallbackDay = repository.buildOfflineFallback(locId, today)
        cachedDay = fallbackDay
        lastLoadedDate = today
        startForeground(NOTIFICATION_ID, buildNotification(fallbackDay))

        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            cachedDay = repository.getPrayerTimes(repository.getSelectedLocationId(), today)
            lastLoadedDate = today
            while (isActive) {
                val nowDate = java.time.LocalDate.now()
                if (lastLoadedDate != nowDate) {
                    cachedDay = repository.getPrayerTimes(repository.getSelectedLocationId(), nowDate)
                    lastLoadedDate = nowDate
                }
                updateNotificationTick()
                delay(1000L)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(day: VaktijaDay): Notification {
        val rawStatus = VaktijaRepository.computeNextPrayer(day)
        val status = if (repository.isTestModeEnabled()) {
            val elapsed = ((System.currentTimeMillis() / 1000L) - repository.getTestModeStartEpochSec()).coerceAtLeast(0L)
            val remSec = (59L * 60L - elapsed).coerceAtLeast(1L)
            val remMins = ((remSec + 59L) / 60L).toInt()
            rawStatus.copy(secondsUntilNext = remSec, minutesUntilNextCeil = remMins)
        } else {
            rawStatus
        }
        val h = status.secondsUntilNext / 3600
        val m = (status.secondsUntilNext % 3600) / 60
        val s = status.secondsUntilNext % 60

        val isFriday = java.time.LocalDate.now().dayOfWeek.value == 5 ||
            day.gregorianDate.startsWith("pet")
        val targetPrefixes = listOf(
            "Zora za",
            "Izlazak sunca za",
            if (isFriday) "Džuma za" else "Podne za",
            "Ikindija za",
            "Akšam za",
            "Jacija za"
        )
        val targetLabel = targetPrefixes[status.nextIndex]
        val countdownTitle = "${targetLabel}: ${h}h ${m}m ${s}s"

        val isNight = (resources.configuration.uiMode and 48) == 32
        val collapsedView = RemoteViews(packageName, R.layout.notification_ediyanet)
        val expandedView = RemoteViews(packageName, R.layout.notification_ediyanet_expanded)

        populateEbiyanetRemoteViews(collapsedView, day, status, countdownTitle, isNight, isFriday)
        populateExpandedRemoteViews(expandedView, day, status, targetLabel, "${h}h ${m}m ${s}s", isNight, isFriday)

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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(countdownTitle)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setGroup("vaktija_ediyanet_panel_group")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
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
        digitsText: String,
        isNight: Boolean,
        isFriday: Boolean
    ) {
        val hijriDisplay = day.hijriDate.ifBlank {
            VaktijaRepository.computeBosnianHijriDate(java.time.LocalDate.now())
        }
        val blackColor = if (isNight) Color.WHITE else Color.parseColor("#0C0C0C")
        val subColor = if (isNight) Color.parseColor("#D8DCE0") else Color.parseColor("#3A3D40")
        val redColor = if (isNight) Color.parseColor("#FF453A") else Color.parseColor("#D81515")

        rv.setTextViewText(R.id.tv_exp_city, day.locationName)
        rv.setTextViewText(R.id.tv_exp_hijri, hijriDisplay)
        rv.setTextViewText(R.id.tv_exp_target_label, targetLabel)
        rv.setTextViewText(R.id.tv_exp_digits, digitsText)
        rv.setTextViewText(R.id.tv_exp_standard_date, day.gregorianDate)

        rv.setTextColor(R.id.tv_exp_city, subColor)
        rv.setTextColor(R.id.tv_exp_hijri, subColor)
        rv.setTextColor(R.id.tv_exp_target_label, blackColor)
        rv.setTextColor(R.id.tv_exp_digits, blackColor)
        rv.setTextColor(R.id.tv_exp_standard_date, subColor)

        val nameIds = intArrayOf(R.id.exp_name_0, R.id.exp_name_1, R.id.exp_name_2, R.id.exp_name_3, R.id.exp_name_4, R.id.exp_name_5)
        val timeIds = intArrayOf(R.id.exp_time_0, R.id.exp_time_1, R.id.exp_time_2, R.id.exp_time_3, R.id.exp_time_4, R.id.exp_time_5)

        val expLabels = listOf(
            "Zora",
            "Izlazak sunca",
            if (isFriday) "Džuma" else "Podne",
            "Ikindija",
            "Akšam",
            "Jacija"
        )

        for (i in 0 until 6) {
            val isNext = (i == status.currentActiveIndex)
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
        countdownTitle: String,
        isNight: Boolean,
        isFriday: Boolean
    ) {
        val redActive = if (isNight) Color.parseColor("#FF453A") else Color.parseColor("#D61A1A")
        val blackNormal = if (isNight) Color.WHITE else Color.parseColor("#111111")

        rv.setTextViewText(R.id.tv_ediyanet_countdown, countdownTitle)
        rv.setTextColor(R.id.tv_ediyanet_countdown, blackNormal)

        val nameIds = intArrayOf(R.id.name_0, R.id.name_1, R.id.name_2, R.id.name_3, R.id.name_4, R.id.name_5)
        val timeIds = intArrayOf(R.id.time_0, R.id.time_1, R.id.time_2, R.id.time_3, R.id.time_4, R.id.time_5)

        val shortLabels = listOf(
            "Zora",
            "Izlazak",
            if (isFriday) "Džuma" else "Podne",
            "Ikindija",
            "Akšam",
            "Jacija"
        )

        for (i in 0 until 6) {
            val nameStr = shortLabels[i]
            val timeStr = day.vakatTimes.getOrElse(i) { "--:--" }
            val isActive = (i == status.currentActiveIndex)

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

    private fun buildPromotedNowBriefNotification(
        status: VaktijaRepository.Companion.NextPrayerStatus
    ): Notification {
        val isNight = (resources.configuration.uiMode and 48) == 32
        val nowBriefCircleBitmap = StatusBarCountdownIconGenerator.buildNowBriefCircleBitmap(
            status.minutesUntilNextCeil,
            isAod = isNight
        )
        val circleBitmapIconCompat = IconCompat.createWithBitmap(nowBriefCircleBitmap)
        val circleCutoutIcon = StatusBarCountdownIconGenerator.buildNowBriefCircleCutoutIcon(
            status.minutesUntilNextCeil
        )
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val briefGreeting = getDynamicNowBriefGreeting()

        // Push Conversation Shortcut with the minutes circle avatar so Samsung One UI replaces the Vaktija app icon with the minutes circle!
        val nowBriefPerson = Person.Builder()
            .setKey("vaktija_now_brief_person")
            .setName(briefGreeting)
            .setIcon(circleBitmapIconCompat)
            .setImportant(true)
            .build()

        try {
            val shortcut = ShortcutInfoCompat.Builder(this, NOW_BRIEF_SHORTCUT_ID)
                .setLongLived(true)
                .setShortLabel(briefGreeting)
                .setIcon(circleBitmapIconCompat)
                .setPerson(nowBriefPerson)
                .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
        } catch (_: Exception) {}

        val nowBarExtras = android.os.Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putCharSequence("android.shortCriticalText", status.minutesUntilNextCeil.toString())
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", briefGreeting)
            putParcelable("android.ongoingActivityNoti.primaryIcon", android.graphics.drawable.Icon.createWithBitmap(nowBriefCircleBitmap))
            putParcelable("android.ongoingActivityNoti.chipIcon", android.graphics.drawable.Icon.createWithBitmap(nowBriefCircleBitmap))
        }

        return NotificationCompat.Builder(this, NOW_BRIEF_CHANNEL_ID)
            .setSmallIcon(circleCutoutIcon)
            .setLargeIcon(nowBriefCircleBitmap)
            .setContentTitle(briefGreeting)
            .setShortcutId(NOW_BRIEF_SHORTCUT_ID)
            .addPerson(nowBriefPerson)
            .setGroup("vaktija_now_brief_group")
            .setWhen(System.currentTimeMillis() + status.secondsUntilNext * 1000L)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addExtras(nowBarExtras)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)

            val nowBriefChannel = NotificationChannel(
                NOW_BRIEF_CHANNEL_ID,
                "Vaktija Now Brief / Now Bar (≤ 60 min)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Promovisana obavijest za Samsung Now Bar i Now Brief kada je ≤ 60 min do vakta"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(nowBriefChannel)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "vaktija_premium_ediyanet_v6"
        const val NOW_BRIEF_CHANNEL_ID = "vaktija_now_brief_live_v6"
        const val NOW_BRIEF_SHORTCUT_ID = "vaktija_now_brief_shortcut_v6"
        const val NOTIFICATION_ID = 1448
        const val NOW_BRIEF_NOTIFICATION_ID = 1449
    }
}
