package com.tarlanus.facescanner.viewmodels

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ViewModelMain : ViewModel() {
    val keyManifestCamera = android.Manifest.permission.CAMERA
    private val _showSnackbar : MutableSharedFlow<Boolean> = MutableSharedFlow()
    val showSnackbar = _showSnackbar.asSharedFlow()

    private val _showPermission : MutableSharedFlow<Boolean> = MutableSharedFlow()
    val showPermission = _showPermission.asSharedFlow()

    private val _goCamera : MutableSharedFlow<Boolean> = MutableSharedFlow()
    val goCamera = _goCamera.asSharedFlow()



    fun setShowCamera() {
        viewModelScope.launch {
            _goCamera.emit(true)
        }
    }

    fun closeCamera() {
        viewModelScope.launch {
            _goCamera.emit(false)
        }
    }

    fun startCamera(context: Context, activity: Activity?) {
        val checkPermission = ContextCompat.checkSelfPermission(
            context,
            keyManifestCamera
        ) == PackageManager.PERMISSION_GRANTED
        val showRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(activity, keyManifestCamera)

        if (checkPermission == true) {
            setShowCamera()
        } else {
            if (showRationale) {
                Log.e("onRationale", "showRationale")
                viewModelScope.launch {
                    _showSnackbar.emit(true)
                }
            } else {
                viewModelScope.launch {
                    _showPermission.emit(true)
                }
            }


        }


    }


}