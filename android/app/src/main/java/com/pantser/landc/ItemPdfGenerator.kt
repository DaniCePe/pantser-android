package com.pantser.landc

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * 2x2 item-label sheets (port of the web app's v1.7.0 generateItemPdf).
 *
 * One sheet holds 4 identical 3.5 x 5in box labels on US Letter, each
 * drawn readable-first in landscape (logo on top, then ITEM # /
 * DESCRIPTION / QTY) and rotated into its cell at an absolute
 * position, so a long description wraps inside its own label and can
 * never spill into another cell. Logo and Tinos fonts are bundled in
 * assets/ from the same files the website ships.
 *
 * Geometry note: Android's PDF canvas has its origin at the top-left
 * with y pointing down, while pdf-lib's origin is bottom-left with y
 * pointing up. The web transform maps a landscape point (a right,
 * b up) to page (cellX + 252 - b, cellY + a). In y-down terms, with
 * bDown = 252 - b, that is (cellX + bDown, 792 - cellY - a) — exactly
 * what translate(cellX, 792 - cellY) + rotate(-90) produces, so the
 * printed sheet is identical to the web one.
 */
object ItemPdfGenerator {

    private const val CELL_W = 252f
    private const val CELL_H = 360f
    private const val GUTTER = 36f
    private const val MARGIN_X = 36f
    private const val LABEL_W = 360f
    private const val LABEL_H = 252f
    private const val PAD = 18f
    private const val LOGO_W = 324f
    private const val LOGO_H = LOGO_W * 245f / 766f

    /* Cell bottom-left corners in PDF (y-up) coordinates, same as web. */
    private val CELLS = listOf(
        MARGIN_X to 396f,
        MARGIN_X + CELL_W + GUTTER to 396f,
        MARGIN_X to 36f,
        MARGIN_X + CELL_W + GUTTER to 36f
    )

    private fun textPaint(typeface: Typeface, size: Float): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            color = 0xFF000000.toInt()
        }
    }

    /* Greedy word wrap, mirroring web's wrapValueWords: every line gets
       the same usable width, and continuation lines start at the
       hanging indent (the label width), never under the label. */
    private fun wrapWords(words: List<String>, paint: Paint, maxW: Float): List<String> {
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            val trial = if (cur.isEmpty()) w else "$cur $w"
            if (cur.isNotEmpty() && paint.measureText(trial) > maxW) {
                lines.add(cur)
                cur = w
            } else {
                cur = trial
            }
        }
        if (cur.isNotEmpty() || lines.isEmpty()) lines.add(cur)
        return lines
    }

    fun generate(context: Context, item: String, description: String, qty: String, out: OutputStream) {
        val reg = Typeface.createFromAsset(context.assets, "fonts/Tinos-Regular.ttf")
        val bold = Typeface.createFromAsset(context.assets, "fonts/Tinos-Bold.ttf")
        val logo = BitmapFactory.decodeStream(context.assets.open("logo_readable.jpg"))
        val bold20 = textPaint(bold, 20f)
        val reg20 = textPaint(reg, 20f)
        val reg18 = textPaint(reg, 18f)

        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            fun drawLabel() {
                canvas.drawBitmap(logo, null, RectF(PAD, PAD, PAD + LOGO_W, PAD + LOGO_H), null)
                var yTop = PAD + LOGO_H + 12f
                val rows = listOf(
                    Triple("ITEM #:", item, reg20),
                    Triple("DESCRIPTION:", description, reg18),
                    Triple("QTY:", qty, reg20)
                )
                for ((label, value, valuePaint) in rows) {
                    val indent = bold20.measureText("$label ")
                    val avail = LABEL_W - 2 * PAD - indent
                    val words = value.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    val lines = wrapWords(words, valuePaint, avail)
                    for ((li, line) in lines.withIndex()) {
                        val baseline = yTop + 20f + li * 27f
                        if (li == 0) canvas.drawText(label, PAD, baseline, bold20)
                        canvas.drawText(line, PAD + indent, baseline, valuePaint)
                    }
                    yTop += lines.size * 27f + 8f
                }
            }

            for ((cellX, cellYup) in CELLS) {
                canvas.save()
                canvas.translate(cellX, 792f - cellYup)
                canvas.rotate(-90f)
                drawLabel()
                canvas.restore()
            }
            document.finishPage(page)
            document.writeTo(out)
        } finally {
            document.close()
        }
    }
}
