package com.tarlanus.facescanner.utility

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.util.Pair
import androidx.compose.ui.geometry.Rect
import com.tarlanus.facerecognizerv01.roomdb.AppDataBase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class TensorUtility(val context: Context) {
    var registered: HashMap<String?, FaceClassifier.Recognition?> =
        HashMap<String?, FaceClassifier.Recognition?>()

    val inputSize = 160
    val OUTPUT_SIZE = 512
    val IMAGE_MEAN = 128.0f
    val IMAGE_STD = 128.0f
    val intValues = IntArray(160 * 160)
    private lateinit var embeedings: Array<FloatArray?>

    lateinit var tfLiteInt: Interpreter
    private var isModelQuantized = false

    val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e("getEmbeddingSaves", t.localizedMessage.toString())
    }

    init {

        CoroutineScope(Dispatchers.IO + coroutineExceptionHandler).launch {
            val roomDb = AppDataBase.getRoomInstance(context)
            val dao = roomDb.getRoomDao()
            val saves = dao.getSaves()
            for (face in saves) {
                val id =
                    face.uniqueID
                val embeddingString =
                    face.embedding
                val title =
                    face.name
                val stringList =
                    embeddingString.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                val embeddingFloat =
                    ArrayList<Float?>()
                for (s in stringList) {
                    embeddingFloat.add(s.toFloat())
                }
                val bigArray =
                    Array<FloatArray?>(1) { FloatArray(1) }
                val floatArray = FloatArray(embeddingFloat.size)
                for (i in embeddingFloat.indices) {
                    floatArray[i] = embeddingFloat.get(i)!!
                }
                bigArray[0] = floatArray
                val recognition = FaceClassifier.Recognition(
                    title = title,
                    embedding = bigArray as Array<FloatArray>?
                )
                registered.putIfAbsent(recognition.title, recognition)



            }


        }
    }

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
        inputSize: Int = 160,
        isQuantized: Boolean = false,
        context: Context
    ) {

        tfLiteInt = Interpreter(
            loadModelFile(
                assetManager,
                modelFilename
            )
        )


        val numBytesPerChannel = if (isQuantized) 1 else 4
        val imgData =
            ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * numBytesPerChannel).apply {
                order(ByteOrder.nativeOrder())
            }


    }

    fun recognizeImage(
        bitmap: Bitmap?,
        afterRecognize: (FaceClassifier.Recognition?) -> Unit
    ): FaceClassifier.Recognition {
        bitmap?.getPixels(
            intValues,
            0,
            bitmap.getWidth(),
            0,
            0,
            bitmap.getWidth(),
            bitmap.getHeight()
        )
        val imgData = ByteBuffer.allocateDirect(1 * 160 * 160 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        imgData!!.rewind()
        for (i in 0..<inputSize) {
            for (j in 0..<inputSize) {
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
        embeedings = Array(1) { FloatArray(OUTPUT_SIZE) }
        val inputArray = arrayOf<Any>(imgData)
        val outputMap = mutableMapOf<Int, Any>()
        outputMap[0] = embeedings

        tfLiteInt.runForMultipleInputsOutputs(inputArray, outputMap)


        var distance = Float.Companion.MAX_VALUE
        Log.e("getregistering", "distancefirst $distance")


        val id = "0"
        var label: String? = "?"
        val rec = FaceClassifier.Recognition(
            id,
            label,
            distance,
            location = RectF()
        )
        if (registered.size > 0) {

            val nearest = findNearest(embeedings[0]!!)
            if (nearest != null) {
                val name = nearest.first
                label = name
                distance = nearest.second!!
                rec.title = label
                rec.distance = distance
            }

            Log.e("getregistering", "distance $label")
            rec.embedding = embeedings as Array<FloatArray>?

        } else {
            Log.e("getregistering", "noooo $label")

        }



        afterRecognize(rec)




        return rec
    }

    private fun findNearest(emb: FloatArray): android.util.Pair<String?, Float?>? {
        var ret: android.util.Pair<String?, Float?>? = null
        for (entry in registered.entries) {
            val name = entry.key
            val knownEmb = (entry.value!!.embedding as Array<FloatArray>?)!![0]

            var distance = 0f
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second!!) {
                ret = Pair<String?, Float?>(name, distance)
            }
        }
        return ret
    }


}