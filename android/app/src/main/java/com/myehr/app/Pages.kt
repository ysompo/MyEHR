package com.myehr.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.myehr.app.data.Page
import com.myehr.app.ui.DocPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Largest side of a page photo sent to Claude and kept on the phone. */
private const val MAX_SIDE = 2000

/** Decodes a photo, turns it upright, shrinks it and re-encodes it as JPEG. */
fun shrinkImage(bytes: ByteArray): Page {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("not an image")
    val rotation = runCatching { ExifInterface(bytes.inputStream()).rotationDegrees }.getOrDefault(0)
    val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height))
    if (rotation != 0 || scale < 1f) {
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return Page(out.toByteArray(), "image/jpeg")
}

private fun readUri(ctx: Context, uri: Uri): Page? = runCatching {
    val mime = ctx.contentResolver.getType(uri).orEmpty()
    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    if (mime == "application/pdf") Page(bytes, mime) else shrinkImage(bytes)
}.getOrNull()

@Composable
fun rememberAndroidPicker(onPages: (List<Page>) -> Unit): DocPicker {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val photo = remember { File(File(ctx.cacheDir, "scan").apply { mkdirs() }, "page.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", photo) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        ExternalPick.since = 0L
        if (ok) scope.launch {
            val page = withContext(Dispatchers.IO) { runCatching { shrinkImage(photo.readBytes()) }.getOrNull().also { photo.delete() } }
            if (page != null) onPages(listOf(page))
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        ExternalPick.since = 0L
        scope.launch { onPages(withContext(Dispatchers.IO) { uris.mapNotNull { readUri(ctx, it) } }) }
    }
    return remember {
        object : DocPicker {
            override val hasCamera = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            override fun camera() { ExternalPick.since = System.currentTimeMillis(); camera.launch(photoUri) }
            override fun file() { ExternalPick.since = System.currentTimeMillis(); files.launch(arrayOf("application/pdf", "image/*")) }
        }
    }
}

/** Renders a PDF's pages with Android's own PdfRenderer (the decrypted copy lives only until it is opened). */
private fun renderPdf(ctx: Context, bytes: ByteArray, width: Int = 1400): List<ImageBitmap> {
    val tmp = File.createTempFile("view", ".pdf", ctx.cacheDir)
    try {
        tmp.writeBytes(bytes)
        val fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        tmp.delete()
        return PdfRenderer(fd).use { r ->
            (0 until minOf(r.pageCount, 50)).map { i ->
                r.openPage(i).use { p ->
                    val bmp = Bitmap.createBitmap(width, (width.toFloat() * p.height / p.width).toInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp.asImageBitmap()
                }
            }
        }
    } finally { tmp.delete() }
}

@Composable
fun AndroidPageView(page: Page, modifier: Modifier) {
    val ctx = LocalContext.current
    val images = remember(page) {
        runCatching {
            if (page.mime == "application/pdf") renderPdf(ctx, page.bytes)
            else listOfNotNull(BitmapFactory.decodeByteArray(page.bytes, 0, page.bytes.size)?.asImageBitmap())
        }.getOrDefault(emptyList())
    }
    Column(modifier) {
        if (images.isEmpty()) Text("לא ניתן להציג את הקובץ.")
        images.forEach { Image(it, null, Modifier.fillMaxWidth().padding(bottom = 8.dp), contentScale = ContentScale.FillWidth) }
    }
}
