package com.example.docscan

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
private fun ModeButton(selected: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick, modifier) { Text(label) }
    else OutlinedButton(onClick, modifier) { Text(label) }
}

private fun loadBitmap(f: File, maxDim: Int): Bitmap? {
    if (!f.exists()) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / s > maxDim * 2 || o.outHeight / s > maxDim * 2) s *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
}

private fun fullFrame(bw: Float, bh: Float) =
    listOf(Offset(0f, 0f), Offset(bw, 0f), Offset(bw, bh), Offset(0f, bh))

private fun toOffsets(q: FloatArray) =
    listOf(Offset(q[0], q[1]), Offset(q[2], q[3]), Offset(q[4], q[5]), Offset(q[6], q[7]))

private fun insetFrame(bmp: Bitmap): List<Offset> {
    val bw = bmp.width.toFloat()
    val bh = bmp.height.toFloat()
    return listOf(
        Offset(bw * 0.05f, bh * 0.05f), Offset(bw * 0.95f, bh * 0.05f),
        Offset(bw * 0.95f, bh * 0.95f), Offset(bw * 0.05f, bh * 0.95f)
    )
}

/** 0-3: four corners, 4-7: edge midpoints, 8: centre */
private fun handlesOf(p: List<Offset>): List<Offset> = p + listOf(
    (p[0] + p[1]) / 2f, (p[1] + p[2]) / 2f, (p[2] + p[3]) / 2f, (p[3] + p[0]) / 2f,
    Offset((p[0].x + p[1].x + p[2].x + p[3].x) / 4f, (p[0].y + p[1].y + p[2].y + p[3].y) / 4f)
)

