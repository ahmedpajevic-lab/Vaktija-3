package ba.vaktija.premium.data

import android.content.Context
import ba.vaktija.premium.model.VaktijaDay
import ba.vaktija.premium.model.VaktijaLocations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

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

    suspend fun getPrayerTimes(locationId: Int, date: LocalDate = LocalDate.now()): VaktijaDay =
        withContext(Dispatchers.IO) {
            val cacheKey = "cache_${locationId}_${date.year}_${date.monthValue}_${date.dayOfMonth}"
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
                    return@withContext parseVaktijaJson(locationId, body)
                }
            } catch (_: Exception) {}

            prefs.getString(cacheKey, null)?.let { cached ->
                try {
                    return@withContext parseVaktijaJson(locationId, cached)
                } catch (_: Exception) {}
            }

            return@withContext buildOfflineFallback(locationId, date)
        }

    private fun parseVaktijaJson(locationId: Int, rawJson: String): VaktijaDay {
        val obj = JSONObject(rawJson)
        val locationName = obj.optString("lokacija", VaktijaLocations.byId(locationId).name)
        val datumArr = obj.optJSONArray("datum")
        val hijri = datumArr?.optString(0, "") ?: ""
        val gregorian = datumArr?.optString(1, "") ?: ""
        val vakatArr = obj.getJSONArray("vakat")
        val times = (0 until vakatArr.length()).map { idx ->
            val raw = vakatArr.getString(idx)
            val parts = raw.split(":")
            "%02d:%02d".format(parts[0].toInt(), parts[1].toInt())
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
        val baseTimes = when (locationId) {
            93 -> listOf("04:46", "06:16", "11:30", "14:10", "16:40", "18:01")
            77 -> listOf("04:44", "06:14", "11:31", "14:14", "16:45", "18:05")
            61 -> listOf("04:49", "06:19", "11:33", "14:14", "16:44", "18:05")
            else -> listOf("04:46", "06:16", "11:30", "14:10", "16:40", "18:01")
        }
        return VaktijaDay(
            locationId = locationId,
            locationName = locName,
            hijriDate = "22. džumade-l-ula 1448",
            gregorianDate = "pon, ${date.dayOfMonth}. novembar ${date.year}",
            vakatTimes = baseTimes
        )
    }

    companion object {
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
