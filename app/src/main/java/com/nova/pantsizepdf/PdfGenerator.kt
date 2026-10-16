package com.nova.pantsizepdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Draws the pant-size grid as a US Letter (8.5 x 11 in) PDF.
 * Mirrors the original Pydroid/fpdf script: 4 columns x 15 rows,
 * 0.3" side margins, 0.55" top margin, bold 24pt centered text.
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

    fun generate(sizeTexts: List<String>, out: OutputStream, cols: Int = 4, rows: Int = 15) {
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
