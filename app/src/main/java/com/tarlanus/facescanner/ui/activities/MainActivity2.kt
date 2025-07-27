package com.tarlanus.facescanner.ui.activities


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Size
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.common.util.concurrent.ListenableFuture

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

/*
class MainActivity2 : AppCompatActivity() {
    private lateinit var detector: FaceDetector
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var previewView: PreviewView
    private lateinit var facePreview: ImageView
    private lateinit var tfLite: Interpreter
    private lateinit var recoName: TextView
    private lateinit var previewInfo: TextView
    private lateinit var textAbovePreview: TextView
    private lateinit var recognize: Button
    private lateinit var cameraSwitch: Button
    private lateinit var actions: Button
    private lateinit var addFace: ImageButton
    private var cameraSelector: CameraSelector? = null
    private var developerMode = false
    private var distance = 1.0f
    private var start = true
    private var flipX = false
    private var camFace = CameraSelector.LENS_FACING_BACK
    private lateinit var intValues: IntArray
    private val inputSize = 112
    private val isModelQuantized = false
    private lateinit var embeddings: Array<FloatArray>
    private val IMAGE_MEAN = 128.0f
    private val IMAGE_STD = 128.0f
    private val OUTPUT_SIZE = 192
    private val SELECT_PICTURE = 1
    private var cameraProvider: ProcessCameraProvider? = null
    private val MY_CAMERA_REQUEST_CODE = 100
    private val modelFile = "mobile_face_net.tflite"
    private val registered = HashMap<String, SimilarityClassifier.Recognition>()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registered.putAll(readFromSP())
        setContentView(R.layout.activity_main)

        facePreview = findViewById(R.id.imageView)
        recoName = findViewById(R.id.textView)
        previewInfo = findViewById(R.id.textView2)
        textAbovePreview = findViewById(R.id.textAbovePreview)
        addFace = findViewById(R.id.imageButton)
        addFace.visibility = View.INVISIBLE

        val sharedPref = getSharedPreferences("Distance", Context.MODE_PRIVATE)
        distance = sharedPref.getFloat("distance", 1.00f)

        facePreview.visibility = View.INVISIBLE
        recognize = findViewById(R.id.button3)
        cameraSwitch = findViewById(R.id.button5)
        actions = findViewById(R.id.button2)
        textAbovePreview.text = "Recognized Face:"

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), MY_CAMERA_REQUEST_CODE)
        }

        actions.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle("Select Action:")
                val names = arrayOf(
                    "View Recognition List",
                    "Update Recognition List",
                    "Save Recognitions",
                    "Load Recognitions",
                    "Clear All Recognitions",
                    "Import Photo (Beta)",
                    "Hyperparameters",
                    "Developer Mode"
                )
                setItems(names) { _, which ->
                    when (which) {
                        0 -> displayNameListView()
                        1 -> updateNameListView()
                        2 -> insertToSP(registered, 0)
                        3 -> registered.putAll(readFromSP())
                        4 -> clearNameList()
                        5 -> loadPhoto()
                        6 -> testHyperparameter()
                        7 -> developerMode()
                    }
                }
                setPositiveButton("OK") { _, _ -> }
                setNegativeButton("Cancel", null)
                show()
            }
        }

        cameraSwitch.setOnClickListener {
            camFace = if (camFace == CameraSelector.LENS_FACING_BACK) {
                flipX = true
                CameraSelector.LENS_FACING_FRONT
            } else {
                flipX = false
                CameraSelector.LENS_FACING_BACK
            }
            cameraProvider?.unbindAll()
            cameraBind()
        }

        addFace.setOnClickListener { addFace() }

        recognize.setOnClickListener {
            if (recognize.text.toString() == "Recognize") {
                start = true
                textAbovePreview.text = "Recognized Face:"
                recognize.text = "Add Face"
                addFace.visibility = View.INVISIBLE
                recoName.visibility = View.VISIBLE
                facePreview.visibility = View.INVISIBLE
                previewInfo.text = ""
            } else {
                textAbovePreview.text = "Face Preview: "
                recognize.text = "Recognize"
                addFace.visibility = View.VISIBLE
                recoName.visibility = View.INVISIBLE
                facePreview.visibility = View.VISIBLE
                previewInfo.text = "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
            }
        }

        try {
            tfLite = Interpreter(loadModelFile(this, modelFile))
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        detector = FaceDetection.getClient(highAccuracyOpts)

        cameraBind()
    }

    private fun testHyperparameter() {
        AlertDialog.Builder(this).apply {
            setTitle("Select Hyperparameter:")
            val names = arrayOf("Maximum Nearest Neighbour Distance")
            setItems(names) { _, which ->
                if (which == 0) hyperparameters()
            }
            setPositiveButton("OK") { _, _ -> }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private fun developerMode() {
        developerMode = !developerMode
        Toast.makeText(this, "Developer Mode ${if (developerMode) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
    }

    private fun addFace() {
        start = false
        AlertDialog.Builder(this).apply {
            setTitle("Enter Name")
            val input = EditText(this@MainActivity).apply {
                inputType = InputType.TYPE_CLASS_TEXT
            }
            setView(input)
            setPositiveButton("ADD") { _, _ ->
                val result = SimilarityClassifier.Recognition("0", "", -1f).apply {
                    extra = embeddings
                }
                registered[input.text.toString()] = result
                start = true
            }
            setNegativeButton("Cancel") { _, _ -> start = true }
            show()
        }
    }

    private fun clearNameList() {
        AlertDialog.Builder(this).apply {
            setTitle("Do you want to delete all Recognitions?")
            setPositiveButton("Delete All") { _, _ ->
                registered.clear()
                Toast.makeText(this@MainActivity, "Recognitions Cleared", Toast.LENGTH_SHORT).show()
            }
            insertToSP(registered, 1)
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private fun updateNameListView() {
        AlertDialog.Builder(this).apply {
            if (registered.isEmpty()) {
                setTitle("No Faces Added!!")
                setPositiveButton("OK", null)
            } else {
                setTitle("Select Recognition to delete:")
                val names = registered.keys.toTypedArray()
                val checkedItems = BooleanArray(registered.size)
                setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                setPositiveButton("OK") { _, _ ->
                    checkedItems.forEachIndexed { index, isChecked ->
                        if (isChecked) registered.remove(names[index])
                    }
                    insertToSP(registered, 2)
                    Toast.makeText(this@MainActivity, "Recognitions Updated", Toast.LENGTH_SHORT).show()
                }
                setNegativeButton("Cancel", null)
            }
            show()
        }
    }

    private fun hyperparameters() {
        AlertDialog.Builder(this).apply {
            setTitle("Euclidean Distance")
            setMessage("0.00 -> Perfect Match\n1.00 -> Default\nTurn On Developer Mode to find optimum value\n\nCurrent Value:")
            val input = EditText(this@MainActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(distance.toString())
            }
            setView(input)
            setPositiveButton("Update") { _, _ ->
                distance = input.text.toString().toFloat()
                val sharedPref = getSharedPreferences("Distance", Context.MODE_PRIVATE)
                sharedPref.edit().putFloat("distance", distance).apply()
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private fun displayNameListView() {
        AlertDialog.Builder(this).apply {
            if (registered.isEmpty()) setTitle("No Faces Added!!")
            else setTitle("Recognitions:")
            setItems(registered.keys.toTypedArray(), null)
            setPositiveButton("OK", null)
            show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity, modelFile: String): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        previewView = findViewById(R.id.previewView)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider!!)
            } catch (e: ExecutionException) {
                // Handle errors
            } catch (e: InterruptedException) {
                // Handle errors
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        cameraSelector = CameraSelector.Builder().requireLensFacing(camFace).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val executor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            try {
                Thread.sleep(0)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            @SuppressLint("UnsafeExperimentalUsageError")
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val frameBmp = toBitmap(mediaImage)
                            val rot = imageProxy.imageInfo.rotationDegrees
                            val frameBmp1 = rotateBitmap(frameBmp, rot, false, false)
                            val boundingBox = RectF(face.boundingBox)
                            val croppedFace = getCropBitmapByCPU(frameBmp1, boundingBox)
                            val scaled = if (flipX) {
                                rotateBitmap(croppedFace, 0, true, false)
                            } else {
                                croppedFace
                            }
                            val finalScaled = getResizedBitmap(scaled, 112, 112)
                            if (start) recognizeImage(finalScaled)
                        } else {
                            recoName.text = if (registered.isEmpty()) "Add Face" else "No Face Detected!"
                        }
                    }
                    .addOnFailureListener { }
                    .addOnCompleteListener { imageProxy.close() }
            }
        }

        cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector!!, imageAnalysis, preview)
    }

    private fun recognizeImage(bitmap: Bitmap) {
        facePreview.setImageBitmap(bitmap)
        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        imgData.rewind()

        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    imgData.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
                } else {
                    imgData.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat((pixelValue and (0xFF - IMAGE_MEAN).toInt()) / IMAGE_STD)
                }
            }
        }

        val inputArray = arrayOf(imgData)
        val outputMap = HashMap<Int, Any>()
        embeddings = Array(1) { FloatArray(OUTPUT_SIZE) }
        outputMap[0] = embeddings
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap)

        var distanceLocal = Float.MAX_VALUE
        var id = "0"
        var label = "?"

        if (registered.isNotEmpty()) {
            val nearest = findNearest(embeddings[0])
            if (nearest[0] != null) {
                val name = nearest[0].first
                distanceLocal = nearest[0].second
                if (developerMode) {
                    recoName.text = if (distanceLocal < distance) {
                        "Nearest: $name\nDist: ${String.format("%.3f", distanceLocal)}\n2nd Nearest: ${nearest[1].first}\nDist: ${String.format("%.3f", nearest[1].second)}"
                    } else {
                        "Unknown\nDist: ${String.format("%.3f", distanceLocal)}\nNearest: $name\nDist: ${String.format("%.3f", distanceLocal)}\n2nd Nearest: ${nearest[1].first}\nDist: ${String.format("%.3f", nearest[1].second)}"
                    }
                } else {
                    recoName.text = if (distanceLocal < distance) name else "Unknown"
                }
            }
        }
    }

    private fun findNearest(emb: FloatArray): List<Pair<String, Float>> {
        val neighbourList = mutableListOf<Pair<String, Float>>()
        var ret: Pair<String, Float>? = null
        var prevRet: Pair<String, Float>? = null

        for ((name, value) in registered) {
            val knownEmb = (value.extra as Array<FloatArray>)[0]
            var distance = 0f
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = Math.sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second) {
                prevRet = ret
                ret = Pair(name, distance)
            }
        }
        if (prevRet == null) prevRet = ret
        neighbourList.add(ret!!)
        neighbourList.add(prevRet!!)
        return neighbourList
    }

    private fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        val matrix = Matrix().apply { postScale(scaleWidth, scaleHeight) }
        val resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false)
        bm.recycle()
        return resizedBitmap
    }

    private fun getCropBitmapByCPU(source: Bitmap, cropRectF: RectF): Bitmap {
        val resultBitmap = Bitmap.createBitmap(
            cropRectF.width().toInt(),
            cropRectF.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.WHITE }
        canvas.drawRect(RectF(0f, 0f, cropRectF.width(), cropRectF.height()), paint)
        val matrix = Matrix().apply { postTranslate(-cropRectF.left, -cropRectF.top) }
        canvas.drawBitmap(source, matrix, paint)
        if (!source.isRecycled) source.recycle()
        return resultBitmap
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) bitmap.recycle()
        return rotatedBitmap
    }

    private fun YUV_420_888toNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)

        var pos = 0
        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            var yBufferPos = -rowStride.toLong()
            while (pos < ySize) {
                yBufferPos += rowStride
                yBuffer.position(yBufferPos.toInt())
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        val uvRowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        assert(uvRowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)

        if (pixelStride == 2 && uvRowStride == width && uBuffer[0] == vBuffer[1]) {
            val savePixel = vBuffer[1]
            try {
                vBuffer.put(1, (savePixel.inv()).toByte())
                if (uBuffer[0] == (savePixel.inv()).toByte()) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer.get(nv21, ySize, 1)
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining())
                    return nv21
                }
            } catch (ex: ReadOnlyBufferException) {
                // Handle exception
            }
            vBuffer.put(1, savePixel)
        }

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * uvRowStride
                nv21[pos++] = vBuffer[vuPos]
                nv21[pos++] = uBuffer[vuPos]
            }
        }
        return nv21
    }

    private fun toBitmap(image: Image): Bitmap {
        val nv21 = YUV_420_888toNV21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun insertToSP(jsonMap: HashMap<String, SimilarityClassifier.Recognition>, mode: Int) {
        if (mode == 1) jsonMap.clear()
        else if (mode == 0) jsonMap.putAll(readFromSP())
        val jsonString = Gson().toJson(jsonMap)
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        sharedPreferences.edit().putString("map", jsonString).apply()
        Toast.makeText(this, "Recognitions Saved", Toast.LENGTH_SHORT).show()
    }

    private fun readFromSP(): HashMap<String, SimilarityClassifier.Recognition> {
        val sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE)
        val defValue = Gson().toJson(HashMap<String, SimilarityClassifier.Recognition>())
        val json = sharedPreferences.getString("map", defValue)
        val token = object : TypeToken<HashMap<String, SimilarityClassifier.Recognition>>() {}
        val retrievedMap = Gson().fromJson<HashMap<String, SimilarityClassifier.Recognition>>(json, token.type)
        for (entry in retrievedMap) {
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }
            val arrayList = entry.value.extra as ArrayList<*>
            val innerList = arrayList[0] as ArrayList<*>
            innerList.forEachIndexed { index, value -> output[0][index] = (value as Double).toFloat() }
            entry.value.extra = output
        }
        Toast.makeText(this, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
        return retrievedMap
    }

    private fun loadPhoto() {
        start = false
        val intent = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {
            val selectedImageUri = data?.data
            try {
                val impPhoto = InputImage.fromBitmap(getBitmapFromUri(selectedImageUri!!), 0)
                detector.process(impPhoto).addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        recognize.text = "Recognize"
                        addFace.visibility = View.VISIBLE
                        recoName.visibility = View.INVISIBLE
                        facePreview.visibility = View.VISIBLE
                        previewInfo.text = "1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face."
                        val face = faces[0]
                        var frameBmp = getBitmapFromUri(selectedImageUri)
                        val frameBmp1 = rotateBitmap(frameBmp, 0, flipX, false)
                        val boundingBox = RectF(face.boundingBox)
                        val croppedFace = getCropBitmapByCPU(frameBmp1, boundingBox)
                        val scaled = getResizedBitmap(croppedFace, 112, 112)
                        recognizeImage(scaled)
                        addFace()
                        Thread.sleep(100)
                    }
                }.addOnFailureListener {
                    start = true
                    Toast.makeText(this, "Failed to add", Toast.LENGTH_SHORT).show()
                }
                facePreview.setImageBitmap(getBitmapFromUri(selectedImageUri))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
        val fileDescriptor = parcelFileDescriptor?.fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor?.close()
        return image
    }
}

 */