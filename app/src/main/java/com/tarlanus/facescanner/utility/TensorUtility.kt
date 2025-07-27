package com.tarlanus.facescanner.utility

import android.content.Context
import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TensorUtility {
    private  val OUTPUT_SIZE = 512
    private  val IMAGE_MEAN = 128.0f
    private  val IMAGE_STD = 128.0f
    lateinit var tfLiteInt : Interpreter

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
        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * numBytesPerChannel).apply {
            order(ByteOrder.nativeOrder())
        }
        val intValues = IntArray(inputSize * inputSize)


    }



}