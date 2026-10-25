package be.chatpate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_PHOTO_DIMENSION = 512
private const val MAX_PHOTO_BYTES = 64 * 1024

fun decodeGalleryPhoto(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val size = info.size
            require(size.width > 0 && size.height > 0) { "Le fichier choisi n'est pas une image valide." }
            val scale = minOf(1f, MAX_PHOTO_DIMENSION.toFloat() / max(size.width, size.height))
            decoder.setTargetSize(
                (size.width * scale).toInt().coerceAtLeast(1),
                (size.height * scale).toInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("Impossible d'ouvrir la photo.")
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Le fichier choisi n'est pas une image valide." }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_PHOTO_DIMENSION) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: error("Impossible de lire la photo.")
    return bitmap
}

fun compressPhoto(context: Context, uri: Uri): String {
    val bitmap = decodeGalleryPhoto(context, uri)
    return try {
        encodePhoto(bitmap)
    } finally {
        bitmap.recycle()
    }
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
fun PhotoEditor(
    photo: String,
    onChange: (String) -> Unit,
    squareCropOnly: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }

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
        if (uri != null) {
            scope.launch {
                error = ""
                try {
                    cropBitmap = withContext(Dispatchers.IO) { decodeGalleryPhoto(context, uri) }
                } catch (e: Exception) {
                    error = e.localizedMessage ?: "Impossible de charger cette photo."
                }
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            if (squareCropOnly) {
                error = ""
                cropBitmap = bitmap
            } else {
                processPhoto {
                    try {
                        withContext(Dispatchers.IO) { compressPhoto(bitmap) }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    cropBitmap?.let { source ->
        PhotoCropDialog(
            bitmap = source,
            squareOnly = squareCropOnly,
            onDismiss = {
                cropBitmap = null
            },
            onCrop = { cropped ->
                cropBitmap = null
                processPhoto {
                    try {
                        withContext(Dispatchers.IO) { compressPhoto(cropped) }
                    } finally {
                        cropped.recycle()
                    }
                }
            },
        )
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

private data class CropGeometry(
        val imageLeft: Float,
        val imageTop: Float,
        val imageWidth: Float,
        val imageHeight: Float,
        val cropLeft: Float,
        val cropTop: Float,
        val cropWidth: Float,
        val cropHeight: Float,
) {
    val maxX get() = imageLeft + imageWidth
    val maxY get() = imageTop + imageHeight
}

private fun cropGeometry(
    bitmap: Bitmap,
    viewport: Size,
    ratio: Float,
    cropScale: Float,
    centerX: Float,
    centerY: Float,
): CropGeometry {
    val imageScale = min(viewport.width / bitmap.width, viewport.height / bitmap.height)
    val imageWidth = bitmap.width * imageScale
    val imageHeight = bitmap.height * imageScale
    val imageLeft = (viewport.width - imageWidth) / 2
    val imageTop = (viewport.height - imageHeight) / 2
    val maxCropWidth = min(imageWidth, imageHeight * ratio)
    val maxCropHeight = maxCropWidth / ratio
    val cropWidth = maxCropWidth * cropScale
    val cropHeight = maxCropHeight * cropScale
    val cropLeft = imageLeft + (imageWidth - cropWidth) * centerX
    val cropTop = imageTop + (imageHeight - cropHeight) * centerY
    return CropGeometry(imageLeft, imageTop, imageWidth, imageHeight, cropLeft, cropTop, cropWidth, cropHeight)
}

@Composable
private fun PhotoCropDialog(
    bitmap: Bitmap,
    squareOnly: Boolean,
    onDismiss: () -> Unit,
    onCrop: (Bitmap) -> Unit,
) {
    DisposableEffect(bitmap) {
        onDispose {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    var ratio by remember(bitmap) { mutableStateOf(1f) }
    var cropScale by remember(bitmap) { mutableStateOf(0.82f) }
    val centerX = remember(bitmap) { mutableFloatStateOf(0.5f) }
    val centerY = remember(bitmap) { mutableFloatStateOf(0.5f) }
    var viewportSize by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recadrer la photo") },
        text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .onSizeChanged { viewportSize = it }
                            .pointerInput(bitmap, ratio, cropScale, viewportSize) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    if (viewportSize.width == 0 || viewportSize.height == 0) return@detectDragGestures
                                    val g = cropGeometry(
                                        bitmap,
                                        Size(viewportSize.width.toFloat(), viewportSize.height.toFloat()),
                                        ratio, cropScale, centerX.floatValue, centerY.floatValue,
                                    )
                                    centerX.floatValue = if (g.imageWidth > g.cropWidth) {
                                        (centerX.floatValue + drag.x / (g.imageWidth - g.cropWidth)).coerceIn(0f, 1f)
                                    } else 0.5f
                                    centerY.floatValue = if (g.imageHeight > g.cropHeight) {
                                        (centerY.floatValue + drag.y / (g.imageHeight - g.cropHeight)).coerceIn(0f, 1f)
                                    } else 0.5f
                                }
                            },
                    ) {
                        if (viewportSize.width == 0 || viewportSize.height == 0) return@Canvas
                        val g = cropGeometry(
                            bitmap, Size(size.width, size.height), ratio, cropScale, centerX.floatValue, centerY.floatValue,
                        )
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(g.imageLeft.roundToInt(), g.imageTop.roundToInt()),
                            dstSize = IntSize(g.imageWidth.roundToInt(), g.imageHeight.roundToInt()),
                        )
                        val shade = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)
                        if (squareOnly) {
                            val outsideCircle = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(androidx.compose.ui.geometry.Rect(g.imageLeft, g.imageTop, g.maxX, g.maxY))
                                addOval(androidx.compose.ui.geometry.Rect(g.cropLeft, g.cropTop, g.cropLeft + g.cropWidth, g.cropTop + g.cropHeight))
                            }
                            drawPath(outsideCircle, shade)
                            drawOval(
                                color = androidx.compose.ui.graphics.Color.White,
                                topLeft = Offset(g.cropLeft, g.cropTop),
                                size = Size(g.cropWidth, g.cropHeight),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        } else {
                            drawRect(shade, Offset(g.imageLeft, g.imageTop), Size(g.imageWidth, (g.cropTop - g.imageTop).coerceAtLeast(0f)))
                            drawRect(shade, Offset(g.imageLeft, g.cropTop + g.cropHeight), Size(g.imageWidth, (g.maxY - g.cropTop - g.cropHeight).coerceAtLeast(0f)))
                            drawRect(shade, Offset(g.imageLeft, g.cropTop), Size((g.cropLeft - g.imageLeft).coerceAtLeast(0f), g.cropHeight))
                            drawRect(shade, Offset(g.cropLeft + g.cropWidth, g.cropTop), Size((g.maxX - g.cropLeft - g.cropWidth).coerceAtLeast(0f), g.cropHeight))
                            drawRect(
                                color = androidx.compose.ui.graphics.Color.White,
                                topLeft = Offset(g.cropLeft, g.cropTop),
                                size = Size(g.cropWidth, g.cropHeight),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                    Text("Déplace le cadre pour choisir la zone à conserver.")
                    if (!squareOnly) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1f to "1:1", (4f / 3f) to "4:3", (3f / 4f) to "3:4").forEach { (value, label) ->
                                if (ratio == value) {
                                    Button(onClick = { ratio = value }) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { ratio = value }) { Text(label) }
                                }
                            }
                        }
                    }
                    Text("Taille du cadrage")
                    Slider(value = cropScale, onValueChange = { cropScale = it }, valueRange = 0.35f..1f)
                }
        },
        confirmButton = {
                Button(
                    onClick = {
                        val g = cropGeometry(
                            bitmap,
                            Size(viewportSize.width.toFloat(), viewportSize.height.toFloat()),
                            ratio, cropScale, centerX.floatValue, centerY.floatValue,
                        )
                        val imageScale = g.imageWidth / bitmap.width
                        val left = ((g.cropLeft - g.imageLeft) / imageScale).roundToInt().coerceIn(0, bitmap.width - 1)
                        val top = ((g.cropTop - g.imageTop) / imageScale).roundToInt().coerceIn(0, bitmap.height - 1)
                        val width = (g.cropWidth / imageScale).roundToInt().coerceIn(1, bitmap.width - left)
                        val height = (g.cropHeight / imageScale).roundToInt().coerceIn(1, bitmap.height - top)
                        onCrop(Bitmap.createBitmap(bitmap, left, top, width, height))
                    },
                    enabled = viewportSize.width > 0 && viewportSize.height > 0,
                ) { Text("Utiliser cette photo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
