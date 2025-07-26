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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
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
                val detector = FaceDetection.getClient(options)
                val cropSize = CROP_SIZE
                croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                val btmp = mediaImageToBitmap(mediaImage)
                try {
                    faceClassifier = TFLiteFaceRecognition.create(
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


                detector.process(image)
                    .addOnSuccessListener { faces ->
                        Log.e("getFaces", "onResult")


                        for (face in faces) {
                            val bounds = face.boundingBox
                            performFaceRecognition(face, btmp)
                            registerFace = true

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

        private fun performFaceRecognition(face: Face, btmp: Bitmap) {

            var bounds = face.boundingBox


            bounds = Rect(
                maxOf(bounds.left, 0),
                maxOf(bounds.top, 0),
                minOf(bounds.right, croppedBitmap!!.width - 1),
                minOf(bounds.bottom, croppedBitmap!!.height - 1)
            )

            val crop = Bitmap.createBitmap(
                croppedBitmap!!,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height()
            )
            val scaledCrop = Bitmap.createScaledBitmap(btmp, TF_OD_API_INPUT_SIZE2, TF_OD_API_INPUT_SIZE2, false)
            val face224 = Bitmap.createScaledBitmap(btmp , 224, 224, false)
            val isLive = livenessDetector.isLive(face224)
            Log.e("getregistering", "isLive $isLive")

            val result = faceClassifier.recognizeImage(scaledCrop, true)
            val (title, confidence) = if (result != null) {
                if (registerFace) {
                    Log.e("getregistering", "register")


                    "Unknown" to 0f
                } else {
                    Log.e("getregistering", "already")

                    if ((result.distance ?: 0f) < 0.75f) result.title to (result.distance ?: 0f) else "Unknown" to 0f
                }
            } else {
                Log.e("getregistering", "null")

                "Unknown" to 0f
            }

            val location = RectF(bounds)
            /*
            if (useFacing == CameraCharacteristics.LENS_FACING_BACK) {
                location.right = croppedBitmap!!.width - location.right
                location.left = croppedBitmap!!.width - location.left
            }
            cropToFrameTransform?.mapRect(location)

             */
            val recognition = FaceClassifier.Recognition(
                id = face.trackingId?.toString() ?: "",
                title = title,
                distance = confidence,
                embedding = null,
                location = location,
                crop = null
            )
            if (!isLive) {
                recognition.title = "Spoof"
            }
          //  mappedRecognitions.add(recognition)

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