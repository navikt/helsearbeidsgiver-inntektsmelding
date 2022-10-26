package no.nav.helsearbeidsgiver.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream

class PdfBuilder(
    val paddingVertical: Int = 70,
    val paddingHorisontal: Int = 45,
    val fontName: String = "Source Sans Pro Regular 400.ttf",
    val fontBold: String = "Source Sans Pro SemiBold 600.ttf",
    val fontItalic: String = "Source Sans Pro Italic 400.ttf"
) {

    private val list: MutableList<Text> = mutableListOf()
    private val LINETEXT = "-----"
    private val RATIO = 0.5f
    private val MAX = 780
    private val PAGE_HEIGHT = 1300 // 1560

    fun addTitle(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(20, title, false, false, x, y))
    }

    fun addSection(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(24, title, false, false, x, y))
    }

    fun addBody(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(16, title, false, false, x, y))
    }

    fun addBold(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(16, title, true, false, x, y))
    }

    fun addLine(x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(1, LINETEXT, false, false, x, y))
    }

    fun addFootnote(title: String, x: Int = 0, y: Int = 0): PdfBuilder {
        return add(Text(16, title, false, true, x, y))
    }

    fun add(text: Text): PdfBuilder {
        list.add(text)
        return this
    }

    fun calculatePageCount(): Int {
        return list.maxOf { it.y } / PAGE_HEIGHT
    }

    fun isPage(y: Int, pageNumber: Int): Boolean {
        return y >= pageNumber * PAGE_HEIGHT && y < (pageNumber + 1) * PAGE_HEIGHT
    }

    fun producePage(pageNr: Int, doc: PDDocument): PDPage {
        val FONT_NORMAL = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontName).openStream())
        val FONT_BOLD = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontBold).openStream())
        val FONT_ITALIC = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontItalic).openStream())
        val page = PDPage()
        val contentStream = PDPageContentStream(doc, page)
        if (pageNr == 10000) {
            contentStream.addRect(456f * RATIO, MAX - 390f * RATIO, 550f * RATIO, -300f * RATIO)
            contentStream.stroke()
        }
        val filteredList = list.filter { isPage(it.y, pageNr) }
        val firstY = if (pageNr == 0) 0 else filteredList.minOf { it.y }
        filteredList.forEach {
            if (LINETEXT.equals(it.value)) {
                contentStream.moveTo(it.x.toFloat() * RATIO, MAX - (it.y.toFloat() - 20 - firstY) * RATIO)
                contentStream.lineTo(620f, MAX - (it.y.toFloat() - 20 - firstY) * RATIO)
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
                contentStream.newLineAtOffset(it.x.toFloat() * RATIO, MAX - (it.y.toFloat() - firstY) * RATIO)
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
        for (i in 0..pageCount) {
            doc.addPage(producePage(i, doc))
        }
        doc.save(out)
        val ba = out.toByteArray()
        doc.close()
        return ba
    }
}

data class Text(val fontSize: Int, val value: String, val bold: Boolean = false, val italic: Boolean = false, val x: Int = 0, val y: Int = 0)
