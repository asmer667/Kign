package dev.keygen.app

import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ---------- معاملات الزر ----------
data class Params(
    var base: Long = 0xFF4A4A52,
    var hi: Long = 0xFF8A8A96,
    var lo: Long = 0xFF1C1C22,
    var glow: Long = 0xFF3B5BFF,
    var rad: Float = 14f,
    var bev: Float = 12f,
    var sheen: Float = 0.55f,
    var glowA: Float = 0.40f,
    var inSh: Float = 0.35f,
)

// ---------- الرسم ----------
private fun mix(a: Long, b: Long, t: Float): Int {
    val ar = (a shr 16 and 0xFF).toFloat(); val ag = (a shr 8 and 0xFF).toFloat(); val ab = (a and 0xFF).toFloat()
    val br = (b shr 16 and 0xFF).toFloat(); val bg = (b shr 8 and 0xFF).toFloat(); val bb = (b and 0xFF).toFloat()
    val r = (ar + (br - ar) * t).roundToInt()
    val g = (ag + (bg - ag) * t).roundToInt()
    val bl = (ab + (bb - ab) * t).roundToInt()
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or bl
}

fun drawKeyInto(c: Canvas, w: Float, h: Float, p: Params, pressed: Boolean, withLetter: Boolean) {
    val M = w * 0.05f
    val x = M; val y = M
    val rw = w - 2 * M; val rh = h - 2 * M
    val rad = p.rad * (w / 128f).coerceAtLeast(1f)
    val bev = p.bev * (w / 128f).coerceAtLeast(1f)

    // توهج خارجي سفلي
    if (p.glowA > 0f && !pressed) {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x01FFFFFF
            setShadowLayer(18f * (w / 128f).coerceAtLeast(1f), 0f, 4f, (p.glow and 0xFFFFFF).toInt() or ((p.glowA * 255).roundToInt() shl 24))
        }
        c.drawRoundRect(RectF(x, y, x + rw, y + rh), rad, rad, glowPaint)
    }

    // جسم الزر: تدرج عمودي
    val top = if (pressed) mix(p.base, p.lo, 0.55f) else mix(p.base, p.hi, 0.35f)
    val bot = if (pressed) mix(p.base, p.lo, 0.80f) else mix(p.base, p.lo, 0.45f)
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, y, 0f, y + rh, intArrayOf(top, bot), null, Shader.TileMode.CLAMP)
    }
    c.drawRoundRect(RectF(x, y, x + rw, y + rh), rad, rad, bodyPaint)

    // الحد المعدني (Bevel)
    if (bev > 0f) {
        val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = bev
            shader = LinearGradient(
                0f, y, 0f, y + rh,
                intArrayOf(mix(p.base, p.hi, 0.15f), mix(p.base, p.lo, 0.75f)),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(RectF(x, y, x + rw, y + rh), rad, rad, bevelPaint)

        // إعادة تعبئة الداخل بعد القص
        c.save()
        val inner = Path().apply { addRoundRect(RectF(x + bev / 2, y + bev / 2, x + rw - bev / 2, y + rh - bev / 2), (rad - bev / 2).coerceAtLeast(2f), (rad - bev / 2).coerceAtLeast(2f), Path.Direction.CW) }
        c.clipPath(inner)
        c.drawRect(x, y, x + rw, y + rh, bodyPaint)
        c.restore()
    }

    // اللمعة العلوية
    if (p.sheen > 0f) {
        c.save()
        val clip = Path().apply { addRoundRect(RectF(x + 2, y + 2, x + rw - 2, y + rh * 0.55f), (rad - 2).coerceAtLeast(2f), (rad - 2).coerceAtLeast(2f), Path.Direction.CW) }
        c.clipPath(clip)
        val sheenA = (if (pressed) p.sheen * 0.25f else p.sheen * 0.75f) * 255
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, y, 0f, y + rh * 0.55f,
                intArrayOf((mix(p.hi, 0xFFFFFFFF, 0.3f) and 0xFFFFFF).toInt() or (sheenA.roundToInt() shl 24), mix(p.hi, p.hi, 0f) and 0x00FFFFFF),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(x, y, x + rw, y + rh, sheenPaint)
        c.restore()
    }

    // الظل الداخلي السفلي
    if (p.inSh > 0f) {
        c.save()
        val clip2 = Path().apply { addRoundRect(RectF(x + 2, y + 2, x + rw - 2, y + rh - 2), (rad - 2).coerceAtLeast(2f), (rad - 2).coerceAtLeast(2f), Path.Direction.CW) }
        c.clipPath(clip2)
        val inA = (p.inSh * (if (pressed) 0.9f else 0.55f) * 255).roundToInt()
        val inPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, y + rh * 0.55f, 0f, y + rh,
                intArrayOf(p.lo.toInt() and 0x00FFFFFF, (p.lo and 0xFFFFFF).toInt() or (inA shl 24)),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(x, y, x + rw, y + rh, inPaint)
        c.restore()
    }

    // حافة داخلية حادة
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = mix(p.base, p.hi, if (pressed) 0f else 0.25f) and 0x80FFFFFF.toInt()
    }
    c.drawRoundRect(RectF(x + 1, y + 1, x + rw - 1, y + rh - 1), (rad - 1).coerceAtLeast(1f), (rad - 1).coerceAtLeast(1f), rim)

    if (withLetter) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEFFFFFFF.toInt()
            textSize = rh * 0.4f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(3f, 0f, 2f, 0x80000000.toInt())
        }
        val baseline = y + rh / 2 - (tp.descent() + tp.ascent()) / 2
        c.drawText("q", x + rw / 2, baseline, tp)
    }
}

