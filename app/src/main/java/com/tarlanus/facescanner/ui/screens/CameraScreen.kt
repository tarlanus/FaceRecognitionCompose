package com.tarlanus.facescanner.ui.screens

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tarlanus.facescanner.ui.theme.LightBlue
import com.tarlanus.facescanner.viewmodels.ViewModelCamera

@Composable
fun CameraScreen(onBack : () ->Unit) {
    val viewModelCamera : ViewModelCamera = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            this.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraSelector = viewModelCamera.cameraSelector.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModelCamera.initializeCamera(context, lifecycleOwner, previewView)
    }



    Card(modifier = Modifier.height(230.dp).width(170.dp).border(width = 2.dp, color = LightBlue, shape = RoundedCornerShape(CornerSize(65.dp))), shape = RoundedCornerShape(CornerSize(65.dp)),
        ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }

    BackHandler(onBack = {
        onBack()
        viewModelCamera.setonCleared()
    })

}

@Composable
@Preview(showBackground = true)
fun PreviewCameraScreen() {
    CameraScreen(onBack = {})
}