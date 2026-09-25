package com.example.hueandyou.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.CalibrationConfig
import java.io.File

/**
 * Home screen for both photo flows: an in-app CameraX viewfinder with a shutter and a gallery
 * button, falling back to the system photo picker if camera permission is denied. Reports a
 * ready-to-decode [Uri] via [onPhotoUri] either way.
 */
@Composable
internal fun CameraCaptureStep(onPhotoUri: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPhotoUri(uri) }
    val openGallery = {
        pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionWasDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permissionWasDenied = true
            openGallery()
        }
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        CameraViewfinder(onPhotoUri = onPhotoUri, onGalleryClick = openGallery, modifier = modifier)
    } else {
        CameraPermissionFallback(
            showDeniedMessage = permissionWasDenied,
            onPickPhoto = openGallery,
            modifier = modifier,
        )
    }
}

@Composable
private fun CameraViewfinder(onPhotoUri: (Uri) -> Unit, onGalleryClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var isCapturing by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                val previewView = PreviewView(viewContext)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    },
                    ContextCompat.getMainExecutor(viewContext),
                )
                previewView
            },
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val fraction = CalibrationConfig.CENTER_BOX_FRACTION.toFloat()
            val left = size.width * (1f - fraction) / 2f
            val top = size.height * (1f - fraction) / 2f
            drawMarkerRect(
                topLeft = Offset(left, top),
                boxSize = Size(size.width * fraction, size.height * fraction),
            )
        }

        Text(
            text = stringResource(R.string.camera_viewfinder_hint),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp, start = 32.dp, end = 32.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        CameraAdjustmentSliders(camera = camera, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = stringResource(R.string.rate_clothing_pick_photo),
                tint = Color.White,
            )
        }

        FloatingActionButton(
            onClick = {
                if (isCapturing) return@FloatingActionButton
                isCapturing = true
                val file = createCameraCaptureFile(context)
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            isCapturing = false
                            onPhotoUri(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            isCapturing = false
                        }
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
        ) {
            Icon(Icons.Filled.Camera, contentDescription = stringResource(R.string.camera_shutter_content_description))
        }
    }
}

@Composable
private fun CameraPermissionFallback(
    showDeniedMessage: Boolean,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showDeniedMessage) {
            Text(
                text = stringResource(R.string.camera_permission_denied_message),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Button(onClick = onPickPhoto, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.rate_clothing_pick_photo))
        }
        if (showDeniedMessage) {
            Button(onClick = { context.openAppSettings() }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.camera_open_settings))
            }
        }
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    startActivity(intent)
}

private fun createCameraCaptureFile(context: Context): File {
    val capturesDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    return File.createTempFile("capture_", ".jpg", capturesDir)
}
