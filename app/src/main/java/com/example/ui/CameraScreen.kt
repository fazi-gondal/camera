package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.PhotoEntity
import com.example.util.ImageWatermarker
import android.content.pm.PackageManager
import com.example.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            hasPermission = true
        }
    }

    LaunchedEffect(Unit) {
        val systemGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!systemGranted) {
            cameraPermissionState.launchPermissionRequest()
        } else {
            hasPermission = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Dark atmospheric backdrop
    ) {
        if (hasPermission) {
            CameraContent(viewModel = viewModel)
        } else {
            PermissionGateway(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        }
    }
}

@Composable
fun PermissionGateway(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Styled diagnostic camera icon with subtle gradient shadow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFF9100), Color(0xFFFF5722))
                    ),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Camera Permission Required",
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Camera Access Needed",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "To take photos and embed beautiful, customizable real-time date and time stamps, we need permission to access your device's camera.",
            fontSize = 14.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF5722),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("request_permission_button")
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Grant Camera Permission",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun CameraContent(
    viewModel: CameraViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val selectedColorHex by viewModel.selectedColorHex.collectAsStateWithLifecycle()
    val selectedStyleIndex by viewModel.selectedStyleIndex.collectAsStateWithLifecycle()
    val selectedFilterIndex by viewModel.selectedFilterIndex.collectAsStateWithLifecycle()
    val noteText by viewModel.noteText.collectAsStateWithLifecycle()

    // Camera Configuration State variables
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var activePhotoView by remember { mutableStateOf<PhotoEntity?>(null) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isTorchEnabled by remember { mutableStateOf(false) }

    // Setup CameraX ImageCapture and Provider listeners
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraBoundState by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraInstance by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    // Live tick clock for real-time stamp overlay synchronization
    var currentTickTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTickTime = System.currentTimeMillis()
        }
    }

    // Capture Trigger executor
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    // Re-apply correct flashMode safely without needing to rebind the entire camera!
    LaunchedEffect(flashMode) {
        try {
            imageCapture.flashMode = flashMode
        } catch (e: Exception) {
            Log.e("CameraContent", "Failed setting flashMode on capture", e)
        }
    }

    // Toggle torch state dynamically on active camera instance when state changes
    LaunchedEffect(cameraInstance, isTorchEnabled) {
        try {
            cameraInstance?.cameraControl?.enableTorch(isTorchEnabled)
        } catch (e: Exception) {
            Log.e("CameraContent", "Failed to toggle flashlight/torch", e)
        }
    }

    // Safe, single-point of binding CameraX once PreviewView is ready & states switch!
    LaunchedEffect(previewViewRef, lensFacing) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        try {
            val cameraProvider = kotlinx.coroutines.suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
                cameraProviderFuture.addListener({
                    try {
                        continuation.resume(cameraProviderFuture.get(), onCancellation = null)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }, mainExecutor)
            }

            cameraProvider.unbindAll()

            // Resolve actual target camera selector, defaulting to available selectors to prevent crash if front lens is missing
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val finalSelector = if (cameraProvider.hasCamera(selector)) {
                selector
            } else {
                val alternativeLens = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                val alternativeSelector = CameraSelector.Builder().requireLensFacing(alternativeLens).build()
                if (cameraProvider.hasCamera(alternativeSelector)) {
                    lensFacing = alternativeLens
                    alternativeSelector
                } else {
                    selector
                }
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                finalSelector,
                preview,
                imageCapture
            )
            cameraInstance = camera

            // Reapply torch state to the newly bound camera
            try {
                camera.cameraControl.enableTorch(isTorchEnabled)
            } catch (e: Exception) {
                Log.e("CameraContent", "Could not apply torch state to model", e)
            }

            cameraBoundState = true
        } catch (e: Exception) {
            Log.e("CameraContent", "Binding failed dynamically", e)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // VIEWPORT CONTAINER - Full Screen Edge-to-Edge Background
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Always render AndroidView in composition so that PreviewView is properly initialized
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!cameraBoundState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF5722))
                }
            }

            // VIBRANT SURFACE GRID & CROSSHAIRS PREVIEW INDICATOR
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(BorderStroke(1.dp, Color(0x11FFFFFF)))
            )
        }

        // CONTROL OVERLAYS - Glassy / Semi-transparent panels laid out vertically
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // OVERLAY TOP BAR: Flash, Status Indicator & Lens Flip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x7F000000))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash Mode Toggle Button
                IconButton(
                    onClick = {
                        if (isTorchEnabled) {
                            // Transition: Torch -> Off
                            isTorchEnabled = false
                            flashMode = ImageCapture.FLASH_MODE_OFF
                            Toast.makeText(context, "Flash/Flashlight Off", Toast.LENGTH_SHORT).show()
                        } else if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                            // Transition: Off -> Flash On (fires during capture)
                            flashMode = ImageCapture.FLASH_MODE_ON
                            isTorchEnabled = false
                            Toast.makeText(context, "Flash On (during capture)", Toast.LENGTH_SHORT).show()
                        } else {
                            // Transition: Flash On -> Torch (continuous flashlight)
                            flashMode = ImageCapture.FLASH_MODE_OFF
                            isTorchEnabled = true
                            Toast.makeText(context, "Flashlight On (continuous stream)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .background(Color(0x7F212121), CircleShape)
                        .testTag("flash_toggle_button")
                ) {
                    val icon = when {
                        isTorchEnabled -> Icons.Default.FlashOn
                        flashMode == ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }
                    val tintColor = when {
                        isTorchEnabled -> Color(0xFF00E676) // Neon Green
                        flashMode == ImageCapture.FLASH_MODE_ON -> Color(0xFFFFEB3B) // Yellow
                        else -> Color.White
                    }
                    Icon(icon, contentDescription = "Flash Options", tint = tintColor)
                }

                // Watermark & Filter active badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x9F00E676), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "STAMP ACTIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    if (selectedFilterIndex > 0) {
                        val activeFilterLabel = when (selectedFilterIndex) {
                            1 -> "WARM"
                            2 -> "NOIR"
                            3 -> "NEON"
                            4 -> "SEPIA"
                            5 -> "VIVID"
                            else -> ""
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xE0FF5722), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = activeFilterLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Camera Switch Lens facing toggler
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .background(Color(0x7F212121), CircleShape)
                        .testTag("lens_facing_toggle_button")
                ) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Lens",
                        tint = Color.White
                    )
                }
            }

            // LIVE DYNAMIC GRAPHIC STAMP WATERMARK PREVIEW Overlay (In-view positioning)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = when (selectedStyleIndex) {
                    2 -> Alignment.BottomCenter
                    3 -> Alignment.BottomStart
                    else -> Alignment.BottomEnd
                }
            ) {
                val formattedLiveTime = remember(selectedStyleIndex, currentTickTime) {
                    val pattern = when (selectedStyleIndex) {
                        1 -> "yyyy’MM’dd  HH:mm"
                        2 -> "yyyy.MM.dd | HH:mm:ss"
                        else -> "yyyy-MM-dd  HH:mm:ss"
                    }
                    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(currentTickTime))
                }

                val visualStampText = if (noteText.isNotBlank()) {
                    "$noteText  •  $formattedLiveTime"
                } else {
                    formattedLiveTime
                }

                val textColor = Color(android.graphics.Color.parseColor(selectedColorHex))

                AnimatedContent(
                    targetState = Triple(selectedStyleIndex, visualStampText, textColor),
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "VisualStampChange"
                ) { (style, stampString, color) ->
                    when (style) {
                        1 -> { // Retro Digital Glow
                            Text(
                                text = stampString,
                                color = Color(0xFFFF5722),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color(0x77000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        2 -> { // Cyber Black Banner overlay style across bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x99000000))
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stampString,
                                    color = Color(0xFF00E676),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        3 -> { // Classic Left Yellow / Custom
                            Text(
                                text = stampString,
                                color = color,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Left,
                                modifier = Modifier
                                    .background(Color(0x77000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        else -> { // Standard overlay Bottom-Right
                            Text(
                                text = stampString,
                                color = color,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier
                                    .background(Color(0x77000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // BOTTOM CONTROL DRAWER PANEL - Semi-transparent glassy dark slate background!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE0121212))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Style Selector Chips
                ScrollableTabRow(
                    selectedTabIndex = selectedStyleIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = {}
                ) {
                    listOf("Classic BR", "Retro LED", "Neon Banner", "Classic BL").forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedStyleIndex == idx,
                            onClick = { viewModel.updateSelectedStyle(idx) },
                            modifier = Modifier
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedStyleIndex == idx) Color(0xFFFF5722) else Color(0xFF222222)
                                )
                                .testTag("style_tab_$idx")
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedStyleIndex == idx) Color.White else Color.LightGray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Optional Custom Suffix Text entry
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { viewModel.updateNoteText(it) },
                    placeholder = { Text("Add custom note prefix (e.g. Travel, Gym)", fontSize = 13.sp, color = Color.Gray) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (noteText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateNoteText("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF181818),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("note_input_field")
                )

                // Color selection dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Stamp Color:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(viewModel.availableColors) { (colorHex, colorName) ->
                            val colorItem = Color(android.graphics.Color.parseColor(colorHex))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colorItem)
                                    .border(
                                        width = if (selectedColorHex == colorHex) 2.dp else 0.dp,
                                        color = if (selectedColorHex == colorHex) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.updateSelectedColor(colorHex) }
                                    .testTag("color_choice_$colorHex")
                            )
                        }
                    }
                }

                // Aesthetic Photo Filter Selection presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Photo Filter:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(viewModel.availableFilters) { (filterName, filterIdx) ->
                            val isSelected = selectedFilterIndex == filterIdx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFFF5722) else Color(0xFF222222))
                                    .clickable { viewModel.updateSelectedFilter(filterIdx) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("filter_choice_$filterIdx")
                            ) {
                                Text(
                                    text = filterName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color.LightGray
                                )
                            }
                        }
                    }
                }

                // Snap photo triggers & library access
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Latest Thumbnail preview / Gallery Toggle icon
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1A1A))
                            .border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(12.dp))
                            .clickable {
                                if (photos.isNotEmpty()) {
                                    activePhotoView = photos.first()
                                } else {
                                    Toast.makeText(context, "No photos captured yet! Snap a photo first.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .testTag("gallery_thumbnail_view"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photos.isNotEmpty()) {
                            AsyncImage(
                                model = File(photos.first().filePath),
                                contentDescription = "Latest stamp capture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = "Empty Library",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // HUGE SHUTTER BUTTON
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .clickable(enabled = !isCapturing) {
                                executePhotoSnapping(
                                    context = context,
                                    imageCapture = imageCapture,
                                    viewModel = viewModel,
                                    noteText = noteText,
                                    selectedColorHex = selectedColorHex,
                                    selectedStyleIndex = selectedStyleIndex,
                                    filterIndex = selectedFilterIndex,
                                    mainExecutor = mainExecutor
                                )
                            }
                            .testTag("main_shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(if (isCapturing) Color.LightGray else Color(0xFFFF5722)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "Take Stamp Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Total Photo Count Badge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(60.dp)
                    ) {
                        Text(
                            text = photos.size.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5722)
                        )
                        Text(
                            text = "photos",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            lineHeight = 10.sp
                        )
                    }
                }

                // HORIZONTAL JOURNAL ROLL (SCROLLABLE RECENT CAPTURES SUMMARY)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historical Captured Roll",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        if (photos.isNotEmpty()) {
                            Text(
                                text = "Tap images to view or share",
                                fontSize = 10.sp,
                                color = Color(0xAAFF9100)
                            )
                        }
                    }

                    if (photos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .background(Color(0xFF181818), RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, Color(0xFF242424)), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No stamp photos captured in logs. Snap one!",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .testTag("gallery_photo_row")
                        ) {
                            items(photos, key = { it.id }) { item ->
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { activePhotoView = item }
                                ) {
                                    AsyncImage(
                                        model = File(item.filePath),
                                        contentDescription = "Stamp thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Stamp Color tiny corner indicator
                                    val tinyColor = try {
                                        Color(android.graphics.Color.parseColor(item.stampColorHex))
                                    } catch (e: Exception) {
                                        Color.Yellow
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .background(tinyColor, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // INTUITIVE FULL-SCREEN PHOTO VIEWER DIALOG OVERLAY (WITH REAL SHARING AND DELETING)
    activePhotoView?.let { photo ->
        Dialog(
            onDismissRequest = { activePhotoView = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.95f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Close dialog button top-left
                    IconButton(
                        onClick = { activePhotoView = null },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(20.dp)
                            .background(Color(0x7F111111), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close Viewer", tint = Color.White)
                    }

                    // Display full width watermarked stamped image
                    AsyncImage(
                        model = File(photo.filePath),
                        contentDescription = "Stamped photo details",
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(bottom = 80.dp),
                        contentScale = ContentScale.Fit
                    )

                    // Overlay information board at the bottom of standard dialog
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (photo.note.isNotBlank()) photo.note else "stamped_photo.jpg",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = photo.formattedDateString,
                                        fontSize = 13.sp,
                                        color = Color.LightGray
                                    )
                                }

                                // Interactive share / delete icons
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Native Sharing
                                    FilledIconButton(
                                        onClick = {
                                            shareStampPhoto(context, photo.filePath)
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color(0xFFFF5722)
                                        )
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                                    }

                                    // Local deletion
                                    FilledIconButton(
                                        onClick = {
                                            viewModel.deletePhoto(photo)
                                            activePhotoView = null
                                            Toast.makeText(context, "Stamped photo deleted", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color(0xFFE53935)
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Logic flow handler for triggering take picture outputs & physical watermarking transformations safely
private fun executePhotoSnapping(
    context: Context,
    imageCapture: ImageCapture,
    viewModel: CameraViewModel,
    noteText: String,
    selectedColorHex: String,
    selectedStyleIndex: Int,
    filterIndex: Int,
    mainExecutor: java.util.concurrent.Executor
) {
    viewModel.setCapturing(true)

    // Save output directory path configuration
    val mediaDirectoryName = context.getString(R.string.app_name)
    val appMediaDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
        File(it, mediaDirectoryName).apply { mkdirs() }
    }
    val targetDir = if (appMediaDir != null && appMediaDir.exists()) appMediaDir else context.filesDir

    val stampDatePrefix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val outputPhotoFile = File(targetDir, "STAMP_${stampDatePrefix}.jpg")

    val fileOutputOptions = ImageCapture.OutputFileOptions.Builder(outputPhotoFile).build()

    imageCapture.takePicture(
        fileOutputOptions,
        mainExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // Background operations to load the JPEG, apply EXIF rot, stamp visual banner, save
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        ImageWatermarker.watermarkImageWithTimestamp(
                            photoFile = outputPhotoFile,
                            customText = noteText,
                            colorHex = selectedColorHex,
                            styleIndex = selectedStyleIndex,
                            filterIndex = filterIndex
                        )

                        // Insert photo path into SQLite Database
                        viewModel.savePhotoMetadata(
                            filePath = outputPhotoFile.absolutePath,
                            customLabel = noteText
                        )

                        // Inform user on main thread
                        ContextCompat.getMainExecutor(context).execute {
                            viewModel.setCapturing(false)
                            Toast.makeText(context, "Stamped photo captured & saved!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("CaptureCallback", "Watermark process failed", e)
                        ContextCompat.getMainExecutor(context).execute {
                            viewModel.setCapturing(false)
                            Toast.makeText(context, "Applied stamp saving failure", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CaptureCallback", "Image capture failed", exception)
                viewModel.setCapturing(false)
                Toast.makeText(context, "Error photographing: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

// Share intent launcher with secure modern file provider URIs
private fun shareStampPhoto(context: Context, filePath: String) {
    try {
        val imageFile = File(filePath)
        if (!imageFile.exists()) {
            Toast.makeText(context, "Image file no longer exists", Toast.LENGTH_SHORT).show()
            return
        }

        val authoritiesDomain = "com.aistudio.stampcamera.pxywqz.fileprovider"
        val secureUri: Uri = FileProvider.getUriForFile(context, authoritiesDomain, imageFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, secureUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Stamped Photo"))
    } catch (e: Exception) {
        Log.e("SharePhoto", "Error executing share", e)
        Toast.makeText(context, "Unable to execute system sharing", Toast.LENGTH_SHORT).show()
    }
}