fun renderBitmap(p: Params, pressed: Boolean, withLetter: Boolean, wide: Boolean, scale: Int): Bitmap {
    val w = if (wide) 384 * scale else 128 * scale
    val h = 140 * scale
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    drawKeyInto(Canvas(bmp), w.toFloat(), h.toFloat(), p, pressed, withLetter)
    return bmp
}

// ---------- الحفظ ----------
fun savePng(activity: MainActivity, bmp: Bitmap, name: String): Boolean {
    return try {
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KeyGen")
        }
        val uri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        activity.contentResolver.openOutputStream(uri).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (e: Exception) { false }
}

// ---------- الواجهة ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { KeyGenScreen(this) } }
    }
}

@Composable
fun KeyGenScreen(activity: MainActivity) {
    var base by remember { mutableLongStateOf(0xFF4A4A52) }
    var hi by remember { mutableLongStateOf(0xFF8A8A96) }
    var lo by remember { mutableLongStateOf(0xFF1C1C22) }
    var glow by remember { mutableLongStateOf(0xFF3B5BFF) }
    var rad by remember { mutableFloatStateOf(14f) }
    var bev by remember { mutableFloatStateOf(12f) }
    var sheen by remember { mutableFloatStateOf(0.55f) }
    var glowA by remember { mutableFloatStateOf(0.40f) }
    var inSh by remember { mutableFloatStateOf(0.35f) }
    var scale by remember { mutableIntStateOf(2) }

    val p = remember(base, hi, lo, glow, rad, bev, sheen, glowA, inSh) {
        Params(base, hi, lo, glow, rad, bev, sheen, glowA, inSh)
    }

    fun applyPreset(pp: Params) {
        base = pp.base; hi = pp.hi; lo = pp.lo; glow = pp.glow
        rad = pp.rad; bev = pp.bev; sheen = pp.sheen; glowA = pp.glowA; inSh = pp.inSh
    }

    fun export(tag: String, pressed: Boolean, letter: Boolean, wide: Boolean) {
        val bmp = renderBitmap(p, pressed, letter, wide, scale)
        val ok = savePng(activity, bmp, "key_${tag}_${System.currentTimeMillis()}")
        Toast.makeText(activity, if (ok) "تم الحفظ في الصور/KeyGen" else "فشل الحفظ", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("مولّد أزرار FUTO Keyboard", style = MaterialTheme.typography.titleLarge)

        // معاينة
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            PreviewKey(p, "عادي") { drawKeyInto(it.nativeCanvas, 128f, 140f, p, false, true) }
            PreviewKey(p, "مضغوط") { drawKeyInto(it.nativeCanvas, 128f, 140f, p, true, true) }
            PreviewKey(p, "منبثق") { drawKeyInto(it.nativeCanvas, 128f, 140f, p, false, false) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PreviewKey(p, "زر المسافة", Modifier.width(220.dp)) { drawKeyInto(it.nativeCanvas, 384f, 140f, p, false, false) }
        }

        // ألوان
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorField("الأساس", base) { base = it }
            ColorField("اللمعة", hi) { hi = it }
            ColorField("الظل", lo) { lo = it }
            ColorField("التوهج", glow) { glow = it }
        }

        SliderRow("استدارة الزوايا", rad, 2f..40f) { rad = it }
        SliderRow("عمق الحد", bev, 0f..30f) { bev = it }
        SliderRow("اللمعة العلوية", sheen, 0f..1f) { sheen = it }
        SliderRow("شدة التوهج", glowA, 0f..1f) { glowA = it }
        SliderRow("الظل الداخلي", inSh, 0f..1f) { inSh = it }
        SliderRow("دقة التصدير (×$scale)", scale.toFloat(), 1f..4f) { scale = it.roundToInt() }

        // قوالب
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { applyPreset(Params(0xFF5A5F6A, 0xFFC9CCD4, 0xFF17181C, 0xFF2A2A33, 14f, 16f, 0.70f, 0f, 0.45f)) }) { Text("معدني") }
            Button(onClick = { applyPreset(Params(0xFF23283A, 0xFF7C86C9, 0xFF0C0E16, 0xFF3B5BFF, 12f, 8f, 0.45f, 0.55f, 0.30f)) }) { Text("توهج أزرق") }
            Button(onClick = { applyPreset(Params(0xFF2B2B33, 0xFF3A3A44, 0xFF1A1A20, 0xFF000000, 8f, 0f, 0.20f, 0f, 0.15f)) }) { Text("مسطّح") }
        }

        // تصدير
        Text("تصدير", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { export("normal", false, true, false) }) { Text("عادي") }
            Button(onClick = { export("pressed", true, true, false) }) { Text("مضغوط") }
            Button(onClick = { export("popup", false, false, false) }) { Text("منبثق") }
            Button(onClick = { export("space", false, false, true) }) { Text("مسافة") }
        }
        Text(
            "الصور تُحفظ في: الصور/KeyGen — أبعاد: 128×140 (256/384/512 حسب الدقة) والمسافة 384×140. أدخلها في محرر الثيم ضمن Other assets ثم اربطها بالـ matchrules.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun PreviewKey(p: Params, label: String, modifier: Modifier = Modifier, draw: (androidx.compose.ui.graphics.drawscope.DrawScope) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier.width(90.dp).height(100.dp)) { drawIntoCanvas { draw(it) } }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ColorField(label: String, value: Long, onChange: (Long) -> Unit) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        var hex by remember(value) { mutableStateOf("%06X".format(value and 0xFFFFFF)) }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                hex = v.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }.take(6)
                hex.toLongOrNull(16)?.let { onChange(0xFF000000 or it) }
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.1f".format(value)}", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
