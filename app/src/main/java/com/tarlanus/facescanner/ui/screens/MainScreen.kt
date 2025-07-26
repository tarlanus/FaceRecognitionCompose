package com.tarlanus.facescanner.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tarlanus.facescanner.ui.theme.DarkBlue
import com.tarlanus.facescanner.ui.theme.LightBlue
import com.tarlanus.facescanner.viewmodels.ViewModelMain
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {

    val viewModelMain: ViewModelMain = viewModel()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val stateSnackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val keyManifestCamera = android.Manifest.permission.CAMERA
    val showSnackber = viewModelMain.showSnackbar.collectAsStateWithLifecycle(false)

    val showPermission = viewModelMain.showPermission.collectAsStateWithLifecycle(false)
    val showCamera = viewModelMain.goCamera.collectAsStateWithLifecycle(initialValue = false)
    val permissionCamera =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if (result) {
                viewModelMain.setShowCamera()
            }
        }


    LaunchedEffect(showPermission.value) {
        if (showPermission.value) {
            permissionCamera.launch(keyManifestCamera)
        }
    }

    LaunchedEffect(showSnackber.value) {
        if (showSnackber.value) {
            val result = stateSnackbar.showSnackbar(
                message = "Camera permission required",
                actionLabel = "Retry"
            )
            if (result == SnackbarResult.ActionPerformed) {
                permissionCamera.launch(keyManifestCamera)
            }
        }


    }


    val lazyListState = rememberLazyListState()
    val scrollState = rememberScrollState()

    val screenHeight = LocalConfiguration.current.screenHeightDp


    Scaffold(
        modifier = Modifier.Companion
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Face Scanning", color = Color.Yellow, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBlue,
                    scrolledContainerColor = DarkBlue
                ),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(stateSnackbar) }
    ) { innerPad ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightBlue)
                .padding(innerPad),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (showCamera.value) {
                androidx.compose.ui.window.Dialog(onDismissRequest = {
                    viewModelMain.closeCamera()
                }) {
                    CameraScreen(onBack = {
                        viewModelMain.closeCamera()
                    })
                }
            }

            Text("List of scans", fontSize = 18.sp, color = Color.White)
            Button(onClick = {
                viewModelMain.startCamera(context, activity)



            }) {
                Text("Open Camera")
            }
            Button(onClick = {

            }) {
                Text("Show List")
            }


            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(7) {
                    Box(
                        modifier = Modifier
                            .padding(15.dp)
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.Black)
                    )
                }

                item {
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Text("Open Camera")
                    }
                }

                item {
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Text("Show List")
                    }
                }
            }


        }


    }

}

@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    MainScreen()

}