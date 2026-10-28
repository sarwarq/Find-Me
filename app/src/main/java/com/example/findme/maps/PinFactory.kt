package com.example.findme.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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

/** Draws the marker bitmaps of the Rendezvous design (see the "Map controls & pins" board). */
object PinFactory {

    private const val INK = 0xFF10141C.toInt()
    private const val BLUE = 0xFF2F55F4.toInt()
    private const val BLUE_DARK = 0xFF4D6BFF.toInt()
    private const val CORAL = 0xFFFF6B4A.toInt()
    private const val GREEN = 0xFF16A05A.toInt()
    private const val GRAY_TEXT = 0xFF5B6573.toInt()
    /** Soft shadow under pins: ink at 28 %. */
    private const val SHADOW = 0x47101418

    /** Room for the shadow around pins with a tail, in dp; [pinAnchor] relies on it. */
    private const val PIN_SHADOW_DP = 4f

    private val cache = HashMap<String, Bitmap>()
    private val fonts = HashMap<Int, Typeface>()

    /** Onest at [weight] (the font file is variable). */
    private fun typeface(context: Context, weight: Int): Typeface = fonts.getOrPut(weight) {
        val base = ResourcesCompat.getFont(context, R.font.onest) ?: Typeface.DEFAULT
        Typeface.create(base, weight, false)
    }

    private fun Canvas.drawIcon(context: Context, @DrawableRes res: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        d.setTint(color)
        val half = size / 2
        d.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        d.draw(this)
    }

    private fun Context.dp(): Float = resources.displayMetrics.density

    /** Small round marker of a search result: category color with a white icon and ring. */
    fun resultPin(context: Context, style: CategoryStyle): Bitmap =
        cache.getOrPut("result:${style.icon}:${style.color}") {
            val d = context.dp()
            val r = 12f * d
            val ring = 2.5f * d
            val shadow = 3f * d
            val size = ((r + ring + shadow) * 2).toInt()
            val bmp = createBitmap(size, size)
            val c = Canvas(bmp)
            val cx = size / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.WHITE
            paint.setShadowLayer(shadow * 0.8f, 0f, d, SHADOW)
            c.drawCircle(cx, cx, r + ring, paint)
            paint.clearShadowLayer()
            paint.color = style.color
            c.drawCircle(cx, cx, r, paint)
            c.drawIcon(context, style.icon, cx, cx, r * 1.2f, Color.WHITE)
            bmp
        }

    /**
     * The selected place or a route destination: an ink circle with a white ring and the
     * category icon on a short stem. Its anchor is the bottom of the stem ([pinAnchor]).
     */
    fun placePin(context: Context, style: CategoryStyle, circleColor: Int = INK): Bitmap =
        cache.getOrPut("place:${style.icon}:$circleColor") {
            val d = context.dp()
            val r = 22f * d
            val ring = 4f * d
            val stemW = 4f * d
            val stem = 11f * d
            val shadow = PIN_SHADOW_DP * d
            val w = ((r + ring + shadow) * 2).toInt()
            val h = (shadow + (r + ring) * 2 + stem + shadow).toInt()
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            val cx = w / 2f
            val cy = shadow + ring + r
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // Stem first, with a thin white edge so it shows on a dark map too.
            val stemTop = cy + r
            val stemBottom = cy + r + ring + stem
            paint.color = Color.WHITE
            c.drawRoundRect(RectF(cx - stemW / 2 - d, stemTop, cx + stemW / 2 + d, stemBottom + d), stemW, stemW, paint)
            paint.color = circleColor
            c.drawRoundRect(RectF(cx - stemW / 2, stemTop, cx + stemW / 2, stemBottom), stemW / 2, stemW / 2, paint)
            paint.color = Color.WHITE
            paint.setShadowLayer(shadow, 0f, 1.5f * d, SHADOW)
            c.drawCircle(cx, cy, r + ring, paint)
            paint.clearShadowLayer()
            paint.color = circleColor
            c.drawCircle(cx, cy, r, paint)
            c.drawIcon(context, style.icon, cx, cy, r * 1.0f, Color.WHITE)
            bmp
        }

    /** Destination of a route: the same ink pin as a selected place. */
    fun finishPin(context: Context, style: CategoryStyle): Bitmap = placePin(context, style)

