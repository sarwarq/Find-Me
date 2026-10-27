package com.example.findme.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.example.findme.R
import kotlin.math.max

/** Draws marker bitmaps in the style of Yandex Maps. */
object PinFactory {

    private val cache = HashMap<String, Bitmap>()
    private val fonts = HashMap<Boolean, Typeface>()

    private fun typeface(context: Context, bold: Boolean): Typeface = fonts.getOrPut(bold) {
        val res = if (bold) R.font.okine_sans_bold else R.font.okine_sans_regular
        ResourcesCompat.getFont(context, res) ?: Typeface.DEFAULT
    }

    private fun Canvas.drawIcon(context: Context, @DrawableRes res: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        d.setTint(color)
        val half = size / 2
        d.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        d.draw(this)
    }

    /** Small round marker of a search result: colored circle with a white icon. */
    fun resultPin(context: Context, style: CategoryStyle): Bitmap =
        cache.getOrPut("result:${style.icon}:${style.color}") {
            val d = context.resources.displayMetrics.density
            val r = 12f * d
            val border = 2f * d
            val shadow = 2f * d
            val size = ((r + border + shadow) * 2).toInt()
            val bmp = createBitmap(size, size)
            val c = Canvas(bmp)
            val cx = size / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.WHITE
            paint.setShadowLayer(shadow, 0f, d * 0.5f, 0x55000000)
            c.drawCircle(cx, cx, r + border, paint)
            paint.clearShadowLayer()
            paint.color = style.color
            c.drawCircle(cx, cx, r, paint)
            c.drawIcon(context, style.icon, cx, cx, r * 1.25f, Color.WHITE)
            bmp
        }

    /**
     * Big drop pin of the selected place: a circle with the category icon and a short tail.
     * Its anchor is the tip of the tail (0.5, 1).
     */
    fun placePin(context: Context, style: CategoryStyle, circleColor: Int = style.color): Bitmap =
        cache.getOrPut("place:${style.icon}:$circleColor") {
            val d = context.resources.displayMetrics.density
            val r = 22f * d
            val tail = 9f * d
            val shadow = 3f * d
            val w = ((r + shadow) * 2).toInt()
            val h = (r * 2 + tail + shadow * 2).toInt()
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            val cx = w / 2f
            val cy = shadow + r
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val path = Path().apply {
                addCircle(cx, cy, r, Path.Direction.CW)
                moveTo(cx - r * 0.42f, cy + r * 0.88f)
                lineTo(cx, cy + r + tail)
                lineTo(cx + r * 0.42f, cy + r * 0.88f)
                close()
            }
            paint.color = circleColor
            paint.setShadowLayer(shadow, 0f, d, 0x40000000)
            c.drawPath(path, paint)
            paint.clearShadowLayer()
            c.drawIcon(context, style.icon, cx, cy, r * 1.05f, Color.WHITE)
            bmp
        }

    /**
     * Meet-up member: round photo (or the first letter on [color]) in a white ring with a tail.
     * Green ring when [arrived]; faded when [stale] (no update for a while). Anchor: [pinAnchor].
     */
    fun memberPin(
        context: Context,
        uid: String,
        initial: String,
        color: Int,
        photo: Bitmap?,
        stale: Boolean,
        arrived: Boolean,
    ): Bitmap = cache.getOrPut("member:$uid:$initial:$color:${photo?.generationId}:${photo?.hashCode()}:$stale:$arrived") {
        val d = context.resources.displayMetrics.density
        val r = 21f * d
        val ring = 3f * d
        val tail = 8f * d
        val shadow = 3f * d
        val w = ((r + shadow) * 2).toInt()
        val h = (r * 2 + tail + shadow * 2).toInt()
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val cx = w / 2f
        val cy = shadow + r
        val alpha = if (stale) 140 else 255
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Ring with a tail.
        val path = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            moveTo(cx - r * 0.4f, cy + r * 0.9f)
            lineTo(cx, cy + r + tail)
            lineTo(cx + r * 0.4f, cy + r * 0.9f)
            close()
        }
        paint.color = if (arrived) 0xFF2FA84F.toInt() else Color.WHITE
        paint.alpha = alpha
        paint.setShadowLayer(shadow, 0f, d, 0x40000000)
        c.drawPath(path, paint)
        paint.clearShadowLayer()

