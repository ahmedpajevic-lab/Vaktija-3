package ba.vaktija.nova.util

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
import ba.vaktija.nova.R

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
        val rect = RectF(4f, 4f, size - 4f, size - 4f)
        canvas.drawRoundRect(rect, 18f, 18f, boxPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textSize = if (minutesUntilNext >= 10) 58f else 66f
            textAlign = Paint.Align.CENTER
        }

        val label = minutesUntilNext.toString()
        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val centerY = (size / 2f) - textBounds.exactCenterY()
        canvas.drawText(label, size / 2f, centerY, textPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    fun buildNowBriefCircleCutoutIcon(minutesUntilNext: Int): IconCompat {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 44f, circlePaint)

        val mins = minutesUntilNext.coerceAtLeast(1)
        val label = mins.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textSize = if (mins >= 10) 52f else 60f
            textAlign = Paint.Align.CENTER
        }

        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val centerY = (size / 2f) - textBounds.exactCenterY()
        canvas.drawText(label, size / 2f, centerY, textPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    fun buildNowBriefCircleBitmap(minutesUntilNext: Int, isAod: Boolean = false): Bitmap {
        val size = 117
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isAod) Color.parseColor("#2F353E") else Color.parseColor("#3B424C")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius, bgPaint)

        val mins = minutesUntilNext.coerceAtLeast(1)
        val label = mins.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
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
