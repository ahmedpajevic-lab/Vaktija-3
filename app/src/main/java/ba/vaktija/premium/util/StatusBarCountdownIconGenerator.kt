package ba.vaktija.premium.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import ba.vaktija.premium.R

/**
 * Generates the upper-left Android status bar icon (full-bleed 96x96px so it matches the exact size of the Vaktija.ba icon):
 * - When > 60 min: returns the original Vaktija.ba status bar icon (R.drawable.ic_stat_vaktija_default).
 * - When <= 60 min: returns a full-size rounded square box showing the remaining minutes (60..1).
 */
object StatusBarCountdownIconGenerator {

    fun buildStatusBarIcon(context: Context, minutesUntilNext: Int): IconCompat {
        if (minutesUntilNext > 60 || minutesUntilNext <= 0) {
            return IconCompat.createWithResource(context, R.drawable.ic_stat_vaktija_default)
        }

        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        // Compact One UI battery-pill proportions so Android/One UI auto-inverts it cleanly
        val rect = RectF(4f, 16f, size - 4f, size - 16f)
        canvas.drawRoundRect(rect, 18f, 18f, boxPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textSize = if (minutesUntilNext >= 10) 48f else 54f
            textAlign = Paint.Align.CENTER
        }

        val label = minutesUntilNext.toString()
        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val centerY = (size / 2f) - textBounds.exactCenterY()
        canvas.drawText(label, size / 2f, centerY, textPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    /**
     * Generates the exact 39dp x 39dp (117x117px) Samsung Now Brief circular badge
     * displaying ONLY the minutes to the next prayer time (nicely visible white bold numerals
     * over Samsung Now Brief gradient circle), next to Now Brief text ("Evening is here").
     */
    fun buildNowBriefCircleBitmap(minutesUntilNext: Int, isAod: Boolean = false): Bitmap {
        val size = 117 // 39dp at 3x density (exact Samsung Now Brief circle dimensions)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f

        // Deep Navy Blue background (#051632 -> #02112A when active; slightly dimmed on Always On Display)
        val topBlue = if (isAod) Color.parseColor("#041024") else Color.parseColor("#051632")
        val botBlue = if (isAod) Color.parseColor("#010C1E") else Color.parseColor("#02112A")
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(topBlue, botBlue),
                floatArrayOf(0f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius, bgPaint)

        // Gold numerals matching the clock hands (#E0B76B -> #CCA255 -> #BD9145; slightly dimmed on AOD)
        val goldTop = if (isAod) Color.parseColor("#A1844D") else Color.parseColor("#E0B76B")
        val goldMid = if (isAod) Color.parseColor("#93753D") else Color.parseColor("#CCA255")
        val goldBot = if (isAod) Color.parseColor("#886832") else Color.parseColor("#BD9145")

        val mins = minutesUntilNext.coerceAtLeast(1)
        val label = mins.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldMid
            shader = android.graphics.LinearGradient(
                0f, size * 0.25f, 0f, size * 0.75f,
                intArrayOf(goldTop, goldMid, goldBot),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = if (mins >= 100) 44f else 53f
            textAlign = Paint.Align.CENTER
        }

        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val centerY = radius - textBounds.exactCenterY()
        canvas.drawText(label, radius, centerY, textPaint)

        return bitmap
    }
}
