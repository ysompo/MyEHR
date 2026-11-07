package com.myehr.app.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.myehr.app.data.Page
import com.myehr.app.ui.DocPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

private const val MAX_SIDE = 2000

/** Shrinks a picture to at most 2000 px and re-encodes it as JPEG. */
fun shrinkImage(bytes: ByteArray): Page {
    val src = ImageIO.read(bytes.inputStream()) ?: error("not an image")
    val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(src.width, src.height))
    val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    out.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null); dispose()
    }
    val buf = ByteArrayOutputStream()
    ImageIO.write(out, "jpg", buf)
    return Page(buf.toByteArray(), "image/jpeg")
}

fun readFile(f: File): Page? = runCatching {
    if (f.extension.equals("pdf", true)) Page(f.readBytes(), "application/pdf") else shrinkImage(f.readBytes())
}.getOrNull()

/** Computers have no camera step: a scanned page is picked as a file. */
@Composable
fun rememberDesktopPicker(onPages: (List<Page>) -> Unit): DocPicker {
    val scope = rememberCoroutineScope()
    return remember {
        object : DocPicker {
            override val hasCamera = false
            override fun camera() {}
            override fun file() {
                val d = FileDialog(null as Frame?, "בחירת מסמך", FileDialog.LOAD).apply {
                    isMultipleMode = true
                    setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in setOf("pdf", "jpg", "jpeg", "png") }
                    isVisible = true
                }
                val chosen = d.files.toList()
                scope.launch { onPages(withContext(Dispatchers.IO) { chosen.mapNotNull(::readFile) }) }
            }
        }
    }
}

fun renderPages(page: Page): List<ImageBitmap> = runCatching {
    if (page.mime == "application/pdf") Loader.loadPDF(page.bytes).use { doc ->
        val r = PDFRenderer(doc)
        (0 until minOf(doc.numberOfPages, 50)).map { r.renderImageWithDPI(it, 110f).toComposeImageBitmap() }
    } else listOfNotNull(ImageIO.read(page.bytes.inputStream())?.toComposeImageBitmap())
}.getOrDefault(emptyList())

@Composable
fun DesktopPageView(page: Page, modifier: Modifier) {
    val images = remember(page) { renderPages(page) }
    Column(modifier) {
        if (images.isEmpty()) Text("לא ניתן להציג את הקובץ.")
        images.forEach { Image(it, null, Modifier.fillMaxWidth().padding(bottom = 8.dp), contentScale = ContentScale.FillWidth) }
    }
}

/** Save-as dialog. */
@Composable
fun rememberDesktopSaver(onResult: (Boolean) -> Unit): (String, ByteArray) -> Unit = remember {
    { name, bytes ->
        val d = FileDialog(null as Frame?, "שמירת קובץ", FileDialog.SAVE).apply { file = name; isVisible = true }
        val dir = d.directory; val f = d.file
        onResult(if (dir == null || f == null) false else runCatching { File(dir, f).writeBytes(bytes) }.isSuccess)
    }
}
