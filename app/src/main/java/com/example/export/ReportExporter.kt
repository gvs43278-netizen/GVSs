package com.example.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.BusinessConstants
import com.example.domain.BusinessTransaction
import com.example.domain.MoneyFormatter
import com.example.domain.TxStatus
import com.example.domain.TxType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

object ReportExporter {

    fun writeTransactionsCsv(
        outputStream: OutputStream,
        title: String,
        summaryLines: List<Pair<String, String>>,
        transactions: List<BusinessTransaction>,
    ) {
        // UTF-8 BOM ensures Excel opens Bengali text without encoding issues
        outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val sb = StringBuilder()
        sb.appendLine(csvRow(listOf(BusinessConstants.APP_NAME_BN, title)))
        sb.appendLine(csvRow(listOf("তৈরির সময়", MoneyFormatter.formatDateTime(System.currentTimeMillis()))))
        sb.appendLine()
        summaryLines.forEach { (k, v) ->
            sb.appendLine(csvRow(listOf(k, v)))
        }
        sb.appendLine()
        sb.appendLine(
            csvRow(
                listOf(
                    "লেনদেন আইডি",
                    "তারিখ ও সময়",
                    "ধরন",
                    "অবস্থা",
                    "গ্রাহক/বিবরণ",
                    "গ্রহণ অ্যাকাউন্ট",
                    "গ্রহণ (৳)",
                    "প্রদান অ্যাকাউন্ট",
                    "প্রদান (৳)",
                    "কমিশন আয় (৳)",
                    "পার্টনার কমিশন ব্যয় (৳)",
                    "তৈরি করেছেন",
                    "অনুমোদন করেছেন",
                    "অ্যাকাউন্ট পরিবর্তন",
                    "নোট",
                )
            )
        )
        transactions.forEach { tx ->
            sb.appendLine(
                csvRow(
                    listOf(
                        tx.id,
                        MoneyFormatter.formatDateTime(tx.occurredAtMillis),
                        TxType.fromRaw(tx.txType).labelBn,
                        TxStatus.fromRaw(tx.status).labelBn,
                        tx.customerName ?: tx.expenseCategory ?: "",
                        tx.receivedAccountId?.let { BusinessConstants.accountDisplayName(it) } ?: "",
                        MoneyFormatter.formatTakaPlain(tx.receivedAmountPaisa),
                        tx.paidAccountId?.let { BusinessConstants.accountDisplayName(it) } ?: "",
                        MoneyFormatter.formatTakaPlain(tx.paidAmountPaisa),
                        MoneyFormatter.formatTakaPlain(tx.manualCommissionPaisa),
                        MoneyFormatter.formatTakaPlain(tx.partnerCommissionPaisa),
                        tx.createdByName,
                        tx.approvedBy ?: "",
                        tx.accountChangesSummary ?: "",
                        tx.notes ?: "",
                    )
                )
            )
        }
        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    fun writeReportPdf(
        outputStream: OutputStream,
        title: String,
        summaryLines: List<Pair<String, String>>,
        transactions: List<BusinessTransaction>,
        context: Context? = null,
    ) {
        val bengaliBase = context?.let {
            runCatching { ResourcesCompat.getFont(it, R.font.noto_sans_bengali) }.getOrNull()
        }
        val boldTypeface = bengaliBase?.let { Typeface.create(it, Typeface.BOLD) }

        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val maxContentWidth = pageWidth - margin * 2

        val titlePaint = Paint().apply {
            color = Color.rgb(10, 92, 75)
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
            if (boldTypeface != null) typeface = boldTypeface
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10.5f
            isFakeBoldText = true
            isAntiAlias = true
            if (boldTypeface != null) typeface = boldTypeface
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            isAntiAlias = true
            if (bengaliBase != null) typeface = bengaliBase
        }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin + 12f

        fun newPage() {
            pdf.finishPage(page)
            pageNum += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y = margin + 12f
        }

        fun drawWrappedLine(text: String, x: Float, paint: Paint, lineStep: Float) {
            val maxW = (pageWidth - margin - x).coerceAtLeast(100f)
            var remaining = text
            while (remaining.isNotEmpty()) {
                if (y > pageHeight - 44f) newPage()
                val count = paint.breakText(remaining, true, maxW, null).coerceAtLeast(1)
                val chunk = remaining.substring(0, count)
                canvas.drawText(chunk, x, y, paint)
                y += lineStep
                remaining = remaining.substring(count).trimStart()
            }
        }

        drawWrappedLine("${BusinessConstants.APP_NAME_BN} — $title", margin, titlePaint, 18f)
        drawWrappedLine("তৈরির সময়: ${MoneyFormatter.formatDateTime(System.currentTimeMillis())}", margin, bodyPaint, 13f)
        canvas.drawLine(margin, y, margin + maxContentWidth, y, linePaint)
        y += 16f

        summaryLines.forEach { (label, value) ->
            drawWrappedLine("$label: $value", margin, headerPaint, 15f)
        }

        y += 6f
        canvas.drawLine(margin, y, margin + maxContentWidth, y, linePaint)
        y += 16f
        drawWrappedLine("লেনদেন তালিকা (${transactions.size}টি)", margin, headerPaint, 16f)

        transactions.forEachIndexed { index, tx ->
            if (y > pageHeight - 75f) newPage()
            val typeLabel = TxType.fromRaw(tx.txType).labelBn
            val statusLabel = TxStatus.fromRaw(tx.status).labelBn
            val dateLabel = MoneyFormatter.formatDateTime(tx.occurredAtMillis)
            val line1 = "${index + 1}. [$statusLabel] $typeLabel | $dateLabel | ID: ${tx.id.take(12)}"
            drawWrappedLine(line1, margin, headerPaint, 13f)

            val recv = if (tx.receivedAmountPaisa > 0) {
                "গ্রহণ: ${BusinessConstants.accountDisplayName(tx.receivedAccountId)} ${MoneyFormatter.formatTaka(tx.receivedAmountPaisa)}"
            } else ""
            val paid = if (tx.paidAmountPaisa > 0) {
                "প্রদান: ${BusinessConstants.accountDisplayName(tx.paidAccountId)} ${MoneyFormatter.formatTaka(tx.paidAmountPaisa)}"
            } else ""
            val comm = if (tx.manualCommissionPaisa > 0) {
                "কমিশন: ${MoneyFormatter.formatTaka(tx.manualCommissionPaisa)}"
            } else ""
            val line2 = listOf(recv, paid, comm).filter { it.isNotBlank() }.joinToString(" | ")
            if (line2.isNotBlank()) {
                drawWrappedLine(line2, margin + 10f, bodyPaint, 13f)
            }
            val party = tx.customerName ?: tx.notes ?: ""
            if (party.isNotBlank()) {
                drawWrappedLine("বিবরণ: $party (${tx.createdByName})", margin + 10f, bodyPaint, 13f)
            }
            y += 4f
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        outputStream.flush()
        pdf.close()
    }

    fun printReport(
        context: Context,
        title: String,
        summaryLines: List<Pair<String, String>>,
        transactions: List<BusinessTransaction>,
    ) {
        val tempPdf = File(context.cacheDir, "print_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(tempPdf).use { out ->
            writeReportPdf(out, title, summaryLines, transactions, context = context)
        }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("BusinessManagerPro_Report.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                try {
                    FileInputStream(tempPdf).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        printManager.print("${BusinessConstants.APP_NAME_BN} - $title", adapter, null)
    }

    private fun csvRow(cols: List<String>): String =
        cols.joinToString(",") { cell ->
            val escaped = cell.replace("\"", "\"\"")
            "\"$escaped\""
        }
}