    /** Anchor of [placePin], [finishPin] and [memberPin]: the bottom of the stem. */
    fun pinAnchor(context: Context, bmp: Bitmap): PointF {
        val shadow = PIN_SHADOW_DP * context.dp()
        return PointF(0.5f, (bmp.height - shadow) / bmp.height)
    }

    /**
     * Meet point: a coral drop with a white edge and an ink icon (ink on coral: white fails
     * contrast). Anchor: [meetPinAnchor], the tip of the drop.
     */
    fun meetPin(context: Context, @DrawableRes icon: Int = R.drawable.ms_group): Bitmap = cache.getOrPut("meet:$icon") {
        val d = context.dp()
        // The drop is drawn in a 40 x 50 box (the design's units) and scaled up a little.
        val s = 1.1f * d
        val pad = PIN_SHADOW_DP * d
        val w = (40 * s + pad * 2).toInt()
        val h = (50 * s + pad * 2).toInt()
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val drop = Path().apply {
            moveTo(20f, 49f)
            cubicTo(20f, 49f, 2f, 31f, 2f, 19f)
            arcTo(RectF(2f, 1f, 38f, 37f), 180f, 180f, false)
            cubicTo(38f, 31f, 20f, 49f, 20f, 49f)
            close()
            transform(Matrix().apply { setScale(s, s); postTranslate(pad, pad) })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = CORAL
        paint.setShadowLayer(pad * 0.8f, 0f, 1.5f * d, SHADOW)
        c.drawPath(drop, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * d
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.WHITE
        c.drawPath(drop, paint)
        c.drawIcon(context, icon, pad + 20 * s, pad + 19 * s, 20 * s, INK)
        bmp
    }

    fun meetPinAnchor(context: Context, bmp: Bitmap): PointF {
        val d = context.dp()
        return PointF(0.5f, (PIN_SHADOW_DP * d + 49 * 1.1f * d) / bmp.height)
    }

    /**
     * Meet-up member: round photo (or the first letter on their [color]) in a white ring on a
     * short white stem. Green ring when [arrived]; faded when [stale]. Anchor: [pinAnchor].
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
        val d = context.dp()
        val r = 18f * d
        val ring = 3f * d
        val stem = 8f * d
        val stemW = 3f * d
        val shadow = PIN_SHADOW_DP * d
        val w = ((r + ring + shadow) * 2).toInt()
        val h = (shadow + (r + ring) * 2 + stem + shadow).toInt()
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val cx = w / 2f
        val cy = shadow + ring + r
        val alpha = if (stale) 140 else 255
        val ringColor = if (arrived) GREEN else Color.WHITE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = ringColor
        paint.alpha = alpha
        c.drawRect(RectF(cx - stemW / 2, cy + r, cx + stemW / 2, cy + r + ring + stem), paint)
        paint.setShadowLayer(shadow, 0f, 1.5f * d, SHADOW)
        c.drawCircle(cx, cy, r + ring, paint)
        paint.clearShadowLayer()

        if (photo != null) {
            val save = c.save()
            c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
            val side = minOf(photo.width, photo.height)
            val src = android.graphics.Rect(
                (photo.width - side) / 2, (photo.height - side) / 2,
                (photo.width + side) / 2, (photo.height + side) / 2,
            )
            val dst = RectF(cx - r, cy - r, cx + r, cy + r)
            c.drawBitmap(photo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
            c.restoreToCount(save)
        } else {
            paint.color = color
            paint.alpha = alpha
            c.drawCircle(cx, cy, r, paint)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                this.alpha = alpha
                textSize = r * 0.95f
                typeface = typeface(context, 700)
                textAlign = Paint.Align.CENTER
            }
            c.drawText(initial, cx, cy - (text.ascent() + text.descent()) / 2, text)
        }
        bmp
    }

    /** Numbered stop on a route: white number on a blue circle with a white ring. */
    fun stopPin(context: Context, number: Int): Bitmap = cache.getOrPut("stop:$number") {
        val d = context.dp()
        val r = 12f * d
        val ring = 2.5f * d
        val size = ((r + ring + 3 * d) * 2).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.setShadowLayer(2.5f * d, 0f, d, SHADOW)
        c.drawCircle(cx, cx, r + ring, paint)
        paint.clearShadowLayer()
        paint.color = BLUE
        c.drawCircle(cx, cx, r, paint)
        paint.color = Color.WHITE
        paint.textSize = 13f * d
        paint.typeface = typeface(context, 700)
        paint.textAlign = Paint.Align.CENTER
        c.drawText(number.toString(), cx, cx - (paint.descent() + paint.ascent()) / 2, paint)
        bmp
    }

    /** Route start that is not "my location": a white dot with an ink ring. */
    fun startPin(context: Context): Bitmap = cache.getOrPut("start") {
        val d = context.dp()
        val r = 8f * d
        val size = ((r + 3 * d) * 2).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.setShadowLayer(2 * d, 0f, 0.5f * d, SHADOW)
        c.drawCircle(cx, cx, r, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f * d
        paint.color = INK
        c.drawCircle(cx, cx, r - 1.75f * d, paint)
        bmp
    }

    /**
     * Me on the map: a blue dot in a white ring inside a soft blue halo.
     * With [heading] a small blue pointer on the halo shows where I'm facing.
     */
    fun userPin(context: Context, heading: Boolean): Bitmap = cache.getOrPut("user:$heading") {
        val d = context.dp()
        val halo = 22f * d
        val r = 9f * d
        val ring = 3f * d
        val size = ((halo + 2 * d) * 2).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = (BLUE and 0x00FFFFFF) or 0x2E000000
        c.drawCircle(cx, cx, halo, paint)
        if (heading) {
            paint.color = BLUE
            val path = Path().apply {
                moveTo(cx, cx - halo + d)
                lineTo(cx + 6 * d, cx - r - ring - 2 * d)
                lineTo(cx - 6 * d, cx - r - ring - 2 * d)
                close()
            }
            c.drawPath(path, paint)
        }
        paint.color = Color.WHITE
        paint.setShadowLayer(2 * d, 0f, 0.5f * d, SHADOW)
        c.drawCircle(cx, cx, r + ring, paint)
        paint.clearShadowLayer()
        paint.color = BLUE
        c.drawCircle(cx, cx, r, paint)
        bmp
    }

    /** Units of the arrow shape (the design's 40 x 44 box) per dp, and the room around it. */
    private const val ARROW_SCALE = 1.2f
    private const val ARROW_PAD_DP = 6f

    /** My arrow in guidance: white with a blue edge (a brighter blue on the night map). */
    fun navArrow(context: Context, dark: Boolean = false): Bitmap = cache.getOrPut("navArrow:$dark") {
        val d = context.dp()
        val s = ARROW_SCALE * d
        val pad = ARROW_PAD_DP * d
        val bmp = createBitmap((40 * s + pad * 2).toInt(), (44 * s + pad * 2).toInt())
        val c = Canvas(bmp)
        val arrow = Path().apply {
            moveTo(20f, 3f); lineTo(36f, 39f); lineTo(20f, 31f); lineTo(4f, 39f); close()
            transform(Matrix().apply { setScale(s, s); postTranslate(pad, pad) })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.setShadowLayer(4 * d, 0f, 2 * d, 0x66000000)
        c.drawPath(arrow, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * d
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = if (dark) BLUE_DARK else BLUE
        c.drawPath(arrow, paint)
        bmp
    }

    /** The point of [navArrow] that sits on my position: the middle of the shape. */
    fun navArrowAnchor(context: Context, bmp: Bitmap): PointF {
        val d = context.dp()
        return PointF(0.5f, (ARROW_PAD_DP * d + 24 * ARROW_SCALE * d) / bmp.height)
    }

    /**
     * Route balloon: "18 min / 7.4 km" in white on blue for the selected route,
     * "+6 min" on white for alternatives. The tail points down-left; see [balloonAnchor].
     */
    fun routeBalloon(context: Context, title: String, subtitle: String?, selected: Boolean, titleColor: Int): Bitmap =
        cache.getOrPut("balloon:$title:$subtitle:$selected:$titleColor") {
            val d = context.dp()
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 15f * d
                typeface = typeface(context, 700)
                color = titleColor
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f * d
                typeface = typeface(context, 500)
                color = if (selected) 0xD9FFFFFF.toInt() else GRAY_TEXT
            }
            val padX = 10f * d
            val padY = 7f * d
            val lineGap = 1f * d
            val tail = 8f * d
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
            paint.color = if (selected) BLUE else Color.WHITE
            paint.setShadowLayer(shadow, 0f, d, if (selected) 0x592F55F4 else SHADOW)
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
        val shadow = 4f * context.dp()
        return PointF(shadow / bmp.width, (bmp.height - shadow) / bmp.height)
    }
}
