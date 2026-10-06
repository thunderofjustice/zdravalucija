package hr.zdravalucija.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val customImagePattern = Regex("local-image://[a-f0-9]{64}\\.jpg")
fun isCustomImage(url: String?) = url != null && customImagePattern.matches(url)
fun customImageUrl(bytes: ByteArray): String = "local-image://" + MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) } + ".jpg"

/** Copy only the selected photo; no gallery permissions or retained content URI required. */
suspend fun readCustomPhoto(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "Fotografija se ne može otvoriti." }.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 25 * 1024 * 1024) { "Odaberi fotografiju manju od 25 MB." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val scale = (1600f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inSampleSize = 1 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Odabrana datoteka nije fotografija." }
        options.inJustDecodeBounds = false
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Fotografija se ne može učitati." }
        val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
    }
    try {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Fotografija se nije mogla spremiti." }
        output.toByteArray().also { require(it.size <= 3 * 1024 * 1024) { "Fotografija je prevelika." } }
    } finally { bitmap.recycle() }
}

class UploadImageUseCase(private val dao: LucijaDao, private val store: ImageStore, private val context: Context) {
    suspend operator fun invoke(type: String, id: Long, uri: Uri) {
        val name: String
        val previous: String?
        val query: String?
        when (type) {
            "foods" -> requireNotNull(dao.food(id)).let { name = it.name; previous = it.imageUrl; query = it.imageQuery }
            "categories" -> requireNotNull(dao.category(id)).let { name = it.name; previous = it.imageUrl; query = it.imageQuery }
            "meals" -> requireNotNull(dao.meal(id)).let { name = it.name; previous = it.imageUrl; query = it.imageQuery }
            else -> error("Nepoznata stavka.")
        }
        val bytes = readCustomPhoto(context, uri)
        val url = customImageUrl(bytes)
        store.restoreCustom(url, bytes)
        val changed = when (type) {
            "foods" -> dao.replaceFoodImage(id, name, previous, query, url, query)
            "categories" -> dao.replaceCategoryImage(id, name, previous, query, url, query)
            else -> dao.replaceMealImage(id, name, previous, query, url, query)
        }
        check(changed == 1) { "Stavka se u međuvremenu promijenila. Pokušaj ponovno." }
    }
}
