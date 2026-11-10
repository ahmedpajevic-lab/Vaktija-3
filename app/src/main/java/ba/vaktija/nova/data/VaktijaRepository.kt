package ba.vaktija.nova.data

import android.content.Context
import ba.vaktija.nova.model.VaktijaDay
import ba.vaktija.nova.model.VaktijaLocations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

class VaktijaRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("vaktija_premium_prefs", Context.MODE_PRIVATE)

    fun getSelectedLocationId(): Int =
        prefs.getInt("selected_location_id", VaktijaLocations.DEFAULT_LOCATION_ID)

    fun setSelectedLocationId(id: Int) {
        prefs.edit().putInt("selected_location_id", id).apply()
    }

    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", false)

    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean("dark_theme", dark).apply()
    }

    fun getTextScale(): Float = prefs.getFloat("text_scale", 1.0f)

    fun setTextScale(scale: Float) {
        prefs.edit().putFloat("text_scale", scale).apply()
    }

    fun isNowBriefEnabled(): Boolean = prefs.getBoolean("now_brief_enabled", true)

    fun setNowBriefEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("now_brief_enabled", enabled).apply()
    }

    fun isTestModeEnabled(): Boolean = prefs.getBoolean("test_59_enabled", false)

    fun getTestModeStartEpochSec(): Long = prefs.getLong("test_59_start_sec", 0L)

    fun setTestModeEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("test_59_enabled", enabled)
            .putLong("test_59_start_sec", System.currentTimeMillis() / 1000L)
            .apply()
    }

    suspend fun getPrayerTimes(locationId: Int, date: LocalDate = LocalDate.now()): VaktijaDay =
        withContext(Dispatchers.IO) {
            val cacheKey = "cache_v3_${locationId}_${date.year}_${date.monthValue}_${date.dayOfMonth}"
            val endpoint = "https://api.vaktija.ba/vaktija/v1/$locationId/${date.year}/${date.monthValue}/${date.dayOfMonth}"

            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    prefs.edit().putString(cacheKey, body).apply()
                    return@withContext parseVaktijaJson(locationId, body, date)
                }
            } catch (_: Exception) {}

            prefs.getString(cacheKey, null)?.let { cached ->
                try {
                    return@withContext parseVaktijaJson(locationId, cached, date)
                } catch (_: Exception) {}
            }

            return@withContext buildOfflineFallback(locationId, date)
        }

    private fun parseVaktijaJson(locationId: Int, rawJson: String, date: LocalDate = LocalDate.now()): VaktijaDay {
        val obj = JSONObject(rawJson)
        val locationName = obj.optString("lokacija", VaktijaLocations.byId(locationId).name)
        val datumArr = obj.optJSONArray("datum")
        val rawHijri = datumArr?.optString(0, "")?.trim() ?: ""
        val hijri = if (rawHijri.isNotEmpty()) rawHijri else computeBosnianHijriDate(date)
        val gregorian = formatBosnianGregorianDate(date)
        val vakatArr = obj.getJSONArray("vakat")
        val dst = isBosniaSummerTime(date)
        val rawMins = (0 until vakatArr.length()).map { idx ->
            val raw = vakatArr.getString(idx)
            val parts = raw.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        }.toMutableList()

        if (dst && rawMins.size == 6 && rawMins[2] in 1..739) {
            for (i in rawMins.indices) {
                rawMins[i] = (rawMins[i] + 60) % 1440
            }
        }

        val times = rawMins.mapIndexed { idx, totalMins ->
            if (idx == 2) {
                if (dst) "13:00" else "12:00"
            } else {
                val norm = ((totalMins % 1440) + 1440) % 1440
                "%02d:%02d".format(norm / 60, norm % 60)
            }
        }
        return VaktijaDay(
            locationId = locationId,
            locationName = locationName,
            hijriDate = hijri,
            gregorianDate = gregorian,
            vakatTimes = times
        )
    }

    fun buildOfflineFallback(locationId: Int, date: LocalDate): VaktijaDay {
        val locName = VaktijaLocations.byId(locationId).name
        val times = computeAstronomicalTakvimTimes(locationId, date)
        return VaktijaDay(
            locationId = locationId,
            locationName = locName,
            hijriDate = computeBosnianHijriDate(date),
            gregorianDate = formatBosnianGregorianDate(date),
            vakatTimes = times
        )
    }

    companion object {
        fun isBosniaSummerTime(date: LocalDate): Boolean {
            val m = date.monthValue
            if (m < 3 || m > 10) return false
            if (m in 4..9) return true
            val lastDay = LocalDate.of(date.year, m, 31)
            val lastSunday = 31 - (lastDay.dayOfWeek.value % 7)
            return if (m == 3) date.dayOfMonth >= lastSunday else date.dayOfMonth < lastSunday
        }

        fun computeAstronomicalTakvimTimes(locationId: Int, date: LocalDate): List<String> {
            val dst = isBosniaSummerTime(date)
            val tz = if (dst) 2.0 else 1.0
            val doy = date.dayOfYear

            var lat = 44.538
            var lon = 18.676
            when (locationId) {
                77 -> { lat = 43.856; lon = 18.413 }
                61 -> { lat = 43.343; lon = 17.808 }
                1 -> { lat = 44.772; lon = 17.191 }
                2 -> { lat = 44.812; lon = 15.869 }
                102 -> { lat = 44.203; lon = 17.908 }
                14 -> { lat = 44.873; lon = 18.808 }
                3 -> { lat = 44.759; lon = 19.214 }
                90 -> { lat = 44.226; lon = 17.666 }
                110 -> { lat = 43.137; lon = 20.512 }
                else -> if (locationId != 93) {
                    lon = 18.676 - ((locationId % 9) - 4) * 0.22
                }
            }

            val gamma = (2.0 * Math.PI / 365.0) * (doy - 1 + 0.5)
            val eqtime = 229.18 * (
                0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                    0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
                )
            val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

            val latRad = Math.toRadians(lat)
            fun hourAngle(zenithDeg: Double): Double {
                val zRad = Math.toRadians(zenithDeg)
                val cosHa = ((cos(zRad) - sin(latRad) * sin(decl)) / (cos(latRad) * cos(decl)))
                    .coerceIn(-0.999, 0.999)
                return Math.toDegrees(acos(cosHa))
            }

            val solarNoon = 720.0 - 4.0 * lon - eqtime + tz * 60.0
            val zoraMin = solarNoon - 4.0 * hourAngle(108.0) + 1.0
            val izlazakMin = solarNoon - 4.0 * hourAngle(90.833) - 7.0
            val asrAlt = atan(1.0 / (1.0 + tan(abs(latRad - decl))))
            val asrZenith = 90.0 - Math.toDegrees(asrAlt)
            val ikindijaMin = solarNoon + 4.0 * hourAngle(asrZenith)
            val aksamMin = solarNoon + 4.0 * hourAngle(90.833) + 6.0
            val jacijaMin = solarNoon + 4.0 * hourAngle(106.3)

            fun fmt(mins: Double): String {
                val r = ((mins.roundToInt() % 1440) + 1440) % 1440
                return "%02d:%02d".format(r / 60, r % 60)
            }

            val podneStr = if (dst) "13:00" else "12:00"
            return listOf(
                fmt(zoraMin),
                fmt(izlazakMin),
                podneStr,
                fmt(ikindijaMin),
                fmt(aksamMin),
                fmt(jacijaMin)
            )
        }

        fun computeBosnianHijriDate(date: LocalDate): String {
            return try {
                val hijri = java.time.chrono.HijrahDate.from(date)
                val day = hijri.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                val monthIdx = hijri.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1
                val year = hijri.get(java.time.temporal.ChronoField.YEAR)
                val months = listOf(
                    "muharrem", "safer", "rebiu-l-evvel", "rebiu-l-ahir",
                    "džumade-l-ula", "džumade-l-uhra", "redžeb", "ša'ban",
                    "ramazan", "ševval", "zu-l-ka'de", "zu-l-hidždže"
                )
                "${day}. ${months.getOrElse(monthIdx) { "džumade-l-ula" }} ${year}"
            } catch (_: Exception) {
                "25. džumade-l-ula 1448"
            }
        }

        fun formatBosnianGregorianDate(date: LocalDate): String {
            val days = listOf("pon", "uto", "sri", "čet", "pet", "sub", "ned")
            val months = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec")
            val dName = days.getOrElse(date.dayOfWeek.value - 1) { "čet" }
            val mName = months.getOrElse(date.monthValue - 1) { "nov" }
            return "$dName, ${date.dayOfMonth}. $mName ${date.year}."
        }

        data class NextPrayerStatus(
            val currentActiveIndex: Int,
            val nextIndex: Int,
            val nextName: String,
            val nextTimeStr: String,
            val secondsUntilNext: Long,
            val minutesUntilNextCeil: Int
        )

        fun computeNextPrayer(day: VaktijaDay, now: LocalTime = LocalTime.now()): NextPrayerStatus {
            val nowSec = now.toSecondOfDay()
            for (i in day.vakatTimes.indices) {
                val parts = day.vakatTimes[i].split(":")
                val targetTime = LocalTime.of(parts[0].toInt(), parts[1].toInt())
                val targetSec = targetTime.toSecondOfDay()
                if (targetSec > nowSec) {
                    val diffSec = (targetSec - nowSec).toLong()
                    val minsCeil = ((diffSec + 59) / 60).toInt()
                    val currentActive = if (i == 0) 5 else i - 1
                    return NextPrayerStatus(
                        currentActiveIndex = currentActive,
                        nextIndex = i,
                        nextName = VaktijaDay.SHORT_NAMES[i],
                        nextTimeStr = day.vakatTimes[i],
                        secondsUntilNext = diffSec,
                        minutesUntilNextCeil = minsCeil
                    )
                }
            }
            val zoraParts = day.vakatTimes[0].split(":")
            val zoraSec = LocalTime.of(zoraParts[0].toInt(), zoraParts[1].toInt()).toSecondOfDay()
            val diffSec = ((24 * 3600 - nowSec) + zoraSec).toLong()
            val minsCeil = ((diffSec + 59) / 60).toInt()
            return NextPrayerStatus(
                currentActiveIndex = 5,
                nextIndex = 0,
                nextName = VaktijaDay.SHORT_NAMES[0],
                nextTimeStr = day.vakatTimes[0],
                secondsUntilNext = diffSec,
                minutesUntilNextCeil = minsCeil
            )
        }
    }
}
