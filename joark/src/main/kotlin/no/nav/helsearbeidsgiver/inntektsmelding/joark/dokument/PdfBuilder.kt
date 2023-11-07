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
    private val topText: String? = null
) {

    private val list: MutableList<Text> = mutableListOf()
    private val LINETEXT = "-----"
    private val RATIO = 0.5f
    private val MAX = 780
    private val PAGE_HEIGHT = 1560 - paddingVertical * 2 // 1560
    private val PAGE_WIDTH = 630f

    fun addTitle(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(titleSize, title, bold = false, italic = false, x, y))
    }

    fun addSection(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(sectionSize, title, bold = false, italic = false, x, y))
    }

    fun addBody(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(bodySize, title, bold = false, italic = false, x, y))
    }

    fun addBold(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(bodySize, title, bold = true, italic = false, x, y))
    }

    fun addText(title: String, x: Int = 0, y: Int = 0, bold: Boolean = false): PdfBuilder {
        return add(Text(bodySize, title, bold, italic = false, x, y))
    }

    fun addLine(x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(1, LINETEXT, bold = false, italic = false, x, y))
    }

    fun addItalics(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(bodySize, title, bold = false, italic = true, x, y))
    }

    private fun add(text: Text): PdfBuilder {
        list.add(text)
        return this
    }

    private fun calculatePageCount(): Int {
        return list.maxOf { it.y } / PAGE_HEIGHT
    }

    private fun isPage(y: Int, pageNumber: Int): Boolean {
        return y >= pageNumber * PAGE_HEIGHT && y < (pageNumber + 1) * PAGE_HEIGHT
    }

    private fun addTopText(contentStream: PDPageContentStream, font: PDType0Font, text: String) {
        contentStream.beginText()
        Text(bodySize, text, bold = false, italic = true, 0 + paddingHorisontal / 2, MAX).also { text ->
            contentStream.setFont(font, text.fontSize.toFloat() * RATIO)
            contentStream.newLineAtOffset(text.x.toFloat(), text.y.toFloat())
            contentStream.showText(text.value)
        }
        contentStream.endText()
    }

    private fun producePage(pageNr: Int, doc: PDDocument, FONT_NORMAL: PDType0Font, FONT_BOLD: PDType0Font, FONT_ITALIC: PDType0Font): PDPage {
        val page = PDPage()
        val contentStream = PDPageContentStream(doc, page)

        if (pageNr == 0) {
            val stream = this::class.java.classLoader.getResource(logo)!!.openStream()
            val pdImage = PDImageXObject.createFromByteArray(doc, stream.readAllBytes(), logo)
            val w = pdImage.width.toFloat() / 3
            val h = pdImage.height.toFloat() / 3
            val logoX = PAGE_WIDTH - w - paddingHorisontal
            val logoY = MAX - h
            contentStream.drawImage(pdImage, logoX, logoY, w, h)
            topText?.also { addTopText(contentStream, FONT_ITALIC, it) }
        }

        val filteredList = list.filter { isPage(it.y, pageNr) }
        val firstY = if (pageNr == 0) 0 else filteredList.minOf { it.y }
        filteredList.forEach {
            if (LINETEXT == it.value) {
                contentStream.moveTo((paddingHorisontal + it.x.toFloat()) * RATIO, MAX - (paddingVertical + it.y.toFloat() - 20 - firstY) * RATIO)
                contentStream.lineTo((PAGE_WIDTH - paddingHorisontal), MAX - (paddingVertical + it.y.toFloat() - 20 - firstY) * RATIO)
                contentStream.stroke()
            } else {
                contentStream.beginText()
                if (it.italic) {
                    contentStream.setFont(FONT_ITALIC, it.fontSize.toFloat() * RATIO)
                } else if (it.bold) {
                    contentStream.setFont(FONT_BOLD, it.fontSize.toFloat() * RATIO)
                } else {
                    contentStream.setFont(FONT_NORMAL, it.fontSize.toFloat() * RATIO)
                }
                contentStream.newLineAtOffset((paddingHorisontal + it.x.toFloat()) * RATIO, MAX - (paddingVertical + it.y.toFloat() - firstY) * RATIO)
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
        val FONT_NORMAL = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontName)!!.openStream())
        val FONT_BOLD = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontBold)!!.openStream())
        val FONT_ITALIC = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontItalic)!!.openStream())
        for (i in 0..pageCount) {
            doc.addPage(producePage(i, doc, FONT_NORMAL, FONT_BOLD, FONT_ITALIC))
        }
        doc.save(out)
        val ba = out.toByteArray()
        doc.close()
        return ba
    }
}

data class Text(val fontSize: Int, val value: String, val bold: Boolean = false, val italic: Boolean = false, val x: Int = 0, val y: Int = 0)
