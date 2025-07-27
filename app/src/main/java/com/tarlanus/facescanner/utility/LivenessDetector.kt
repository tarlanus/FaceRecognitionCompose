package com.tarlanus.facescanner.utility

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

class LivenessDetector(
    context: Context,
    modelPath: String,
    private val spoofThreshold: Float
) {

    private var interpreter: Interpreter?

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(modelBuffer)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    /**
     * @param faceBitmap Input image must be RGB 224x224
     * @return true if live, false if spoof.
     */
    fun isLive(faceBitmap: Bitmap): Boolean {
        require(faceBitmap.width == INPUT_SIZE && faceBitmap.height == INPUT_SIZE) {
            "Input bitmap must be 224x224"
        }

        val input = Array(1) { Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(3) } } }

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = faceBitmap.getPixel(x, y)
                input[0][y][x][0] = ((pixel shr 16) and 0xFF) / 255.0f
                input[0][y][x][1] = ((pixel shr 8) and 0xFF) / 255.0f
                input[0][y][x][2] = (pixel and 0xFF) / 255.0f
            }
        }

        val output = Array(1) { FloatArray(1) }
        interpreter?.run(input, output)

        val score = output[0][0]
        println("Spoof score → $score")

        return score < spoofThreshold
    }

    companion object {
        private const val INPUT_SIZE = 224
    }
}