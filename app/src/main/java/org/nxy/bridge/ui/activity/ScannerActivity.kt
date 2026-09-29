package org.nxy.bridge.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nxy.bridge.ui.model.ScanResultParser
import org.nxy.bridge.ui.theme.BridgeTheme
import zxingcpp.BarcodeReader
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import androidx.compose.ui.geometry.Rect as UiRect

/**
 * 全屏扫码页：扫描窗口内识别二维码，或从图片选择解码；
 * 解析结果通过 setResult 回传，由主页写入配置。
 */
class ScannerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ScannerActivity"

        // 回传解析结果的扩展键
        const val EXTRA_SCAN_RESULT = "extra_scan_result"
        private const val STATE_PICKER_OPEN = "picker_open"
        private const val STATE_CAMERA_UNAVAILABLE = "camera_unavailable"
    }

    /** 图片解码结果 */
    sealed interface DecodeResult {
        data class Success(val texts: List<String>) : DecodeResult
        data class Failure(val reason: String) : DecodeResult
    }

    // 相机是否完成绑定并开始预览
    private var cameraReady by mutableStateOf(false)

    // 相机不可用（权限拒绝、无后置相机、绑定失败）
    private var cameraUnavailable by mutableStateOf(false)

    // 是否有闪光灯硬件
    private var hasFlashUnit by mutableStateOf(false)

    // 手电筒是否开启
    private var torchOn by mutableStateOf(false)

    // 窗口内有可解码但内容无效的二维码
    private var invalidCodeInWindow by mutableStateOf(false)

    // 多码候选列表，非空时暂停分析
    private var candidates by mutableStateOf<List<String>>(emptyList())

    // 错误消息，非空时显示对话框
    private var errorMessage by mutableStateOf<String?>(null)

    private var pickerOpen = false

    // 是否已回传结果，防止重复保存和重复导航
    private var resultApplied = false

    // 相机启动重试链是否已在运行
    private var cameraStartLooping = false

    // 页面是否已销毁，停止重试链
    private var destroyed = false

    private var camera: Camera? = null
    private var previewViewRef: PreviewView? = null

    // 扫描窗口在 PreviewView 坐标系中的矩形，UI 线程写入、分析回调读取
    @Volatile
    private var windowRectInPreview: RectF? = null

    // 多码候选、选图或回传完成期间暂停分析
    @Volatile
    private var analysisEnabled = true

    private val reader by lazy {
        BarcodeReader(
            BarcodeReader.Options(
                formats = setOf(BarcodeReader.Format.QR_CODE), tryHarder = true
            )
        )
    }

    // 相机后台分析线程
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> onImagePicked(uri) }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestCameraStart()
        } else {
            // 权限拒绝不弹框，直接进入系统选图
            fallbackToPicker()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickerOpen = savedInstanceState?.getBoolean(STATE_PICKER_OPEN) ?: false
        cameraUnavailable = savedInstanceState?.getBoolean(STATE_CAMERA_UNAVAILABLE) ?: false

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        window.setBackgroundDrawableResource(android.R.color.black)

        setContent {
            BridgeTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    ScannerScreen()
                }
            }
        }

        if (pickerOpen) return
        if (cameraUnavailable) {
            finish()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            requestCameraStart()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PICKER_OPEN, pickerOpen)
        outState.putBoolean(STATE_CAMERA_UNAVAILABLE, cameraUnavailable)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        // 离开关灯并停止分析线程，相机随生命周期解绑
        destroyed = true
        camera?.cameraControl?.enableTorch(false)
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * 扫码页布局：相机预览、遮罩与交互按钮。
     */
    @Composable
    private fun ScannerScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!cameraUnavailable) {
                CameraPreview(modifier = Modifier.fillMaxSize())
            }
            when {
                cameraReady -> {
                    ScannerOverlay(
                        invalid = invalidCodeInWindow,
                        torchOn = torchOn,
                        torchAvailable = hasFlashUnit,
                        onBack = ::finish,
                        onToggleTorch = ::toggleTorch,
                        onPicker = ::launchPicker
                    )
                }

                !cameraUnavailable -> Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            CandidatesDialog(candidates = candidates, onCandidateSelected = { text ->
                candidates = emptyList()
                parseAndFinish(text, fromCamera = false)
            }, onDismiss = {
                candidates = emptyList()
                resumeAfterDialog()
                invalidCodeInWindow = false
            })

            errorMessage?.let { message ->
                AlertDialog(onDismissRequest = { dismissError() }, confirmButton = {
                    TextButton(onClick = { dismissError() }) { Text("确定") }
                }, title = { Text("识别失败") }, text = { Text(message) })
            }
        }
    }

    private fun dismissError() {
        errorMessage = null
        resumeAfterDialog()
    }

    // 对话框关闭后恢复：相机可用回扫码页，不可用则返回管理页
    private fun resumeAfterDialog() {
        if (!cameraUnavailable) {
            analysisEnabled = true
            if (!cameraReady) requestCameraStart()
        } else finish()
    }

    /**
     * 相机预览区域。
     */
    @Composable
    private fun CameraPreview(modifier: Modifier = Modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    previewViewRef = this
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        if (cameraStartLooping && width > 0 && height > 0) {
                            cameraStartLooping = false
                            startCamera(this)
                        }
                    }
                }
            }, modifier = modifier
        )
    }

    /**
     * 遮罩与交互层：窗口外遮罩、窗口边框、返回按钮和操作按钮；
     * 竖屏按钮横排在底部，横屏纵排在右侧。
     */
    @Composable
    private fun ScannerOverlay(
        invalid: Boolean,
        torchOn: Boolean,
        torchAvailable: Boolean,
        onBack: () -> Unit,
        onToggleTorch: () -> Unit,
        onPicker: () -> Unit
    ) {
        // 窗口边框颜色，在绘制层外预取
        val borderColor = if (invalid) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
        val density = LocalDensity.current

        @Composable
        fun TorchButton() {
            FilledIconButton(
                onClick = onToggleTorch,
                enabled = torchAvailable,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.Black.copy(alpha = 0.25f),
                    disabledContentColor = Color.Gray
                )
            ) {
                Icon(
                    if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = "手电筒"
                )
            }
        }

        @Composable
        fun PickerButton() {
            FilledIconButton(
                onClick = onPicker,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Rounded.PhotoLibrary, contentDescription = "选择图片"
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val statusBarHeight = with(density) {
                WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp()
            }
            val isLandscape = maxWidth > maxHeight
            val window = with(density) {
                if (isLandscape) {
                    val scanAreaWidth = (maxWidth - 136.dp).coerceAtLeast(0.dp)
                    val scanAreaLeft = 80.dp.coerceAtMost(scanAreaWidth)
                    val availableWidth = (scanAreaWidth - scanAreaLeft - 24.dp).coerceAtLeast(0.dp)
                    val side = minOf(availableWidth, (maxHeight - 48.dp).coerceAtLeast(0.dp))
                    UiRect(
                        offset = Offset(
                            (scanAreaLeft + (availableWidth - side) / 2).toPx(),
                            ((maxHeight - side) / 2).toPx()
                        ),
                        size = Size(side.toPx(), side.toPx())
                    )
                } else {
                    val topClearance =
                        maxOf(statusBarHeight + 80.dp, 128.dp).coerceAtMost(maxHeight)
                    val bottomClearance = (maxHeight - 128.dp).coerceAtLeast(topClearance)
                    val centerY = topClearance + (bottomClearance - topClearance) * 0.382f
                    val side = minOf(
                        (maxWidth - 48.dp).coerceAtLeast(0.dp),
                        (centerY - topClearance) * 2,
                        (bottomClearance - centerY) * 2
                    )
                    UiRect(
                        offset = Offset(
                            ((maxWidth - side) / 2).toPx(),
                            (centerY - side / 2).toPx()
                        ),
                        size = Size(side.toPx(), side.toPx())
                    )
                }
            }

            windowRectInPreview = RectF(
                window.left, window.top, window.right, window.bottom
            )

            // 窗口外半透明遮罩，窗口内部完全透明露出原相机画面
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cornerRadius = CornerRadius(12.dp.toPx())
                val full = Path().apply {
                    addRect(UiRect(0f, 0f, size.width, size.height))
                }
                val hole = Path().apply {
                    addRoundRect(RoundRect(window, cornerRadius))
                }
                val mask = Path.combine(PathOperation.Difference, full, hole)
                drawPath(mask, Color.Black.copy(alpha = 0.55f))

                // 窗口边框，无效码时红色，平时主题色
                drawRoundRect(
                    color = borderColor,
                    topLeft = window.topLeft,
                    size = window.size,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 16.dp,
                        top = if (isLandscape) 16.dp else statusBarHeight + 16.dp
                    )
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            }

            if (isLandscape) {
                val buttonSpacing = (maxHeight - 128.dp).coerceIn(0.dp, 32.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(buttonSpacing)
                    ) {
                        TorchButton()
                        PickerButton()
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    TorchButton()
                    PickerButton()
                }
            }

        }
    }

    /**
     * 多码候选列表。
     */
    @Composable
    private fun CandidatesDialog(
        candidates: List<String>, onCandidateSelected: (String) -> Unit, onDismiss: () -> Unit
    ) {
        if (candidates.isEmpty()) return
        AlertDialog(onDismissRequest = onDismiss, title = { Text("选择链接") }, text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                candidates.forEach { text ->
                    TextButton(onClick = { onCandidateSelected(text) }) {
                        Text(text = text, maxLines = 1)
                    }
                }
            }
        }, confirmButton = {}, dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        })
    }

    /**
     * 延迟到首帧布局后绑定相机，确保 PreviewView 已有尺寸。
     */
    private fun requestCameraStart() {
        if (cameraStartLooping || destroyed) return
        cameraStartLooping = true
        awaitPreviewAndStart()
    }

    private fun awaitPreviewAndStart() {
        if (destroyed) return
        val previewView = previewViewRef
        if (previewView != null && previewView.width > 0 && previewView.height > 0) {
            cameraStartLooping = false
            startCamera(previewView)
        }
    }

    private fun startCamera(previewView: PreviewView) {
        val future = try {
            ProcessCameraProvider.getInstance(this)
        } catch (error: Exception) {
            Log.w(TAG, "camera initialization failed", error)
            fallbackToPicker()
            return
        }
        future.addListener({
            if (destroyed || isFinishing || cameraUnavailable) return@addListener
            try {
                val provider = future.get()

                // 无后置相机时直接进入选图
                if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    Log.w(TAG, "no back camera")
                    fallbackToPicker()
                    return@addListener
                }

                val viewPort = previewView.viewPort
                if (viewPort == null) {
                    Log.w(TAG, "viewport null")
                    fallbackToPicker()
                    return@addListener
                }

                // 预览与分析共用 ViewPort，保证解码区域与可见窗口对应
                val preview = Preview.Builder().build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888).build()

                analysis.setAnalyzer(analysisExecutor, ::analyzeFrame)

                val useCaseGroup = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
                    .setViewPort(viewPort).build()

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroup
                )

                hasFlashUnit = camera?.cameraInfo?.hasFlashUnit() ?: false
                cameraReady = true
                Log.i(TAG, "camera bound, flash= $hasFlashUnit")
            } catch (e: Exception) {
                Log.w(TAG, "bind camera failed", e)
                fallbackToPicker()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() = setTorch(!torchOn)

    private fun analyzeFrame(image: ImageProxy) {
        try {
            val rect = windowRectInPreview
            if (!analysisEnabled || rect == null || rect.isEmpty) return

            // 窗口逆映射到分析帧并设置裁切，只解码可见窗口内的像素
            val mapped = mapWindowToAnalysisCrop(image, rect) ?: return
            image.setCropRect(mapped)
            val results = reader.read(image)
            handleResults(results)
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed", e)
        } finally {
            image.close()
        }
    }

    /**
     * 可视窗口逆映射到分析帧像素区域。
     * 依据 CameraX 官方坐标转换文档：缓冲区裁切矩形与视图顶点按旋转偏移
     * 建立 polyToPoly 矩阵，再求逆把视图坐标换回缓冲区坐标。
     * 映射未就绪、不可逆或与可见区域无交集时返回 null，跳过本帧。
     */
    private fun mapWindowToAnalysisCrop(image: ImageProxy, window: RectF): Rect? {
        val previewView = previewViewRef ?: return null
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return null

        val cropRect = image.cropRect
        val rotationDegrees = image.imageInfo.rotationDegrees

        // 缓冲区裁切矩形顶点（顺时针）
        val source = floatArrayOf(
            cropRect.left.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.bottom.toFloat(),
            cropRect.left.toFloat(),
            cropRect.bottom.toFloat()
        )

        // 视图顶点（顺时针），按旋转度数偏移对应关系
        val destination = floatArrayOf(
            0f, 0f, viewWidth, 0f, viewWidth, viewHeight, 0f, viewHeight
        )
        val vertexSize = 2
        val shiftOffset = rotationDegrees / 90 * vertexSize
        val temp = destination.clone()
        for (toIndex in source.indices) {
            val fromIndex = (toIndex + shiftOffset) % source.size
            destination[toIndex] = temp[fromIndex]
        }

        val forward = Matrix()
        forward.setPolyToPoly(source, 0, destination, 0, 4)
        val inverse = Matrix()
        if (!forward.invert(inverse)) return null

        // 视图窗口 -> 缓冲区坐标，旋转为 90 度倍数时结果仍为正向矩形
        val mapped = RectF(window)
        inverse.mapRect(mapped)

        // 仅保留 ViewPort 可见缓冲区内的部分，禁止偷扫全帧
        if (!mapped.intersect(RectF(cropRect))) return null
        val rect = Rect(
            ceil(mapped.left).toInt().coerceIn(0, image.width),
            ceil(mapped.top).toInt().coerceIn(0, image.height),
            floor(mapped.right).toInt().coerceIn(0, image.width),
            floor(mapped.bottom).toInt().coerceIn(0, image.height)
        )
        if (rect.isEmpty) return null
        return rect
    }

    private fun handleResults(results: List<BarcodeReader.Result>) {
        if (results.isEmpty()) {
            invalidCodeInWindow = false
            return
        }

        val texts = results.mapNotNull { it.text }
        if (texts.isEmpty()) {
            invalidCodeInWindow = true
            return
        }

        if (texts.size > 1) {
            showCandidates(texts)
            return
        }

        parseAndFinish(texts.first(), fromCamera = true)
    }

    // 多个可解码码：暂停分析，展示候选列表供用户选择
    private fun showCandidates(texts: List<String>) {
        analysisEnabled = false
        candidates = texts
    }

    /**
     * 解析单个码内容并回传：相机路径无效时红框等待，图片路径无效时弹窗提示。
     */
    private fun parseAndFinish(text: String, fromCamera: Boolean) {
        val parsed = ScanResultParser.parse(text)
        if (parsed == null) {
            if (fromCamera) {
                // 有码但不合法：红框，等码离开后恢复
                invalidCodeInWindow = true
            } else {
                // 内容无效：弹窗提示，关闭后恢复
                errorMessage = "二维码内容无效"
            }
            return
        }
        if (fromCamera) {
            analysisEnabled = false
            runOnUiThread { applyParsedAndFinish(parsed) }
        } else {
            applyParsedAndFinish(parsed)
        }
    }

    private fun applyParsedAndFinish(parsed: ScanResultParser.ParsedScan) {
        if (resultApplied || destroyed || isFinishing) return
        resultApplied = true
        analysisEnabled = false
        val intent = Intent().putExtra(EXTRA_SCAN_RESULT, Gson().toJson(parsed))
        setResult(RESULT_OK, intent)
        Log.i(
            TAG,
            "scan result ready, url= $parsed.url, parameters= $parsed.parameters, landscape= $parsed.landscape, keepScreenOn= $parsed.keepScreenOn, disableBack= $parsed.disableBack"
        )
        finish()
    }

    /**
     * 相机不可用（权限拒绝、无后置相机、绑定失败）：
     * 不弹框，只自动拉起一次系统选图。
     */
    private fun fallbackToPicker() {
        if (destroyed || isFinishing || cameraUnavailable) return
        analysisEnabled = false
        cameraReady = false
        cameraUnavailable = true
        windowRectInPreview = null
        launchPicker()
    }

    private fun launchPicker() {
        // 选图期间暂停分析与手电筒
        analysisEnabled = false
        setTorch(false)
        // Photo Picker 在不支持的设备上由 AndroidX 自动回退 ACTION_OPEN_DOCUMENT
        try {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            pickerOpen = true
        } catch (error: Exception) {
            Log.w(TAG, "image picker unavailable", error)
            errorMessage = "无法打开图片选择器"
        }
    }

    private fun setTorch(enabled: Boolean) {
        val cam = camera ?: return
        if (!hasFlashUnit) return
        torchOn = enabled
        cam.cameraControl.enableTorch(enabled)
    }

    private fun onImagePicked(uri: Uri?) {
        pickerOpen = false
        if (!cameraUnavailable && !cameraReady) requestCameraStart()
        if (uri == null) {
            // 取消选择：相机可用回扫码页，否则返回管理页
            if (cameraUnavailable) {
                finish()
            } else {
                analysisEnabled = true
            }
            return
        }
        lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { decodeImage(uri) }
            } catch (error: Exception) {
                Log.w(TAG, "decode image failed", error)
                DecodeResult.Failure("无法读取图片")
            }
            when (result) {
                is DecodeResult.Success -> handleImageTexts(result.texts)
                is DecodeResult.Failure -> errorMessage = result.reason
            }
        }
    }

    private fun handleImageTexts(texts: List<String>) {
        if (texts.size > 1) {
            showCandidates(texts)
            return
        }
        parseAndFinish(texts.first(), fromCamera = false)
    }

    /**
     * 按原始分辨率解码整张图片，并按 EXIF 方向旋转。
     */
    private fun decodeImage(uri: Uri): DecodeResult {
        val bitmap = openStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return DecodeResult.Failure("图片格式不支持或无法读取。")

        val rotated = rotateByExif(bitmap, uri)
        val results = reader.read(rotated)
        val texts = results.mapNotNull { it.text }
        return if (texts.isEmpty()) {
            DecodeResult.Failure("图片中未发现二维码。")
        } else {
            DecodeResult.Success(texts)
        }
    }

    private fun openStream(uri: Uri): InputStream? = try {
        contentResolver.openInputStream(uri)
    } catch (e: Exception) {
        Log.w(TAG, "open stream failed", e)
        null
    }

    /**
     * 按 EXIF 方向旋转位图。
     */
    private fun rotateByExif(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val orientation = openStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.w(TAG, "rotate failed", e)
            bitmap
        }
    }
}