        // Photo or letter.
        val inner = r - ring
        if (photo != null) {
            val save = c.save()
            c.clipPath(Path().apply { addCircle(cx, cy, inner, Path.Direction.CW) })
            val side = minOf(photo.width, photo.height)
            val src = android.graphics.Rect(
                (photo.width - side) / 2, (photo.height - side) / 2,
                (photo.width + side) / 2, (photo.height + side) / 2,
            )
            val dst = RectF(cx - inner, cy - inner, cx + inner, cy + inner)
            c.drawBitmap(photo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
            c.restoreToCount(save)
        } else {
            paint.color = color
            paint.alpha = alpha
            c.drawCircle(cx, cy, inner, paint)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                this.alpha = alpha
                textSize = inner * 1.0f
                typeface = typeface(context, bold = true)
                textAlign = Paint.Align.CENTER
            }
            c.drawText(initial, cx, cy - (text.ascent() + text.descent()) / 2, text)
        }
        bmp
    }

    /** Anchor of [placePin] and [finishPin]: the tip of the tail. */
    fun pinAnchor(context: Context, bmp: Bitmap): PointF {
        val shadow = 3f * context.resources.displayMetrics.density
        return PointF(0.5f, (bmp.height - shadow) / bmp.height)
    }

    /** Black circle with an icon marking the route destination. */
    fun finishPin(context: Context, style: CategoryStyle): Bitmap =
        placePin(context, style, circleColor = 0xFF21201F.toInt())

    /** Numbered stop on a route: white number on a blue circle with a white ring. */
    fun stopPin(context: Context, number: Int): Bitmap = cache.getOrPut("stop:$number") {
        val d = context.resources.displayMetrics.density
        val r = 12f * d
        val size = ((r + 3 * d) * 2).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.setShadowLayer(2 * d, 0f, 0.5f * d, 0x55000000)
        c.drawCircle(cx, cx, r, paint)
        paint.clearShadowLayer()
        paint.color = 0xFF196DFF.toInt()
        c.drawCircle(cx, cx, r - 2 * d, paint)
        paint.color = Color.WHITE
        paint.textSize = 13f * d
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        val y = cx - (paint.descent() + paint.ascent()) / 2
        c.drawText(number.toString(), cx, y, paint)
        bmp
    }

    /** White circle with a dark ring for a route start that is not "my location". */
    fun startPin(context: Context): Bitmap = cache.getOrPut("start") {
        val d = context.resources.displayMetrics.density
        val r = 8f * d
        val size = ((r + 3 * d) * 2).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.setShadowLayer(2 * d, 0f, 0.5f * d, 0x55000000)
        c.drawCircle(cx, cx, r, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f * d
        paint.color = 0xFF21201F.toInt()
        c.drawCircle(cx, cx, r - 1.75f * d, paint)
        bmp
    }

    /** User location marker: red dot with a white border. With [heading] it gets a pointer on top. */
    fun userPin(context: Context, heading: Boolean): Bitmap = cache.getOrPut("user:$heading") {
        val d = context.resources.displayMetrics.density
        val r = 8f * d
        val border = 3f * d
        val pointer = if (heading) 9f * d else 0f
        val size = ((r + border + pointer) * 2 + 4 * d).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (heading) {
            paint.color = 0xFFFA453A.toInt()
            val tip = cx - r - border - pointer
            val path = Path().apply {
                moveTo(cx, tip)
                lineTo(cx + r * 0.9f, cx - r * 0.55f)
                lineTo(cx - r * 0.9f, cx - r * 0.55f)
                close()
            }
            c.drawPath(path, paint)
        }
        paint.color = Color.WHITE
        paint.setShadowLayer(2.5f * d, 0f, 0.5f * d, 0x66000000)
        c.drawCircle(cx, cx, r + border, paint)
        paint.clearShadowLayer()
        paint.color = 0xFFFA453A.toInt()
        c.drawCircle(cx, cx, r, paint)
        bmp
    }

    /** Yellow navigator arrow used in guidance mode. */
    fun navArrow(context: Context): Bitmap = cache.getOrPut("navArrow") {
        val d = context.resources.displayMetrics.density
        val w = 46f * d
        val h = 52f * d
        val pad = 6f * d
        val bmp = createBitmap((w + pad * 2).toInt(), (h + pad * 2).toInt())
        val c = Canvas(bmp)
        val cx = pad + w / 2
        val top = pad
        val bottom = pad + h
        val notch = pad + h * 0.78f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val outline = Path().apply {
            moveTo(cx, top); lineTo(pad + w, bottom); lineTo(cx, notch); lineTo(pad, bottom); close()
        }
        paint.color = 0xFFE59A00.toInt()
        paint.setShadowLayer(4 * d, 0f, 2 * d, 0x66000000)
        c.drawPath(outline, paint)
        paint.clearShadowLayer()
        paint.color = 0xFFFFD84D.toInt()
        c.drawPath(Path().apply { moveTo(cx, top); lineTo(cx, notch); lineTo(pad, bottom); close() }, paint)
        paint.color = 0xFFF2A900.toInt()
        c.drawPath(Path().apply { moveTo(cx, top); lineTo(pad + w, bottom); lineTo(cx, notch); close() }, paint)
        bmp
    }

    /**
     * Route balloon: "21 min / 10 km" on blue for the selected route,
     * "+6 min" on white for alternatives. The tail points down-left; see [balloonAnchor].
     */
    fun routeBalloon(context: Context, title: String, subtitle: String?, selected: Boolean, titleColor: Int): Bitmap =
        cache.getOrPut("balloon:$title:$subtitle:$selected:$titleColor") {
            val d = context.resources.displayMetrics.density
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 17f * d
                typeface = typeface(context, bold = !selected)
                color = titleColor
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 16f * d
                typeface = typeface(context, bold = false)
                color = if (selected) 0xB3FFFFFF.toInt() else 0xFF8E8E8E.toInt()
            }
            val padX = 12f * d
            val padY = 9f * d
            val lineGap = 3f * d
            val tail = 10f * d
            val shadow = 4f * d
            val tw = titlePaint.measureText(title)
            val sw = subtitle?.let { subPaint.measureText(it) } ?: 0f
            val boxW = max(tw, sw) + padX * 2
            val th = -titlePaint.ascent() + titlePaint.descent()
            val sh = if (subtitle != null) -subPaint.ascent() + subPaint.descent() + lineGap else 0f
            val boxH = th + sh + padY * 2
            val w = (boxW + shadow * 2).toInt()
            val h = (boxH + tail + shadow * 2).toInt()
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            val rect = RectF(shadow, shadow, shadow + boxW, shadow + boxH)
            val radius = 12f * d
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = if (selected) 0xFF196DFF.toInt() else Color.WHITE
            paint.setShadowLayer(shadow, 0f, d, 0x40000000)
            val path = Path().apply {
                addRoundRect(rect, radius, radius, Path.Direction.CW)
                // Tail at the bottom-left corner, pointing to the route.
                moveTo(rect.left, rect.bottom - radius)
                lineTo(rect.left, rect.bottom + tail)
                lineTo(rect.left + radius * 1.4f, rect.bottom)
                close()
            }
            c.drawPath(path, paint)
            paint.clearShadowLayer()
            var y = rect.top + padY - titlePaint.ascent()
            c.drawText(title, rect.left + padX, y, titlePaint)
            if (subtitle != null) {
                y += titlePaint.descent() + lineGap - subPaint.ascent()
                c.drawText(subtitle, rect.left + padX, y, subPaint)
            }
            bmp
        }

    /** Anchor of [routeBalloon]: the tail tip. */
    fun balloonAnchor(context: Context, bmp: Bitmap): PointF {
        val shadow = 4f * context.resources.displayMetrics.density
        return PointF(shadow / bmp.width, (bmp.height - shadow) / bmp.height)
    }
}
