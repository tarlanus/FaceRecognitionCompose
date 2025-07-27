package com.tarlanus.facescanner.utility


import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF

import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.collections.get
import kotlin.math.sqrt
import kotlin.text.Typography.registered
import kotlin.times

class TFLiteFaceRecognition(context: Context) : FaceClassifier {

    companion object {
        private const val OUTPUT_SIZE = 512
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f

        @Throws(IOException::class)
        private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
            val fileDescriptor = assets.openFd(modelFilename)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }

        @Throws(IOException::class)
        fun create(
            assetManager: AssetManager,
            modelFilename: String,
            inputSize: Int,
            isQuantized: Boolean,
            context: Context
        ): FaceClassifier {
            val d = TFLiteFaceRecognition(context)
            d.inputSize = inputSize
            d.tfLite = Interpreter(loadModelFile(assetManager, modelFilename))
            d.isModelQuantized = isQuantized


            val numBytesPerChannel = if (isQuantized) 1 else 4
            d.imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * numBytesPerChannel).apply {
                order(ByteOrder.nativeOrder())
            }
            d.intValues = IntArray(inputSize * inputSize)

            return d
        }
    }

    private var isModelQuantized = false
    private var inputSize = 0
    private lateinit var imgData: ByteBuffer
    private lateinit var intValues: IntArray
    private lateinit var embeddings: Array<FloatArray>
     lateinit var tfLite: Interpreter

    override fun register(name: String, recognition: FaceClassifier.Recognition) {

    }

    override fun recognizeImage(bitmap: Bitmap, storeExtra: Boolean): FaceClassifier.Recognition {
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        imgData.rewind()

        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    imgData.put(((pixelValue shr 16) and 0xFF).toByte())
                    imgData.put(((pixelValue shr 8) and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
                } else {
                    imgData.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }

        embeddings = Array(1) { FloatArray(OUTPUT_SIZE) }
        val inputArray = arrayOf<Any>(imgData)
        val outputMap = mutableMapOf<Int, Any>()
        outputMap[0] = embeddings

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap)

        var distance = Float.MAX_VALUE
        var label = "?"
        val id = "0"

        /*
        if (registered.isNotEmpty()) {
            val nearest = findNearest(embeddings[0])
            nearest?.let {
                label = it.first
                distance = it.second
            }
        }

         */

        val rec = FaceClassifier.Recognition(
            id = id,
            title = label,
            distance = distance,
            location = RectF()
        )

        if (storeExtra) {
            rec.embedding = embeddings
        }

        return rec
    }

    private fun findNearest(embedding: FloatArray): Pair<String, Float>? {
        /*
        var nearest: Pair<String, Float>? = null
        for ((name, recognition) in registered) {
            val knownEmb = recognition.embedding?.get(0) ?: continue
            var distance = 0f
            for (i in embedding.indices) {
                val diff = embedding[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = sqrt(distance)
            if (nearest == null || distance < nearest.second) {
                nearest = Pair(name, distance)
            }
        }

         */
        return null
    }
}
