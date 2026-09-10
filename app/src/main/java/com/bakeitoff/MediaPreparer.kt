package com.bakeitoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

data class PreparedMedia(
    val compressedVideoUri: Uri?,
    val bitmaps: List<Bitmap>
)

class MediaPreparationException(message: String) : Exception(message)

class MediaPreparer {

    private val _isCompressing = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing.asStateFlow()

    private val _compressionProgress = MutableStateFlow(0f)
    val compressionProgress: StateFlow<Float> = _compressionProgress.asStateFlow()

    suspend fun prepare(context: Context, uris: List<Uri>): PreparedMedia {
        var compressedVideoUri: Uri? = null
        val bitmaps = mutableListOf<Bitmap>()

        for (uri in uris) {
            val mimeType = context.contentResolver.getType(uri) ?: ""

            if (mimeType.startsWith("video") && compressedVideoUri == null) {
                compressedVideoUri = compressVideo(context, uri)
                    ?: throw MediaPreparationException("Falha ao comprimir o vídeo. Tente novamente.")
            } else if (mimeType.startsWith("image")) {
                bitmaps.add(decodeAndResizeImage(context, uri))
            }
        }

        return PreparedMedia(compressedVideoUri, bitmaps)
    }

    fun deleteTemporaryVideo(uri: Uri) {
        try {
            val file = File(uri.path ?: return)
            if (file.exists()) {
                val deletado = file.delete()
                if (deletado) {
                    Log.d("BakeItOffDebug", "Vídeo temporário deletado com sucesso: ${file.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro ao deletar arquivo temporário: ${e.message}")
        }
    }

    private fun decodeAndResizeImage(context: Context, uri: Uri): Bitmap {
        val bitmapOriginal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        return bitmapOriginal.resizeForAi(1024)
    }

    private suspend fun compressVideo(context: Context, videoUri: Uri): Uri? = suspendCancellableCoroutine { continuation ->
        _isCompressing.value = true
        _compressionProgress.value = 0f

        VideoCompressor.start(
            context = context,
            uris = listOf(videoUri),
            isStreamable = false,
            sharedStorageConfiguration = SharedStorageConfiguration(
                saveAt = SaveLocation.movies,
                subFolderName = "BakeItOff_Temp"
            ),
            configureWith = Configuration(
                quality = VideoQuality.LOW,
                disableAudio = false,
                keepOriginalResolution = false,
                videoWidth = 480.0,
                videoHeight = 854.0,
                videoNames = listOf("bakeitoff_${System.currentTimeMillis()}"),
                isMinBitrateCheckEnabled = false
            ),
            listener = object : CompressionListener {
                override fun onProgress(index: Int, percent: Float) {
                    _compressionProgress.value = percent
                }

                override fun onSuccess(index: Int, size: Long, path: String?) {
                    _isCompressing.value = false
                    if (path != null) {
                        continuation.resume(Uri.fromFile(File(path)))
                    } else {
                        continuation.resume(null)
                    }
                }

                override fun onFailure(index: Int, failureMessage: String) {
                    _isCompressing.value = false
                    Log.e("BakeItOffDebug", "Falha na compressão: $failureMessage")
                    continuation.resume(null)
                }

                override fun onStart(index: Int) {}

                override fun onCancelled(index: Int) {
                    _isCompressing.value = false
                    continuation.resume(null)
                }
            }
        )
    }

    private fun Bitmap.resizeForAi(maxDimension: Int = 1024): Bitmap {
        val width = this.width
        val height = this.height

        if (width <= maxDimension && height <= maxDimension) {
            return this
        }

        val ratio: Float = width.toFloat() / height.toFloat()
        val finalWidth: Int
        val finalHeight: Int

        if (ratio > 1) {
            finalWidth = maxDimension
            finalHeight = (maxDimension / ratio).toInt()
        } else {
            finalHeight = maxDimension
            finalWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(this, finalWidth, finalHeight, true)
    }
}
