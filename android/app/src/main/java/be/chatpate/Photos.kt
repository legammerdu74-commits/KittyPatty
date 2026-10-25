package be.chatpate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val MAX_PHOTO_DIMENSION = 512
private const val MAX_PHOTO_BYTES = 64 * 1024

fun compressPhoto(context: Context, uri: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("Impossible d'ouvrir la photo.")
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Le fichier choisi n'est pas une image valide." }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_PHOTO_DIMENSION * 2) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: error("Impossible de lire la photo.")
    return encodePhoto(bitmap)
}

fun compressPhoto(bitmap: Bitmap): String = encodePhoto(bitmap)

private fun encodePhoto(source: Bitmap): String {
    val scale = minOf(1f, MAX_PHOTO_DIMENSION.toFloat() / max(source.width, source.height))
    var bitmap = Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    try {
        while (true) {
            for (quality in listOf(78, 65, 52, 40)) {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val bytes = output.toByteArray()
                if (bytes.size <= MAX_PHOTO_BYTES) return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
            if (max(bitmap.width, bitmap.height) <= 192) {
                error("La photo ne peut pas être compressée suffisamment.")
            }
            val smaller = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * 0.75f).toInt().coerceAtLeast(1),
                (bitmap.height * 0.75f).toInt().coerceAtLeast(1),
                true,
            )
            if (bitmap !== source) bitmap.recycle()
            bitmap = smaller
        }
    } finally {
        if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
    }
}

fun decodePhoto(data: String): Bitmap? = runCatching {
    val bytes = Base64.decode(data, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

@Composable
fun PhotoImage(photo: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap = remember(photo) { photo.takeIf { it.isNotEmpty() }?.let(::decodePhoto) }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = "Photo", modifier = modifier, contentScale = contentScale)
    }
}

@Composable
fun PhotoEditor(photo: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }

    fun processPhoto(action: suspend () -> String) {
        scope.launch {
            error = ""
            try {
                onChange(action())
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Impossible de charger cette photo."
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processPhoto {
            withContext(Dispatchers.IO) { compressPhoto(context, uri) }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) processPhoto {
            withContext(Dispatchers.IO) { compressPhoto(bitmap) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photo.isNotEmpty()) {
            PhotoImage(
                photo,
                Modifier
                    .width(180.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { camera.launch(null) }) { Text("Prendre une photo") }
            TextButton(onClick = { gallery.launch("image/*") }) { Text("Galerie") }
        }
        if (photo.isNotEmpty()) {
            TextButton(onClick = { onChange(""); error = "" }) { Text("Supprimer la photo") }
        }
        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
