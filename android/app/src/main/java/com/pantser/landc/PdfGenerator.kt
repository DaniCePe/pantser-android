package com.pantser.landc

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the pant-size grid as a US Letter (8.5 x 11 in) PDF.
 * Mirrors the original Pydroid/fpdf script: 4 columns x 15 rows,
 * 0.3" side margins, 0.55" top margin, bold 24pt centered text.
 *
 * Barcode mode (port of the web app's v1.4.0/v1.4.1, set C auto-switch
 * v1.5.0 mirroring web v1.7.2): each cell becomes a Code 128 barcode
 * with the size printed underneath in plain text, sized to the cell
 * with the web's taller, narrower grocery-store proportions. Even-
 * length digit strings encode as set C (digit pairs); anything else
 * as set B. Text set B cannot encode falls back to the normal text
 * cell, exactly like web.
 */
object PdfGenerator {

    // Letter page size in PDF points (1/72 of an inch)
    private const val PAGE_WIDTH_PT = 612f   // 8.5 * 72
    private const val PAGE_HEIGHT_PT = 792f  // 11 * 72

    // Margins in inches, matching the original script
    private const val MARGIN_LEFT_IN = 0.3f
    private const val MARGIN_TOP_IN = 0.55f
    private const val MARGIN_RIGHT_IN = 0.3f
    private const val MARGIN_BOTTOM_IN = 0.47f

    private const val FONT_SIZE_PT = 24f

    fun generate(sizeTexts: List<String>, out: OutputStream, cols: Int = 4, rows: Int = 15, barcode: Boolean = false) {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH_PT.toInt(), PAGE_HEIGHT_PT.toInt(), 1,
            ).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Bold sans-serif at 24pt, centered horizontally in each cell.
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT_BOLD
                textSize = FONT_SIZE_PT
                textAlign = Paint.Align.CENTER
                color = Color.BLACK
            }
            // Regular sans-serif for barcode captions, and a fill paint
            // for the bars.
            val regPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
                color = Color.BLACK
            }
            val barPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }

            val leftPt = MARGIN_LEFT_IN * 72f
            val topPt = MARGIN_TOP_IN * 72f
            val rightPt = MARGIN_RIGHT_IN * 72f
            val bottomPt = MARGIN_BOTTOM_IN * 72f

            val usableWidth = PAGE_WIDTH_PT - leftPt - rightPt
            val usableHeight = PAGE_HEIGHT_PT - topPt - bottomPt
            val cellWidth = usableWidth / cols
            val cellHeight = usableHeight / rows

            // Baseline offset so text is vertically centered in each cell
            val fm = paint.fontMetrics
            val baselineOffset = -(fm.ascent + fm.descent) / 2f

            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val sizeText = sizeTexts.getOrElse(col) { sizeTexts.getOrNull(0) ?: "" }
                    if (sizeText.isEmpty()) continue
                    val cellLeft = leftPt + col * cellWidth
                    val cellTop = topPt + row * cellHeight

                    val runs = if (barcode) Code128.runs(sizeText) else null
                    if (runs != null) {
                        var capSize = 11f
                        regPaint.textSize = capSize
                        while (capSize > 6f && regPaint.measureText(sizeText) > cellWidth * 0.94f) {
                            capSize -= 0.5f
                            regPaint.textSize = capSize
                        }
                        val gap = 3f
                        val barH = min(cellHeight * 0.66f, min(36f, cellHeight - gap - capSize - 1f))
                        val modules = runs.sum()
                        val moduleW = min(
                            cellWidth * 0.94f / (modules + 20),
                            min(2.6f, max(0.6f, barH * 3.2f / (modules + 20)))
                        )
                        val codeW = moduleW * (modules + 20)
                        val blockH = barH + gap + capSize
                        val topEdge = cellTop + (cellHeight - blockH) / 2f
                        var bx = cellLeft + (cellWidth - codeW) / 2f + 10f * moduleW
                        for ((i, w) in runs.withIndex()) {
                            if (i % 2 == 0) {
                                canvas.drawRect(bx, topEdge, bx + w * moduleW, topEdge + barH, barPaint)
                            }
                            bx += w * moduleW
                        }
                        // Caption baseline: glyph bottoms land on the
                        // block's bottom edge, like the web rendering.
                        regPaint.textSize = capSize
                        val capFm = regPaint.fontMetrics
                        canvas.drawText(
                            sizeText,
                            cellLeft + cellWidth / 2f,
                            topEdge + barH + gap + capSize - capFm.descent,
                            regPaint
                        )
                        continue
                    }

                    val cx = leftPt + col * cellWidth + cellWidth / 2f
                    val cy = topPt + row * cellHeight + cellHeight / 2f
                    canvas.drawText(sizeText, cx, cy + baselineOffset, paint)
                }
            }

            document.finishPage(page)
            document.writeTo(out)
        } finally {
            document.close()
        }
    }
}
