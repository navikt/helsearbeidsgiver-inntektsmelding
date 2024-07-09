package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream

class PdfBuilder(
    private val paddingVertical: Int = 100,
    private val paddingHorisontal: Int = 45,
    private val fontName: String = "Source Sans Pro Regular 400.ttf",
    private val fontBold: String = "Source Sans Pro SemiBold 600.ttf",
    private val fontItalic: String = "Source Sans Pro Italic 400.ttf",
    val titleSize: Int = 30,
    val sectionSize: Int = 24,
    val bodySize: Int = 16,
    private val logo: String = "logo.png",
    private val topText: String? = null,
) {
    private val list: MutableList<Text> = mutableListOf()
    private val linetext = "-----"
    private val ratio = 0.5f
    private val max = 780
    private val pageHeight = 1560 - paddingVertical * 2 // 1560
    private val pageWidth = 630f

    fun addTitle(
        title: String,
        x: Int = 0,
        y: Int = 0,
    ): PdfBuilder = add(Text(titleSize, title, bold = false, italic = false, x, y))

    fun addSection(
        title: String,
        x: Int = 0,
        y: Int = 0,
    ): PdfBuilder = add(Text(sectionSize, title, bold = false, italic = false, x, y))

    fun addBody(
        title: String,
        x: Int = 0,
        y: Int = 0,
    ): PdfBuilder = add(Text(bodySize, title, bold = false, italic = false, x, y))

    fun addBold(
        title: String,
        x: Int = 0,
        y: Int = 0,
    ): PdfBuilder = add(Text(bodySize, title, bold = true, italic = false, x, y))

    fun addText(
        title: String,
        x: Int = 0,
        y: Int = 0,
        bold: Boolean = false,
    ): PdfBuilder = add(Text(bodySize, title, bold, italic = false, x, y))

    fun addLine(
        x: Int = 0,
        y: Int = 0,
    ): PdfBuilder = add(Text(1, linetext, bold = false, italic = false, x, y))

    fun addItalics(
        title: String,
        x: Int = 0,
        y: Int = 0,
    ): PdfBuilder = add(Text(bodySize, title, bold = false, italic = true, x, y))

    private fun add(text: Text): PdfBuilder {
        list.add(text)
        return this
    }

    private fun calculatePageCount(): Int = list.maxOf { it.y } / pageHeight

    private fun isPage(
        y: Int,
        pageNumber: Int,
    ): Boolean = y >= pageNumber * pageHeight && y < (pageNumber + 1) * pageHeight

    private fun addTopText(
        contentStream: PDPageContentStream,
        font: PDType0Font,
        text: String,
    ) {
        contentStream.beginText()
        Text(bodySize, text, bold = false, italic = true, 0 + paddingHorisontal / 2, max).also { text ->
            contentStream.setFont(font, text.fontSize.toFloat() * ratio)
            contentStream.newLineAtOffset(text.x.toFloat(), text.y.toFloat())
            contentStream.showText(text.value)
        }
        contentStream.endText()
    }

    private fun producePage(
        pageNr: Int,
        doc: PDDocument,
        fontNormal: PDType0Font,
        fontBold: PDType0Font,
        fontItalic: PDType0Font,
    ): PDPage {
        val page = PDPage()
        val contentStream = PDPageContentStream(doc, page)

        if (pageNr == 0) {
            val stream =
                this::class.java.classLoader
                    .getResource(logo)!!
                    .openStream()
            val pdImage = PDImageXObject.createFromByteArray(doc, stream.readAllBytes(), logo)
            val w = pdImage.width.toFloat() / 3
            val h = pdImage.height.toFloat() / 3
            val logoX = pageWidth - w - paddingHorisontal
            val logoY = max - h
            contentStream.drawImage(pdImage, logoX, logoY, w, h)
            topText?.also { addTopText(contentStream, fontItalic, it) }
        }

        val filteredList = list.filter { isPage(it.y, pageNr) }
        val firstY = if (pageNr == 0) 0 else filteredList.minOf { it.y }
        filteredList.forEach {
            if (linetext == it.value) {
                contentStream.moveTo((paddingHorisontal + it.x.toFloat()) * ratio, max - (paddingVertical + it.y.toFloat() - 20 - firstY) * ratio)
                contentStream.lineTo((pageWidth - paddingHorisontal), max - (paddingVertical + it.y.toFloat() - 20 - firstY) * ratio)
                contentStream.stroke()
            } else {
                contentStream.beginText()
                if (it.italic) {
                    contentStream.setFont(fontItalic, it.fontSize.toFloat() * ratio)
                } else if (it.bold) {
                    contentStream.setFont(fontBold, it.fontSize.toFloat() * ratio)
                } else {
                    contentStream.setFont(fontNormal, it.fontSize.toFloat() * ratio)
                }
                contentStream.newLineAtOffset((paddingHorisontal + it.x.toFloat()) * ratio, max - (paddingVertical + it.y.toFloat() - firstY) * ratio)
                contentStream.showText(it.value)
                contentStream.endText()
            }
        }
        contentStream.close()
        return page
    }

    fun export(): ByteArray {
        val doc = PDDocument()
        val out = ByteArrayOutputStream()
        val pageCount = calculatePageCount()
        val fontNormal =
            PDType0Font.load(
                doc,
                this::class.java.classLoader
                    .getResource(fontName)!!
                    .openStream(),
            )
        val fontBold =
            PDType0Font.load(
                doc,
                this::class.java.classLoader
                    .getResource(fontBold)!!
                    .openStream(),
            )
        val fontItalic =
            PDType0Font.load(
                doc,
                this::class.java.classLoader
                    .getResource(fontItalic)!!
                    .openStream(),
            )
        for (i in 0..pageCount) {
            doc.addPage(producePage(i, doc, fontNormal, fontBold, fontItalic))
        }
        doc.save(out)
        val ba = out.toByteArray()
        doc.close()
        return ba
    }
}

data class Text(
    val fontSize: Int,
    val value: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val x: Int = 0,
    val y: Int = 0,
)
