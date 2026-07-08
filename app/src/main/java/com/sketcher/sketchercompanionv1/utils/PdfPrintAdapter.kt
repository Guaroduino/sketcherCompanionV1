package com.sketcher.sketchercompanionv1.utils

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * PrintDocumentAdapter implementation that reads a generated temporary PDF file
 * and copies its contents to the print queue destination.
 */
class PdfPrintAdapter(
    private val context: Context,
    private val tempPdfFile: File,
    private val documentName: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        // Inform the print system about the document's basic metadata
        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1) // Single page drawing representation
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            cleanup()
            return
        }

        var input: FileInputStream? = null
        var output: FileOutputStream? = null

        try {
            input = FileInputStream(tempPdfFile)
            output = FileOutputStream(destination.fileDescriptor)
            
            input.copyTo(output)
            
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            e.printStackTrace()
            callback?.onWriteFailed(e.message)
        } finally {
            try {
                input?.close()
            } catch (e: Exception) {}
            try {
                output?.close()
            } catch (e: Exception) {}
        }
    }

    override fun onFinish() {
        super.onFinish()
        cleanup()
    }

    private fun cleanup() {
        if (tempPdfFile.exists()) {
            tempPdfFile.delete()
        }
    }
}