@Composable
private fun PageImage(f: File, maxDim: Int, rev: Int, modifier: Modifier) {
    val bmp = remember(f.path, rev, f.lastModified()) { loadBitmap(f, maxDim) }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier, contentScale = ContentScale.Fit)
}

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf("home") // home | detail | settings | crop | review
    private var docs by mutableStateOf<List<Doc>>(emptyList())
    private var current by mutableStateOf<Doc?>(null)
    private var query by mutableStateOf("")
    private var ocrText by mutableStateOf("")
    private var excel by mutableStateOf<File?>(null)
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var useAi by mutableStateOf(false)
    private var apiKey by mutableStateOf("")
    private var model by mutableStateOf("gemini-flash-lite-latest")
    private var showRename by mutableStateOf(false)
    private var renameText by mutableStateOf("")
    private var showDelete by mutableStateOf(false)
    private var rev by mutableStateOf(0)
    private var cameraMode by mutableStateOf("google") // google | own

    // new-scan state (nothing is saved to the library until the user taps Save)
    private var pendingSlip = false
    private var pendingCount by mutableStateOf(0)
    private var pendingVariant by mutableStateOf("orig") // orig | color | bw
    private var cropUris by mutableStateOf<List<Uri>>(emptyList())
    private var cropIndex by mutableStateOf(0)
    private var cropBmp by mutableStateOf<Bitmap?>(null)
    private var recropIndex by mutableStateOf(-1) // >= 0: adjusting the frame of an already-cropped page in the review screen
    private var cropInit by mutableStateOf<List<Offset>?>(null) // starting frame when re-adjusting
    private val pendingFrames = mutableMapOf<Int, FloatArray>() // last frame used for each page

    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { r ->
        if (r.resultCode == RESULT_OK) onScanResult(r.data)
    }

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) startCrop(uris.take(20), false)
    }

    private var photoFile: File? = null
    private var photoSlip = false
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val f = photoFile
        if (ok && f != null && f.exists()) startCrop(listOf(Uri.fromFile(f)), photoSlip)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useAi = prefs.getBoolean("ai", false)
        apiKey = prefs.getString("key", "") ?: ""
        model = prefs.getString("model", "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest"
        cameraMode = prefs.getString("camera", "google") ?: "google"
        docs = DocStore.list(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    BackHandler(enabled = screen != "home") {
                        if (screen == "crop" && recropIndex >= 0) cancelRecrop()
                        else if (screen == "crop" || screen == "review") discardPending()
                        else screen = "home"
                    }
                    val doc = current
                    val cb = cropBmp
                    when {
                        screen == "settings" -> SettingsScreen()
                        screen == "review" -> ReviewScreen()
                        screen == "crop" && cb != null -> CropScreen(cb)
                        screen == "detail" && doc != null -> DetailScreen(doc)
                        else -> HomeScreen()
                    }
                    if (showRename) AlertDialog(
                        onDismissRequest = { showRename = false },
                        confirmButton = {
                            TextButton({ renameCurrent(); showRename = false }) { Text(stringResource(R.string.dialog_ok)) }
                        },
                        dismissButton = {
                            TextButton({ showRename = false }) { Text(stringResource(R.string.dialog_cancel)) }
                        },
                        title = { Text(stringResource(R.string.dialog_rename_title)) },
                        text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) }
                    )
                    if (showDelete) AlertDialog(
                        onDismissRequest = { showDelete = false },
                        confirmButton = {
                            TextButton({ deleteCurrent(); showDelete = false }) { Text(stringResource(R.string.btn_delete)) }
                        },
                        dismissButton = {
                            TextButton({ showDelete = false }) { Text(stringResource(R.string.dialog_cancel)) }
                        },
                        title = { Text(stringResource(R.string.dialog_delete_title)) },
                        text = { Text(stringResource(R.string.dialog_delete_msg)) }
                    )
                }
            }
        }
    }

    // ---------------- Screens ----------------
    @Composable
    private fun HomeScreen() {
        val shown = docs.filter {
            query.isBlank() || it.title.contains(query, true) || it.text.contains(query, true)
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton({ screen = "settings" }) { Text(stringResource(R.string.btn_settings)) }
                TextButton({ finishAndRemoveTask() }) { Text(stringResource(R.string.btn_quit)) }
            }
            if (status.isNotEmpty()) Text(status)
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_hint)) }, singleLine = true
            )
            if (shown.isEmpty()) Text(stringResource(R.string.empty_library))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { d -> DocRow(d) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ startCamera(false) }, Modifier.weight(1f), enabled = !busy) {
                    Text(stringResource(R.string.btn_camera))
                }
                Button({ pickImages.launch("image/*") }, Modifier.weight(1f), enabled = !busy) {
                    Text(stringResource(R.string.btn_image))
                }
            }
            OutlinedButton({ startCamera(true) }, Modifier.fillMaxWidth(), enabled = !busy) {
                Text(stringResource(R.string.btn_scan_slip))
            }
        }
    }

    @Composable
    private fun DocRow(d: Doc) {
        val date = remember(d.created) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(d.created))
        }
        Row(
            Modifier.fillMaxWidth().clickable { openDoc(d) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageImage(d.page(0), 160, rev, Modifier.size(64.dp, 80.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(stringResource(R.string.doc_meta, date, d.pages), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    /** Manual selection frame: drag the four corners, then the area is straightened. */
    @Composable
    private fun CropScreen(bmp: Bitmap) {
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        var pts by remember(bmp) { mutableStateOf(cropInit ?: insetFrame(bmp)) }
        var detecting by remember(bmp) { mutableStateOf(cropInit == null) }
        var touched by remember(bmp) { mutableStateOf(false) }
        var autoMode by remember(bmp) { mutableStateOf(0) }
        val scope = rememberCoroutineScope()
        // 先显示一个普通的框，同时在后台自动检测文件位置，检测到就移过去（除非你已经开始拖动了）
        LaunchedEffect(bmp) {
            if (cropInit == null) {
                val q = withContext(Dispatchers.Default) { DocDetect.find(bmp, 0) }
                if (q != null && !touched) pts = toOffsets(q)
            }
            detecting = false
        }
        var active by remember(bmp) { mutableStateOf(-1) }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (recropIndex >= 0) stringResource(R.string.recrop_title, recropIndex + 1)
                else stringResource(R.string.crop_title, cropIndex + 1, cropUris.size),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.crop_hint), style = MaterialTheme.typography.bodySmall)
            if (detecting) Text(stringResource(R.string.crop_detecting), style = MaterialTheme.typography.bodySmall)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxW = constraints.maxWidth.toFloat()
                val boxH = constraints.maxHeight.toFloat()
                val scale = minOf(boxW / bw, boxH / bh)
                val offX = (boxW - bw * scale) / 2f
                val offY = (boxH - bh * scale) / 2f
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(
                    Modifier.fillMaxSize().pointerInput(bmp, scale) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val hs = handlesOf(pts).map { Offset(offX + it.x * scale, offY + it.y * scale) }
                                val d = hs.map { (it - start).getDistance() }
                                val m = d.indices.minByOrNull { d[it] } ?: -1
                                active = if (m >= 0 && d[m] < 90f) m else -1
                                if (active >= 0) touched = true
                            },
                            onDrag = { change, amt ->
                                val a = active
                                if (a >= 0) {
                                    change.consume()
                                    // 0-3 corner, 4-7 edge dot (moves its two corners), 8 centre (moves everything)
                                    val idx = when {
                                        a < 4 -> listOf(a)
                                        a < 8 -> listOf(a - 4, (a - 3) % 4)
                                        else -> listOf(0, 1, 2, 3)
                                    }
                                    val dx = (amt.x / scale).coerceIn(idx.maxOf { -pts[it].x }, idx.minOf { bw - pts[it].x })
                                    val dy = (amt.y / scale).coerceIn(idx.maxOf { -pts[it].y }, idx.minOf { bh - pts[it].y })
                                    pts = pts.mapIndexed { i, p -> if (i in idx) Offset(p.x + dx, p.y + dy) else p }
                                }
                            },
                            onDragEnd = { active = -1 },
                            onDragCancel = { active = -1 }
                        )
                    }
                ) {
                    val sp = pts.map { Offset(offX + it.x * scale, offY + it.y * scale) }
                    for (i in 0..3) drawLine(Color(0xFF0E7C7B), sp[i], sp[(i + 1) % 4], strokeWidth = 5f)
                    val hs = handlesOf(pts).map { Offset(offX + it.x * scale, offY + it.y * scale) }
                    for (i in 4..7) {
                        drawCircle(Color.White, 16f, hs[i])
                        drawCircle(Color(0xFF0E7C7B), 10f, hs[i])
                    }
                    drawCircle(Color.White, 28f, hs[8])
                    drawCircle(Color(0xFF0E7C7B), 22f, hs[8])
                    for (i in 0..3) drawCircle(Color(0xFFFFB703), 24f, hs[i])
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    // 每按一次换一种检测方法：只用 GrabCut → 只用边缘 → 自动 …
                    autoMode = when (autoMode) { 0 -> 2; 2 -> 1; else -> 0 }
                    val mode = autoMode
                    scope.launch {
                        detecting = true
                        val q = withContext(Dispatchers.Default) { DocDetect.find(bmp, mode) }
                        detecting = false
                        if (q != null) pts = toOffsets(q)
                        else Toast.makeText(this@MainActivity, getString(R.string.crop_auto_fail), Toast.LENGTH_SHORT).show()
                    }
                }, Modifier.weight(1f), enabled = !detecting) { Text(stringResource(R.string.btn_auto_crop)) }
                OutlinedButton({ pts = fullFrame(bw, bh) }, Modifier.weight(1f)) {
                    Text(stringResource(R.string.btn_no_crop))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ if (recropIndex >= 0) cancelRecrop() else discardPending() }, Modifier.weight(1f)) {
                    Text(stringResource(R.string.dialog_cancel))
                }
                Button({ onCropDone(pts.flatMap { listOf(it.x, it.y) }.toFloatArray()) }, Modifier.weight(1f)) {
                    Text(stringResource(R.string.btn_use_crop))
                }
            }
        }
    }

    /** Choose which version to keep: original / enhanced / black-and-white text. */
    @Composable
    private fun ReviewScreen() {
        val r = rev
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeButton(pendingVariant == "orig", stringResource(R.string.v_orig), Modifier.weight(1f)) { setVariant("orig") }
                ModeButton(pendingVariant == "color", stringResource(R.string.v_color), Modifier.weight(1f)) { setVariant("color") }
                ModeButton(pendingVariant == "bw", stringResource(R.string.v_bw), Modifier.weight(1f)) { setVariant("bw") }
            }
            Text(
                stringResource(
                    when (pendingVariant) {
                        "color" -> R.string.v_color_desc
                        "bw" -> R.string.v_bw_desc
                        else -> R.string.v_orig_desc
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (busy) Text(status)
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0 until pendingCount) {
                    OutlinedButton({ recrop(i) }, Modifier.fillMaxWidth(), enabled = !busy) {
                        Text(stringResource(R.string.btn_adjust_frame))
                    }
                    PageImage(pendingFile(pendingVariant, i), 1200, r, Modifier.fillMaxWidth())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ discardPending() }, Modifier.weight(1f)) { Text(stringResource(R.string.btn_discard)) }
                Button({ saveReview() }, Modifier.weight(1f), enabled = !busy) { Text(stringResource(R.string.btn_save_doc)) }
            }
        }
    }

    @Composable
    private fun DetailScreen(doc: Doc) {
        val r = rev
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ screen = "home" }) { Text(stringResource(R.string.btn_back)) }
                Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            }
            if (status.isNotEmpty()) Text(status)

            Text(stringResource(R.string.sec_pdf), style = MaterialTheme.typography.titleSmall)
            OutlinedButton({ shareFile(doc.pdf, "application/pdf") }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_share_pdf))
            }
            OutlinedButton({ savePdf(doc) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_save_pdf))
            }
            OutlinedButton({ printPdf(doc) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_print))
            }

            Text(stringResource(R.string.sec_text), style = MaterialTheme.typography.titleSmall)
            Button({ runOcr(false) }, Modifier.fillMaxWidth(), enabled = !busy) {
                Text(stringResource(if (useAi) R.string.btn_ocr_ai else R.string.btn_ocr_local))
            }
            if (ocrText.isNotEmpty()) {
                OutlinedTextField(
                    ocrText, { ocrText = it; excel = null },
                    Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_result)) },
                    minLines = 8
                )
                Button({ makeExcel(doc) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_make_excel)) }
            }
            if (excel != null) {
                OutlinedButton({ shareFile(excel!!, XLSX) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_share_excel)) }
                OutlinedButton({ saveExcel(doc) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_save_excel)) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ renameText = doc.title; showRename = true }, Modifier.weight(1f)) {
                    Text(stringResource(R.string.btn_rename))
                }
                OutlinedButton({ showDelete = true }, Modifier.weight(1f)) {
                    Text(stringResource(R.string.btn_delete))
                }
            }

            for (i in 0 until doc.pages) PageImage(doc.page(i), 1200, r, Modifier.fillMaxWidth())
        }
    }

    @Composable
    private fun SettingsScreen() {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ screen = "home" }) { Text(stringResource(R.string.btn_back)) }
                Text(stringResource(R.string.btn_settings), style = MaterialTheme.typography.titleMedium)
            }
            if (status.isNotEmpty()) Text(status)
            Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val pick = { ai: Boolean -> useAi = ai; prefs.edit().putBoolean("ai", ai).apply() }
                ModeButton(!useAi, stringResource(R.string.mode_local), Modifier.weight(1f)) { pick(false) }
                ModeButton(useAi, stringResource(R.string.mode_ai), Modifier.weight(1f)) { pick(true) }
            }
            Text(
                stringResource(if (useAi) R.string.mode_ai_desc else R.string.mode_local_desc),
                style = MaterialTheme.typography.bodySmall
            )
            Text(stringResource(R.string.slip_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.setting_camera_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val pickCam = { m: String -> cameraMode = m; prefs.edit().putString("camera", m).apply() }
                ModeButton(cameraMode == "own", stringResource(R.string.cam_own), Modifier.weight(1f)) { pickCam("own") }
                ModeButton(cameraMode == "google", stringResource(R.string.cam_google), Modifier.weight(1f)) { pickCam("google") }
            }
            Text(
                stringResource(if (cameraMode == "own") R.string.cam_own_desc else R.string.cam_google_desc),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                apiKey, { apiKey = it; prefs.edit().putString("key", it).apply() },
                Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.hint_key)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                model, { model = it; prefs.edit().putString("model", it).apply() },
                Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.hint_model)) },
                singleLine = true
            )
        }
    }

    // ---------------- New scan: pending files ----------------
    private fun pendingFile(variant: String, i: Int) =
        File(File(cacheDir, "pending/$variant").apply { mkdirs() }, "page_${i + 1}.jpg")

    private fun clearPending() {
        File(cacheDir, "pending").deleteRecursively()
        pendingFrames.clear()
    }

    private fun discardPending() {
        clearPending()
        cropBmp = null
        cropInit = null
        recropIndex = -1
        pendingCount = 0
        screen = "home"
    }

    /** Review screen: go back to the frame editor for one page, before saving. */
    private fun recrop(i: Int) {
        val src = pendingFile("src", i)
        if (!src.exists()) pendingFile("orig", i).copyTo(src, overwrite = true)
        val b = BitmapFactory.decodeFile(src.path) ?: return
        cropInit = pendingFrames[i]?.let { toOffsets(it) } ?: fullFrame(b.width.toFloat(), b.height.toFloat())
        recropIndex = i
        cropBmp = b
        screen = "crop"
    }

    private fun cancelRecrop() {
        recropIndex = -1
        cropBmp = null
        cropInit = null
        screen = "review"
    }

    private fun startCamera(slip: Boolean) {
        if (slip && apiKey.isBlank()) {
            status = getString(R.string.status_slip_need_key)
            screen = "settings"
            return
        }
        if (cameraMode == "own") openCamera(slip) else startScan(slip)
    }

    private fun openCamera(slip: Boolean) {
        val f = File(File(cacheDir, "shared").apply { mkdirs() }, "photo_${System.currentTimeMillis()}.jpg")
        photoFile = f
        photoSlip = slip
        status = ""
        takePicture.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", f))
    }

    private fun startScan(slip: Boolean) {
        if (slip && apiKey.isBlank()) {
            status = getString(R.string.status_slip_need_key)
            screen = "settings"
            return
        }
        pendingSlip = slip
        status = ""
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE) // crop / rotate only, no filters
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { status = getString(R.string.status_scan_fail, it.message ?: "") }
    }

    private fun onScanResult(data: Intent?) {
        val res = GmsDocumentScanningResult.fromActivityResultIntent(data)
        val uris = res?.pages?.map { it.imageUri } ?: emptyList()
        if (uris.isNotEmpty()) {
            clearPending()
            uris.forEachIndexed { i, u ->
                contentResolver.openInputStream(u)!!.use { input ->
                    pendingFile("orig", i).outputStream().use { input.copyTo(it) }
                }
            }
            startReview(uris.size)
        }
    }

    private fun startCrop(uris: List<Uri>, slip: Boolean) {
        clearPending()
        cropInit = null
        pendingSlip = slip
        status = ""
        cropUris = uris
        cropIndex = 0
        loadCrop()
    }

    private fun decodeForCrop(uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val m = maxOf(info.size.width, info.size.height)
            var s = 1
            while (m / s > 3000) s *= 2
            if (s > 1) decoder.setTargetSampleSize(s)
        }
    } catch (e: Exception) {
        null
    }

    private fun loadCrop() {
        val b = decodeForCrop(cropUris[cropIndex])
        if (b == null) {
            status = getString(R.string.status_scan_fail, "image")
            discardPending()
        } else {
            cropBmp = b
            screen = "crop"
        }
    }

    private fun onCropDone(pts: FloatArray) {
        val src = cropBmp ?: return
        val out = Enhance.warp(src, pts)
        if (recropIndex >= 0) {
            // re-adjusting from the review screen: replace this page and refresh its previews
            val i = recropIndex
            pendingFile("orig", i).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            out.recycle()
            pendingFile("color", i).delete()
            pendingFile("bw", i).delete()
            pendingFrames[i] = pts
            recropIndex = -1
            cropBmp = null
            cropInit = null
            screen = "review"
            ensureVariant(pendingVariant)
            return
        }
        // keep the uncropped photo so the frame can be adjusted again before saving
        pendingFile("src", cropIndex).outputStream().use { src.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        pendingFile("orig", cropIndex).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        out.recycle()
        pendingFrames[cropIndex] = pts
        cropIndex++
        if (cropIndex < cropUris.size) loadCrop() else startReview(cropUris.size)
    }

    private fun startReview(count: Int) {
        pendingCount = count
        pendingVariant = prefs.getString("variant", "orig") ?: "orig"
        cropBmp = null
        screen = "review"
        ensureVariant(pendingVariant)
    }

    private fun setVariant(v: String) {
        pendingVariant = v
        prefs.edit().putString("variant", v).apply()
        ensureVariant(v)
    }

    private fun variantReady(v: String) = (0 until pendingCount).all { pendingFile(v, it).exists() }

    private fun ensureVariant(v: String) {
        rev++
        if (v == "orig" || variantReady(v)) return
        lifecycleScope.launch {
            busy = true
            status = getString(R.string.status_cleaning)
            try {
                withContext(Dispatchers.Default) {
                    for (i in 0 until pendingCount) {
                        if (pendingFile(v, i).exists()) continue
                        val src = BitmapFactory.decodeFile(pendingFile("orig", i).path) ?: continue
                        val out = if (v == "color") Enhance.enhanceColor(src) else Enhance.clean(src, false)
                        src.recycle()
                        pendingFile(v, i).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        out.recycle()
                    }
                }
            } catch (e: Throwable) {
                status = getString(R.string.status_clean_fail, e.message ?: "")
            }
            busy = false
            rev++
        }
    }

    private fun saveReview() {
        val v = if (variantReady(pendingVariant)) pendingVariant else "orig"
        val count = pendingCount
        val title = getString(
            R.string.default_title,
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        )
        lifecycleScope.launch {
            busy = true
            var saved: Doc? = null
            try {
                saved = withContext(Dispatchers.IO) {
                    val d = DocStore.create(
                        this@MainActivity,
                        (0 until count).map { Uri.fromFile(pendingFile(v, it)) }, null, title
                    )
                    PdfWriter.write((0 until d.pages).map { d.page(it) }, d.pdf)
                    d
                }
            } catch (e: Throwable) {
                status = getString(R.string.status_scan_fail, e.message ?: "")
            }
            busy = false
            if (saved != null) {
                clearPending()
                docs = DocStore.list(this@MainActivity)
                openDoc(saved)
                if (pendingSlip) runOcr(true)
            }
        }
    }

    // ---------------- Library actions ----------------
    private fun openDoc(doc: Doc) {
        current = doc
        ocrText = doc.text
        excel = null
        status = ""
        screen = "detail"
    }

    private fun renameCurrent() {
        val doc = current ?: return
        val t = renameText.trim()
        if (t.isNotEmpty()) {
            val d = doc.copy(title = t)
            DocStore.save(d)
            current = d
            docs = DocStore.list(this)
        }
    }

    private fun deleteCurrent() {
        current?.let { DocStore.delete(it) }
        docs = DocStore.list(this)
        current = null
        ocrText = ""
        excel = null
        screen = "home"
    }

    private fun printPdf(doc: Doc) {
        val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val name = "${safeName(doc.title)}.pdf"
        pm.print(doc.title, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder(name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?, callback: WriteResultCallback
            ) {
                try {
                    doc.pdf.inputStream().use { i ->
                        FileOutputStream(destination.fileDescriptor).use { o -> i.copyTo(o) }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, null)
    }

    // ---------------- OCR ----------------
    // On-device OCR: group text lines into rows / cells by position
    private suspend fun ocr(uris: List<Uri>): List<List<String>> {
        val rec = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val out = mutableListOf<List<String>>()
        try {
            for ((i, uri) in uris.withIndex()) {
                val text = rec.process(InputImage.fromFilePath(this, uri)).await()
                val lines = text.textBlocks.flatMap { it.lines }
                    .filter { it.boundingBox != null }
                    .sortedBy { it.boundingBox!!.centerY() }
                val rows = mutableListOf<MutableList<MlText.Line>>()
                for (l in lines) {
                    val b = l.boundingBox!!
                    val last = rows.lastOrNull()
                    val avgY = last?.map { it.boundingBox!!.centerY() }?.average()
                    if (last != null && abs(b.centerY() - avgY!!) < b.height() * 0.6) last.add(l)
                    else rows.add(mutableListOf(l))
                }
                rows.forEach { r -> out.add(r.sortedBy { it.boundingBox!!.left }.map { it.text }) }
                if (i < uris.lastIndex) out.add(emptyList())
            }
        } finally {
            rec.close()
        }
        return out
    }

    private fun runOcr(forceAi: Boolean) {
        val doc = current ?: return
        val ai = useAi || forceAi
        lifecycleScope.launch {
            busy = true
            status = getString(if (ai) R.string.status_ocr_ai else R.string.status_ocr_local)
            try {
                val uris = (0 until doc.pages).map { Uri.fromFile(doc.page(it)) }
                val text = if (ai) {
                    if (apiKey.isBlank()) throw Exception(getString(R.string.err_need_key))
                    withContext(Dispatchers.IO) {
                        uris.joinToString("\n\n") {
                            GeminiOcr.recognize(this@MainActivity, it, apiKey.trim(), model.trim())
                        }
                    }
                } else {
                    ocr(uris).joinToString("\n") { it.joinToString(" | ") }
                }
                val d = doc.copy(text = text)
                DocStore.save(d)
                current = d
                docs = DocStore.list(this@MainActivity)
                ocrText = text
                excel = null
                status = getString(if (text.isBlank()) R.string.status_ocr_empty else R.string.status_ocr_done)
            } catch (e: Exception) {
                status = getString(R.string.status_ocr_fail, e.message ?: "")
            } finally {
                busy = false
            }
        }
    }

    private fun makeExcel(doc: Doc) {
        val rows = ocrText.lines().map { l ->
            if (l.isBlank()) emptyList() else l.split("|").map { it.trim() }
        }
        val f = File(shareDir(), "${safeName(doc.title)}.xlsx")
        XlsxWriter.write(rows, f, getString(R.string.sheet_name))
        excel = f
        status = getString(R.string.status_excel_ready)
    }

    // ---------------- Share / save ----------------
    private fun shareDir() = File(cacheDir, "shared").apply { mkdirs() }

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun shareFile(f: File, mime: String) {
        val u = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, getString(R.string.chooser_share)))
    }

    private fun saveToDownloads(name: String, mime: String, write: (java.io.OutputStream) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, getString(R.string.toast_save_fail), Toast.LENGTH_SHORT).show()
            return
        }
        contentResolver.openOutputStream(uri)!!.use(write)
        Toast.makeText(this, getString(R.string.toast_saved, name), Toast.LENGTH_LONG).show()
    }

    private fun savePdf(doc: Doc) {
        saveToDownloads("${safeName(doc.title)}.pdf", "application/pdf") { out ->
            doc.pdf.inputStream().use { it.copyTo(out) }
        }
    }

    private fun saveExcel(doc: Doc) {
        val f = excel ?: return
        saveToDownloads("${safeName(doc.title)}.xlsx", XLSX) { out -> f.inputStream().use { it.copyTo(out) } }
    }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
