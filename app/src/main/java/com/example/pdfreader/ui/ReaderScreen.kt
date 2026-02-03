package com.example.pdfreader.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.pdfreader.data.BooksRepository
import com.example.pdfreader.data.SettingsRepository
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReaderScreen(
    repo: BooksRepository,
    settings: SettingsRepository,
    bookId: Long,
    onBack: () -> Unit
) {
    val book by repo.book(bookId).collectAsState(initial = null)
    val state = rememberLazyListState()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speaking by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val zoom by settings.zoom.collectAsState()
    val darkMode by settings.darkMode.collectAsState()
    val fullScreen by settings.fullScreen.collectAsState()
    var overlayVisible by remember(fullScreen) { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current
    val scrollX = rememberScrollState()
    val pageColorFilter = remember(darkMode) {
        if (!darkMode) return@remember null
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    DisposableEffect(fullScreen) {
        val activity = context as? Activity
        if (activity != null) {
            val controller = WindowInsetsControllerCompat(activity.window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullScreen) {
                if (Build.VERSION.SDK_INT >= 28) {
                    val lp = activity.window.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    activity.window.attributes = lp
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    activity.window.isStatusBarContrastEnforced = false
                    activity.window.isNavigationBarContrastEnforced = false
                }
                val previousStatus = activity.window.statusBarColor
                val previousNav = activity.window.navigationBarColor
                activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility =
                    (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                onDispose {
                    activity.window.statusBarColor = previousStatus
                    activity.window.navigationBarColor = previousNav
                }
            } else {
                if (Build.VERSION.SDK_INT >= 28) {
                    val lp = activity.window.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    activity.window.attributes = lp
                }
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = 0
                onDispose { }
            }
        } else {
            onDispose { }
        }
    }

    if (book == null) {
        TopAppBar(title = { Text("正在加载") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
        })
        return
    }
    val uri = Uri.parse(book!!.uri)
    val resolver = context.contentResolver
    val renderHandleState = produceState<PdfRenderHandle?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            val pfd = resolver.openFileDescriptor(uri, "r") ?: return@withContext null
            PdfRenderHandle(pfd, PdfRenderer(pfd))
        }
    }
    val renderHandle = renderHandleState.value
    val renderer = renderHandle?.renderer
    val renderMutex = remember(uri) { Mutex() }
    DisposableEffect(renderHandle) {
        onDispose {
            renderHandle?.renderer?.close()
            renderHandle?.pfd?.close()
        }
    }
    val pageCount = renderer?.pageCount ?: 0
    LaunchedEffect(pageCount) {
        repo.updateLastPage(bookId, state.firstVisibleItemIndex, pageCount)
    }
    var doc by remember(uri) { mutableStateOf<PDDocument?>(null) }
    DisposableEffect(uri) {
        onDispose {
            doc?.close()
            doc = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
            doc?.close()
            doc = null
        }
    }

    LaunchedEffect(fullScreen, overlayVisible) {
        if (fullScreen && overlayVisible) {
            delay(2500)
            overlayVisible = false
        }
    }

    Scaffold(
        contentWindowInsets = if (fullScreen) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!fullScreen) {
                TopAppBar(
                    title = { Text(book!!.title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                    },
                    actions = {
                        IconButton(onClick = {
                            settings.toggleFullScreen()
                        }) {
                            Icon(
                                if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = null
                            )
                        }
                        IconButton(onClick = { settings.setZoom(zoom - 0.25f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = null)
                        }
                        Text("${(zoom * 100).toInt()}%")
                        IconButton(onClick = { settings.setZoom(zoom + 0.25f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null)
                        }
                        IconButton(onClick = { settings.toggleDarkMode() }) {
                            Icon(
                                if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null
                            )
                        }
                        IconButton(onClick = {
                            val currentPage = state.firstVisibleItemIndex + 1
                            if (speaking) {
                                speaking = false
                                tts?.stop()
                                return@IconButton
                            }
                            speaking = true
                            scope.launch {
                                val engine = ensureTts(context, tts)
                                tts = engine
                                val d = doc ?: loadPdfTextDocument(context, uri).also { doc = it }
                                if (d == null) {
                                    speaking = false
                                    return@launch
                                }
                                speakCurrentPage(engine, d, currentPage)
                            }
                        }) {
                            Icon(if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        }
                    }
                )
            }
        }
    ) { padding ->
        val contentPaddingModifier = if (fullScreen) Modifier else Modifier.padding(padding)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(fullScreen) {
                    if (fullScreen) {
                        detectTapGestures(
                            onTap = { overlayVisible = !overlayVisible }
                        )
                    }
                }
                .then(contentPaddingModifier)
        ) {
            val viewportWidthDp = maxWidth
            val enableHorizontalScroll = zoom > 1.001f
            val contentWidthDp = if (enableHorizontalScroll) viewportWidthDp * zoom else viewportWidthDp
            val targetWidthPx = with(density) { contentWidthDp.roundToPx().coerceIn(360, 2200) }

            LaunchedEffect(enableHorizontalScroll) {
                if (!enableHorizontalScroll) {
                    scrollX.scrollTo(0)
                }
            }

            Box(modifier = if (enableHorizontalScroll) Modifier.horizontalScroll(scrollX) else Modifier) {
                LazyColumn(
                    modifier = Modifier.width(contentWidthDp),
                    state = state,
                    contentPadding = if (fullScreen) PaddingValues(0.dp) else PaddingValues(8.dp)
                ) {
                    items(pageCount) { index ->
                        val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = index, key2 = targetWidthPx) {
                            value = renderPageBitmap(renderer, index, renderMutex, targetWidthPx)
                        }
                        val bmp = bitmapState.value
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                                colorFilter = pageColorFilter
                            )
                        }
                    }
                }
            }

            if (fullScreen && overlayVisible) {
                Surface(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    tonalElevation = 1.dp
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        IconButton(onClick = { settings.toggleFullScreen() }) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { settings.setZoom(zoom - 0.25f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = null)
                        }
                        IconButton(onClick = { settings.setZoom(zoom + 0.25f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { settings.toggleDarkMode() }) {
                            Icon(
                                if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {
                            val currentPage = state.firstVisibleItemIndex + 1
                            if (speaking) {
                                speaking = false
                                tts?.stop()
                                return@IconButton
                            }
                            speaking = true
                            scope.launch {
                                val engine = ensureTts(context, tts)
                                tts = engine
                                val d = doc ?: loadPdfTextDocument(context, uri).also { doc = it }
                                if (d == null) {
                                    speaking = false
                                    return@launch
                                }
                                speakCurrentPage(engine, d, currentPage)
                            }
                        }) {
                            Icon(if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        }
                    }
                }
            }
        }
    }
}

private data class PdfRenderHandle(val pfd: ParcelFileDescriptor, val renderer: PdfRenderer)

private suspend fun renderPageBitmap(
    renderer: PdfRenderer?,
    index: Int,
    renderMutex: Mutex,
    targetWidthPx: Int
): Bitmap? {
    if (renderer == null) return null
    return withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val page = renderer.openPage(index)
            try {
                val scale = targetWidthPx.toFloat() / page.width.toFloat()
                val targetHeightPx = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }
}

private fun speakCurrentPage(tts: TextToSpeech, doc: PDDocument?, page: Int) {
    if (doc == null) return
    val stripper = PDFTextStripper()
    stripper.startPage = page
    stripper.endPage = page
    val text = stripper.getText(doc).take(6000)
    if (text.isNotBlank()) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "page_$page")
    }
}

private suspend fun loadPdfTextDocument(context: Context, uri: Uri): PDDocument? {
    return withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input)
        }
    }
}

private suspend fun ensureTts(context: Context, current: TextToSpeech?): TextToSpeech {
    if (current != null) return current
    return withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) cont.resume(engine!!)
            }
        }
    }
}
