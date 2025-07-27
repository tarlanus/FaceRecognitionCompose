package com.tarlanus.facescanner.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.tarlanus.facescanner.utility.FaceClassifier
import com.tarlanus.facescanner.utility.LivenessDetector
import com.tarlanus.facescanner.utility.TFLiteFaceRecognition
import com.tarlanus.facescanner.utility.TensorUtility

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ViewModelCamera : ViewModel() {
    private var camera: Camera? = null
    private val _cameraSelector = MutableStateFlow(CameraSelector.DEFAULT_FRONT_CAMERA)
    val cameraSelector = _cameraSelector.asStateFlow()
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var jobCamera : Job? = null


    fun initializeCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        jobCamera?.cancel()
        jobCamera =  viewModelScope.launch {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).get()
                setupCamera(lifecycleOwner, previewView, context)
            } catch (e: Exception) {
                Log.e("onErrorWhileInit", e.message.toString())

            }
        }
    }

    private fun setupCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView, context : Context) {
        cameraProvider?.let { provider ->
            provider.unbindAll()
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            try {
                val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            FaceImageAnalyzer(context)
                        )
                    }
                camera =   provider.bindToLifecycle(
                    lifecycleOwner,
                    _cameraSelector.value,
                    preview,
                    imageAnalyzer

                )
                camera?.cameraControl?.setZoomRatio(1.5f)
            } catch (e: Exception) {
                Log.e("onErrorWhileSetup", e.message.toString())

            }
        }
    }
    @OptIn(ExperimentalGetImage::class)
    class FaceImageAnalyzer(val context: Context) : ImageAnalysis.Analyzer {
        private lateinit var tensorUtility: TensorUtility
        private var croppedBitmap: Bitmap? = null
        private  val CROP_SIZE = 1000
        private lateinit var faceClassifier: FaceClassifier
        private lateinit var livenessDetector: LivenessDetector
        private  val TF_OD_API_INPUT_SIZE2 = 160
        var registerFace = false

        private val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        fun mediaImageToBitmap(mediaImage: Image): Bitmap {
            val yBuffer = mediaImage.planes[0].buffer
            val uBuffer = mediaImage.planes[1].buffer
            val vBuffer = mediaImage.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, out)
            val imageBytes = out.toByteArray()

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val proxybitmap = imageProxy.toBitmap()


                val detector = FaceDetection.getClient(options)
                val cropSize = CROP_SIZE
                croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
                val image = InputImage.fromBitmap(proxybitmap, 0)

                val btmp = mediaImageToBitmap(mediaImage)
                try {
                    faceClassifier = TFLiteFaceRecognition.create(
                        context.assets,
                        "facenet.tflite",
                        160,
                        false,
                        context
                    )

                    tensorUtility = TensorUtility()
                    tensorUtility.create(
                        context.assets,
                        "facenet.tflite",
                        160,
                        false,
                        context

                    )
                } catch (e: IOException) {
                    Log.e("getExceptionOcClass", e.message.toString())
                    e.printStackTrace()
                    Toast.makeText(context, "Classifier could not be initialized", Toast.LENGTH_SHORT).show()

                }

                try {
                    livenessDetector = LivenessDetector(context, "model.tflite", 0.5f)
                } catch (e: IOException) {
                    Log.e("getExceptionlivenessDetector", e.message.toString())
                    throw RuntimeException(e)
                }

                 var isModelQuantized = true

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        Log.e("getFaces", "onResult")


                        for (face in faces) {
                            val bounds = face.boundingBox

                            registerFace = true
                            performFaceRecognition(face, proxybitmap, tensorUtility, isModelQuantized)

                        }
                     //   registerFace = false
                    }
                    .addOnFailureListener { e ->
                        Log.e("getFaces", "onFailure: ${e.message}")
                    }
                    .addOnCompleteListener {
                        imageProxy.close() // Always close the imageProxy
                    }

        }


        }

        fun performFaceRecognition(
            face: Face,
            proxybitmap: Bitmap,
            tensorUtility: TensorUtility,
            isModelQuantized: Boolean
        ) {
            //TODO crop the face
            val bounds = face.getBoundingBox()
            if (bounds.top < 0) {
                bounds.top = 0
            }
            if (bounds.left < 0) {
                bounds.left = 0
            }
            if (bounds.left + bounds.width() > croppedBitmap!!.getWidth()) {
                bounds.right = croppedBitmap!!.getWidth() - 1
            }
            if (bounds.top + bounds.height() > croppedBitmap!!.getHeight()) {
                bounds.bottom = croppedBitmap!!.getHeight() - 1
            }

            var crop = Bitmap.createBitmap(
                croppedBitmap!!,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height()
            )
            crop = Bitmap.createScaledBitmap(crop,
                160,
                160, false)

            val proxyscaled = Bitmap.createScaledBitmap(proxybitmap, 160, 160, false)

            val face224 = Bitmap.createScaledBitmap(proxybitmap, 224, 224, false)
            val isLive = livenessDetector!!.isLive(face224)

            Log.e("getregistering", "isLive $isLive")

            val intValues = IntArray(160 * 160)
            proxyscaled.getPixels(intValues, 0, proxyscaled.width, 0, 0, proxyscaled.width, proxyscaled.height)

            val  imgData = ByteBuffer.allocateDirect(1 * 160 * 160 * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            imgData.rewind()

            val inputSize = 160
             val OUTPUT_SIZE = 512
             val IMAGE_MEAN = 128.0f
              val IMAGE_STD = 128.0f
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

           val  embeddings = Array(1) { FloatArray(OUTPUT_SIZE) }
            val inputArray = arrayOf<Any>(imgData)
            val outputMap = mutableMapOf<Int, Any>()
            outputMap[0] = embeddings

            val tflite = tensorUtility.tfLiteInt

            tflite.runForMultipleInputsOutputs(inputArray, outputMap)


            var distance = Float.MAX_VALUE
            var label = "?"
            val id = "0"


            /*
            val result = faceClassifier!!.recognizeImage(proxyscaled, registerFace)
            var title: String? = "Unknown"
            var confidence = 0f



            Log.e("getregistering", "result $result")


            if (result != null) {
                if (registerFace) {

                    Log.e("getregistering", "registerdialog $isLive")

                } else {
                    if (result.distance!! < 0.75f) {
                        confidence = result.distance
                        title = result.title

                        Log.e("getregistering", "distance $title")

                    } else {
                        Log.e("getregistering", "not $title")

                    }
                }
            } else {
                Log.e("getregistering", "resultat nul")

            }

             */

            val location = RectF(bounds)

        }

    }

    override fun onCleared() {
        super.onCleared()
        jobCamera?.cancel()

        cameraExecutor.shutdown()
    }

    fun setonCleared() {
        jobCamera?.cancel()

        cameraExecutor.shutdown()
    }

}