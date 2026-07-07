package com.trin.stonks.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfUtils {
    fun generateAndShareInvoice(
        context: Context,
        invoiceNumber: String,
        senderName: String,
        senderContact: String,
        clientName: String,
        clientAddress: String,
        clientContact: String,
        notes: String,
        items: List<Triple<String, Int, Double>>,
        discount: Double = 0.0,    // NEW parameter
        taxPercent: Double = 0.0,  // NEW parameter
        shareMode: Boolean
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // --- PAINTS ---
        val titlePaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 28f; color = Color.BLACK }
        val headerPaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 12f; color = Color.DKGRAY }
        val textPaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); textSize = 11f; color = Color.BLACK }
        val boldTextPaint = Paint(textPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        val rightAlignText = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightAlignHeader = Paint(headerPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightAlignBold = Paint(boldTextPaint).apply { textAlign = Paint.Align.RIGHT }
        val redTextPaint = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT; color = Color.parseColor("#D32F2F") } // For Discounts

        var yPos = 60f
        val leftMargin = 50f
        val rightMargin = 545f

        // --- HEADER ---
        canvas.drawText("INVOICE", leftMargin, yPos, titlePaint)

        // Sender Info (Right Aligned)
        canvas.drawText(if (senderName.isNotBlank()) senderName else "STONKS APP", rightMargin, yPos - 12f, rightAlignBold)
        if (senderContact.isNotBlank()) {
            canvas.drawText(senderContact, rightMargin, yPos + 3f, rightAlignText)
        }

        yPos += 30f
        canvas.drawLine(leftMargin, yPos, rightMargin, yPos, linePaint)
        yPos += 25f

        // --- INVOICE DETAILS & BILL TO ---
        val topSectionY = yPos

        // Left Column: Bill To
        canvas.drawText("BILL TO:", leftMargin, yPos, headerPaint)
        var leftY = yPos + 15f
        canvas.drawText(clientName, leftMargin, leftY, boldTextPaint)

        if (clientAddress.isNotBlank()) {
            clientAddress.split("\n", ",").forEach { line ->
                leftY += 14f
                canvas.drawText(line.trim(), leftMargin, leftY, textPaint)
            }
        }
        if (clientContact.isNotBlank()) {
            leftY += 14f
            canvas.drawText(clientContact, leftMargin, leftY, textPaint)
        }

        // Right Column: Invoice Details
        val labelAnchor = rightMargin - 90f // Anchors the label safely away from the value
        var rightY = topSectionY

        canvas.drawText("Invoice Number:", labelAnchor, rightY, rightAlignHeader)
        canvas.drawText(invoiceNumber, rightMargin, rightY, rightAlignText)
        rightY += 18f

        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Date of Issue:", labelAnchor, rightY, rightAlignHeader)
        canvas.drawText(dateStr, rightMargin, rightY, rightAlignText)

        // Move cursor below the longest column
        yPos = maxOf(leftY, rightY) + 40f

        // --- TABLE HEADER ---
        canvas.drawRect(leftMargin, yPos - 14f, rightMargin, yPos + 8f, Paint().apply { color = Color.parseColor("#F8F9FA") })
        canvas.drawText("Description", leftMargin + 8f, yPos, headerPaint)
        canvas.drawText("Qty", 360f, yPos, rightAlignHeader)
        canvas.drawText("Unit Price", 450f, yPos, rightAlignHeader)
        canvas.drawText("Total", rightMargin - 8f, yPos, rightAlignHeader)

        yPos += 25f

        // --- TABLE ITEMS ---
        var subtotal = 0.0
        items.forEach { (name, qty, price) ->
            val lineTotal = qty * price
            subtotal += lineTotal

            canvas.drawText(name, leftMargin + 8f, yPos, textPaint)
            canvas.drawText(qty.toString(), 360f, yPos, rightAlignText)
            canvas.drawText("RM ${"%.2f".format(price)}", 450f, yPos, rightAlignText)
            canvas.drawText("RM ${"%.2f".format(lineTotal)}", rightMargin - 8f, yPos, rightAlignText)

            yPos += 10f
            canvas.drawLine(leftMargin, yPos, rightMargin, yPos, linePaint)
            yPos += 18f
        }

        // --- TOTALS & ADJUSTMENTS ---
        val postDiscountTotal = Math.max(0.0, subtotal - discount)
        val taxAmountCalc = postDiscountTotal * (taxPercent / 100.0)
        val finalTotalDue = postDiscountTotal + taxAmountCalc

        yPos += 10f
        val totalsLabelX = 410f

        // Only show Subtotal if there are adjustments
        if (discount > 0 || taxPercent > 0) {
            canvas.drawText("Subtotal:", totalsLabelX, yPos, rightAlignHeader)
            // Use the 'subtotal' variable calculated above, not 'totalAmount'
            canvas.drawText("RM ${"%.2f".format(subtotal)}", rightMargin - 8f, yPos, rightAlignText)
            yPos += 18f
        }

        // Discount Line
        if (discount > 0) {
            val redTextPaint = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT; color = Color.parseColor("#D32F2F") }
            canvas.drawText("Discount:", totalsLabelX, yPos, rightAlignHeader)
            canvas.drawText("- RM ${"%.2f".format(discount)}", rightMargin - 8f, yPos, redTextPaint)
            yPos += 18f
        }

        // Tax Line
        if (taxPercent > 0) {
            canvas.drawText("Tax ($taxPercent%):", totalsLabelX, yPos, rightAlignHeader)
            canvas.drawText("+ RM ${"%.2f".format(taxAmountCalc)}", rightMargin - 8f, yPos, rightAlignText)
            yPos += 18f
        }

        val totalLabelPaint = Paint(headerPaint).apply { textSize = 16f; textAlign = Paint.Align.RIGHT }
        val totalValuePaint = Paint(titlePaint).apply { textSize = 16f; textAlign = Paint.Align.RIGHT }

        canvas.drawText("Total Due:", totalsLabelX, yPos, totalLabelPaint)
        // Typo fixed here!
        canvas.drawText("RM ${"%.2f".format(finalTotalDue)}", rightMargin - 8f, yPos, totalValuePaint)

        // --- NOTES & FOOTER ---
        if (notes.isNotBlank()) {
            yPos += 50f
            canvas.drawText("Notes:", leftMargin, yPos, headerPaint)
            yPos += 15f
            notes.split("\n").forEach { line ->
                canvas.drawText(line.trim(), leftMargin, yPos, textPaint)
                yPos += 14f
            }
        }

        val footerY = 800f
        canvas.drawLine(leftMargin, footerY - 20f, rightMargin, footerY - 20f, linePaint)
        val footerPaint = Paint(textPaint).apply { textAlign = Paint.Align.CENTER; color = Color.GRAY }
        canvas.drawText("Thank you for your business!", pageInfo.pageWidth / 2f, footerY, footerPaint)

        pdfDocument.finishPage(page)

        // --- SAVE AND SHARE/VIEW ---
        val folder = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(folder, "Invoice_${invoiceNumber}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "com.trin.stonks.fileprovider", file)

        val intent = if (shareMode) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooserTitle = if (shareMode) "Share Invoice" else "Preview Invoice"
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}