package no.nav.helsearbeidsgiver.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream

class PdfBuilder(
    val fontName: String = "Source Sans Pro Regular 400.ttf",
    val fontBold: String = "Source Sans Pro SemiBold 600.ttf",
    val fontItalic: String = "Source Sans Pro Italic 400.ttf"
) {

    private val list: MutableList<Text> = mutableListOf()
    private val LINETEXT = "-----"
    private val RATIO = 0.5f
    private val MAX = 780
    // Bredde = ? ,  Høyde = 1560

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
        return add(Text(16, title, false, false, x, y))
    }

    fun add(text: Text): PdfBuilder {
        list.add(text)
        return this
    }

    fun export(): ByteArray {
        val doc = PDDocument()
        val FONT_NORMAL = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontName).openStream())
        val FONT_BOLD = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontBold).openStream())
        val FONT_ITALIC = PDType0Font.load(doc, this::class.java.classLoader.getResource(fontBold).openStream())
        val out = ByteArrayOutputStream()
        val page = PDPage()
        doc.addPage(page)
        val contentStream = PDPageContentStream(doc, page)
        list.forEach {
            if (LINETEXT.equals(it.value)) {
                contentStream.moveTo(it.x.toFloat() * RATIO, MAX - (it.y.toFloat() - 20) * RATIO)
                contentStream.lineTo(585f, MAX - (it.y.toFloat() - 20) * RATIO)
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
                contentStream.newLineAtOffset(it.x.toFloat() * RATIO, MAX - it.y.toFloat() * RATIO)
                contentStream.showText(it.value)
                contentStream.endText()
            }
        }
        contentStream.close()
        doc.save(out)
        val ba = out.toByteArray()
        doc.close()
        return ba
    }
}

data class Text(val fontSize: Int, val value: String, val bold: Boolean = false, val italic: Boolean = false, val x: Int = 0, val y: Int = 0)
