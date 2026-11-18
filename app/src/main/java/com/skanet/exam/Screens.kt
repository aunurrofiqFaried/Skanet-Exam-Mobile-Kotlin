package com.skanet.exam

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.os.SystemClock
import androidx.compose.foundation.layout.displayCutoutPadding
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Siswa(
    val nama: String,
    val nis: String,
    val kelas: String,
    val jurusan: String
)

// Data contoh, nanti diganti data asli dari login
val SISWA_CONTOH = Siswa(
    nama = "Ahmad Fauzi",
    nis = "12345",
    kelas = "XII TKJ 1",
    jurusan = "Teknik Komputer dan Jaringan"
)

// ---------------------------------------------------------------- CHECKLIST

@Composable
fun ChecklistScreen(onContinue: () -> Unit, onFix: (CheckId) -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(DeviceChecks.run(ctx)) }

    LaunchedEffect(Unit) {
        while (true) {
            items = DeviceChecks.run(ctx)
            delay(1500)
        }
    }

    val passed = items.count { it.passed }
    val allOk = passed == items.size
    val progress by animateFloatAsState(passed.toFloat() / items.size, label = "progress")

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(20.dp)
        ) {
            HeaderLabel("PEMERIKSAAN PERANGKAT")
            Spacer(Modifier.height(8.dp))
            Text("Memeriksa kesiapan", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Semua poin harus lulus sebelum masuk ke dashboard",
                color = Ui.Muted, fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Ui.Ink.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Brush.horizontalGradient(listOf(Ui.Orange, Ui.Ember)))
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("$passed/${items.size}", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))

            items.forEach { item ->
                CheckRow(item, onFix)
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                text = if (allOk) "LANJUTKAN" else "MENUNGGU PEMERIKSAAN",
                enabled = allOk,
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinue
            )
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem, onFix: (CheckId) -> Unit) {
    val color = if (item.passed) Ui.Orange else Ui.Red
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f))
                    .border(1.dp, color.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (item.passed) "✓" else "✕",
                    color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(item.detail, color = Ui.Muted, fontSize = 13.sp)
            }
            if (!item.passed && item.fixable) {
                Text(
                    "PERBAIKI",
                    color = Ui.Orange, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onFix(item.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- DASHBOARD

@Composable
fun DashboardScreen(
    siswa: Siswa,
    photoVersion: Int,
    onOpenProfile: () -> Unit,
    state: ExamLoadState,
    busy: Boolean,
    message: String?,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onStartExam: () -> Unit,
    onLogout: () -> Unit
) {
    var now by remember { mutableStateOf(Date()) }
    var nowElapsed by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            nowElapsed = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    // Segarkan jadwal dari server tiap 30 detik
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            onRefresh()
        }
    }

    val locale = Locale("id", "ID")
    val jamMenit = SimpleDateFormat("HH:mm", locale).format(now)
    val detik = SimpleDateFormat("ss", locale).format(now)
    val tanggal = SimpleDateFormat("EEEE, d MMMM yyyy", locale).format(now)
    val inisial = siswa.nama.split(" ").take(2)
        .mapNotNull { it.firstOrNull() }.joinToString("").uppercase()

    val ready = state as? ExamLoadState.Ready
    val status = ready?.config?.status
    val sudahSelesai = status == "selesai"
    val berjalan = status == "berjalan"
    val belumMulai = ready != null && status == "belum" && ready.config.mulaiAt > nowElapsed
    val bisaMulai = ready != null && !sudahSelesai && !belumMulai && !busy
    val labelTombol = when {
        busy -> "MEMERIKSA..."
        sudahSelesai -> "UJIAN SUDAH SELESAI"
        belumMulai -> "MENUNGGU JAM MULAI"
        berjalan -> "LANJUTKAN UJIAN"
        else -> "MULAI UJIAN"
    }

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderLabel("SISTEM UJIAN SKANET", withRedDot = true)
                Spacer(Modifier.weight(1f))
                Text(
                    "KELUAR",
                    color = Ui.Orange, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLogout() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Selamat datang,", color = Ui.Muted, fontSize = 15.sp)
            Text(siswa.nama, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Jam dan tanggal
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "WAKTU SEKARANG",
                        color = Ui.Orange, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            jamMenit,
                            style = TextStyle(
                                brush = Brush.horizontalGradient(listOf(Ui.Orange, Ui.Red)),
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Ubuntu
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            detik,
                            color = Ui.Muted, fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    Text(tanggal, color = Ui.Muted, fontSize = 15.sp)
                }
                Spacer(Modifier.height(16.dp))

                // Profil (ketuk untuk mengubah)
                GlassCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProfile() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(siswa, photoVersion, 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(siswa.nama, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("NIS ${siswa.nis}", color = Ui.Muted, fontSize = 14.sp)
                        }
                        Text(
                            "UBAH",
                            color = Ui.Orange, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Ui.Ink.copy(alpha = 0.08f))
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        InfoBlock("KELAS", siswa.kelas, Modifier.weight(1f))
                        InfoBlock("JURUSAN", siswa.jurusan, Modifier.weight(2f))
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Kartu ujian
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "UJIAN",
                        color = Ui.Orange, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    when (state) {
                        ExamLoadState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulseDot()
                                Spacer(Modifier.width(12.dp))
                                Text("Memuat jadwal ujian...", color = Ui.Muted)
                            }
                        }

                        ExamLoadState.NoExam -> {
                            Text("Belum ada ujian aktif", fontWeight = FontWeight.Medium)
                            Text(
                                "Hubungi pengawas ujian.",
                                color = Ui.Muted, fontSize = 13.sp
                            )
                        }

                        is ExamLoadState.Error -> {
                            Text(state.message, color = Ui.Red, fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "COBA LAGI",
                                color = Ui.Orange, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onRetry() }
                                    .padding(vertical = 8.dp)
                            )
                        }

                        is ExamLoadState.Ready -> {
                            val c = state.config
                            Text(c.nama, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${c.jamMulaiLabel}  ·  ${c.durasiMenit} menit",
                                color = Ui.Muted, fontSize = 14.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            when {
                                sudahSelesai -> {
                                    val dihentikan = c.alasan == "pelanggaran"
                                    Text(
                                        if (dihentikan) "Ujian dihentikan karena pelanggaran"
                                        else "Anda sudah menyelesaikan ujian ini",
                                        color = if (dihentikan) Ui.Red else Ui.Orange,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                berjalan -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PulseDot()
                                        Spacer(Modifier.width(12.dp))
                                        Text("Ujian sedang berjalan, sisa ", color = Ui.Muted)
                                        Text(
                                            formatHms(c.berakhirAt - nowElapsed),
                                            color = Ui.Orange, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                belumMulai -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Dimulai dalam ", color = Ui.Muted)
                                        Text(
                                            formatHms(c.mulaiAt - nowElapsed),
                                            color = Ui.Orange, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                else -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PulseDot()
                                        Spacer(Modifier.width(12.dp))
                                        Text("Ujian sudah dibuka", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (message != null) {
                Text(
                    message,
                    color = Ui.Red, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            PrimaryButton(
                text = labelTombol,
                enabled = bisaMulai,
                modifier = Modifier.fillMaxWidth(),
                onClick = onStartExam
            )
            Spacer(Modifier.height(10.dp))
            CopyrightFooter()
        }
    }
}

private fun formatHms(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999) / 1000
    return String.format(
        Locale.US, "%02d:%02d:%02d",
        total / 3600, (total % 3600) / 60, total % 60
    )
}

@Composable
private fun HeaderLabel(text: String, withRedDot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (withRedDot) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Ui.Red)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            color = Ui.Orange, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 4.sp
        )
    }
}

@Composable
private fun InfoBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            color = Ui.Orange, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- UJIAN

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExamScreen(
    url: String,
    endAt: Long,
    violations: Int,
    maxViolations: Int,
    onBackPressed: () -> Unit,
    onTimeUp: () -> Unit,
    onFinish: () -> Unit
) {
    BackHandler(enabled = true) { onBackPressed() }

    var remainingMs by remember {
        mutableStateOf((endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
    }
    LaunchedEffect(endAt) {
        while (true) {
            remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (remainingMs == 0L) {
                onTimeUp()
                break
            }
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            }
        )

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .displayCutoutPadding()
                .padding(8.dp)
        ) {
            if (remainingMs > 0L) {
                TimerPill(remainingMs, violations, maxViolations)
            } else {
                FinishButton(onFinish)
            }
        }
    }
}

@Composable
private fun TimerPill(remainingMs: Long, violations: Int, maxViolations: Int) {
    val totalSec = (remainingMs + 999) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val text = if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }

    val low = remainingMs <= 5 * 60 * 1000L
    val color = if (low) Ui.Red else Ui.Orange
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.92f))
            .border(1.dp, color, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "SISA WAKTU",
            color = Ui.Muted, fontFamily = Ubuntu,
            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
        )
        Text(
            text,
            color = color, fontFamily = Ubuntu,
            fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        if (maxViolations > 0 || violations > 0) {
            Text(
                if (maxViolations > 0) "PELANGGARAN $violations/$maxViolations"
                else "PELANGGARAN $violations",
                color = if (violations > 0) Ui.Red else Ui.Muted,
                fontFamily = Ubuntu,
                fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun FinishButton(onFinish: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) {
        if (confirm) {
            delay(4000)
            confirm = false
        }
    }

    val shape = RoundedCornerShape(16.dp)
    val colors = if (confirm) listOf(Ui.Ember, Ui.Red) else listOf(Ui.Orange, Ui.Ember)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(Brush.horizontalGradient(colors))
            .clickable { if (confirm) onFinish() else confirm = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (confirm) "KETUK LAGI UNTUK KONFIRMASI" else "SELESAI UJIAN",
            color = Color.White, fontFamily = Ubuntu,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
        )
    }
}

// ---------------------------------------------------------------- MASUK / DAFTAR

val JURUSAN_LIST = listOf(
    "Rekayasa Perangkat Lunak",
    "Teknik Komputer dan Jaringan",
    "Desain Komunikasi Visual",
    "Kuliner"
)

// Ganti teks ini sesuai keinginan kamu
const val COPYRIGHT_TEXT = "© 2026 Rekayasa Perangkat Lunak Skanet."

@Composable
fun CopyrightFooter(modifier: Modifier = Modifier) {
    Text(
        COPYRIGHT_TEXT,
        color = Ui.Muted,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun AuthScreen(
    checking: Boolean,
    busy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String, String, String) -> Unit
) {
    var register by remember { mutableStateOf(false) }
    var nis by remember { mutableStateOf("") }
    var nama by remember { mutableStateOf("") }
    var kelas by remember { mutableStateOf("") }
    var jurusan by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var konfirmasi by remember { mutableStateOf("") }
    var kode by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        localError = null
        if (register) {
            when {
                nis.isBlank() || nama.isBlank() || kelas.isBlank() || kode.isBlank() ->
                    localError = "Semua kolom wajib diisi."
                jurusan.isBlank() ->
                    localError = "Pilih jurusan terlebih dahulu."
                password.length < 6 ->
                    localError = "Password minimal 6 karakter."
                password != konfirmasi ->
                    localError = "Konfirmasi password tidak sama."
                else -> onRegister(
                    nis.trim(), nama.trim(), kelas.trim(),
                    jurusan, password, kode.trim()
                )
            }
        } else {
            if (nis.isBlank() || password.isEmpty()) {
                localError = "NIS dan password wajib diisi."
            } else {
                onLogin(nis.trim(), password)
            }
        }
    }

    val shownError = localError ?: error

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                HeaderLabel("SISTEM UJIAN SKANET", withRedDot = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (register) "Buat akun siswa" else "Masuk",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    if (register) "Isi data sesuai data di sekolah"
                    else "Masuk dengan NIS dan password kamu",
                    color = Ui.Muted, fontSize = 14.sp
                )
                Spacer(Modifier.height(20.dp))

                if (checking) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot()
                            Spacer(Modifier.width(12.dp))
                            Text("Memeriksa sesi...", color = Ui.Muted)
                        }
                    }
                } else {
                    GlassCard(Modifier.fillMaxWidth()) {
                        AuthField(nis, { nis = it }, "NIS", enabled = !busy)
                        if (register) {
                            AuthField(nama, { nama = it }, "Nama lengkap", enabled = !busy)
                            AuthField(kelas, { kelas = it }, "Kelas (contoh: XII TKJ 1)", enabled = !busy)
                            JurusanDropdown(jurusan, { jurusan = it }, enabled = !busy)
                        }
                        AuthField(password, { password = it }, "Password", password = true, enabled = !busy)
                        if (register) {
                            AuthField(konfirmasi, { konfirmasi = it }, "Ulangi password", password = true, enabled = !busy)
                            AuthField(kode, { kode = it }, "Kode pendaftaran dari sekolah", enabled = !busy)
                        }
                    }

                    if (shownError != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(shownError, color = Ui.Red, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = when {
                            busy -> "MEMPROSES..."
                            register -> "DAFTAR"
                            else -> "MASUK"
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { submit() }
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            if (register) "Sudah punya akun? " else "Belum punya akun? ",
                            color = Ui.Muted, fontSize = 14.sp
                        )
                        Text(
                            if (register) "MASUK" else "DAFTAR",
                            color = Ui.Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !busy) {
                                register = !register
                                localError = null
                            }
                        )
                    }
                }
            }

            CopyrightFooter(Modifier.padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ui.Orange,
    unfocusedBorderColor = Ui.Ink.copy(alpha = 0.18f),
    focusedLabelColor = Ui.Orange,
    unfocusedLabelColor = Ui.Muted,
    cursorColor = Ui.Orange,
    focusedTextColor = Ui.Ink,
    unfocusedTextColor = Ui.Ink,
    focusedContainerColor = Color.White.copy(alpha = 0.6f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.4f)
)

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Ubuntu) },
        singleLine = true,
        enabled = enabled,
        visualTransformation =
            if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
        ),
        shape = RoundedCornerShape(14.dp),
        colors = authFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    )
}

@Composable
private fun JurusanDropdown(
    value: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Jurusan", fontFamily = Ubuntu) },
            trailingIcon = { Text("▾", color = Ui.Orange, fontSize = 18.sp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = authFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        // Lapisan transparan supaya seluruh kolom bisa diketuk
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            JURUSAN_LIST.forEach { j ->
                DropdownMenuItem(
                    text = { Text(j, fontFamily = Ubuntu) },
                    onClick = {
                        onSelect(j)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- FOTO & PROFIL

@Composable
fun Avatar(siswa: Siswa, photoVersion: Int, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bitmap = remember(siswa.nis, photoVersion) {
        ProfilePhoto.load(ctx, siswa.nis)?.asImageBitmap()
    }
    val inisial = siswa.nama.split(" ").take(2)
        .mapNotNull { it.firstOrNull() }.joinToString("").uppercase()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Ui.Orange, Ui.Red))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto profil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                inisial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
        }
    }
}

@Composable
fun ProfileScreen(
    siswa: Siswa,
    photoVersion: Int,
    busy: Boolean,
    error: String?,
    success: String?,
    onPickPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var nama by remember(siswa.nis) { mutableStateOf(siswa.nama) }
    var kelas by remember(siswa.nis) { mutableStateOf(siswa.kelas) }
    var jurusan by remember(siswa.nis) { mutableStateOf(siswa.jurusan) }
    var lama by remember { mutableStateOf("") }
    var baru by remember { mutableStateOf("") }
    var konfirmasi by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickPhoto(uri)
    }

    // Setelah berhasil disimpan, kosongkan kolom password
    LaunchedEffect(success) {
        if (success != null) {
            lama = ""
            baru = ""
            konfirmasi = ""
        }
    }

    fun submit() {
        localError = null
        when {
            nama.trim().length < 3 -> localError = "Nama minimal 3 karakter."
            kelas.isBlank() -> localError = "Kelas wajib diisi."
            jurusan.isBlank() -> localError = "Pilih jurusan."
            baru.isNotEmpty() && lama.isEmpty() ->
                localError = "Isi password lama untuk mengganti password."
            baru.isNotEmpty() && baru.length < 6 ->
                localError = "Password baru minimal 6 karakter."
            baru != konfirmasi ->
                localError = "Konfirmasi password baru tidak sama."
            else -> onSave(nama.trim(), kelas.trim(), jurusan, lama, baru)
        }
    }

    val shownError = localError ?: error

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    "‹ KEMBALI",
                    color = Ui.Orange, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onBack() }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Profil", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Perbarui data dan foto profilmu", color = Ui.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))

                // Foto
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(siswa, photoVersion, 110.dp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "UBAH FOTO",
                            color = Ui.Orange, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    picker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                        Text(
                            "HAPUS FOTO",
                            color = Ui.Muted, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onRemovePhoto() }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    Text(
                        "Foto hanya disimpan di HP ini",
                        color = Ui.Muted, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(20.dp))

                // Data diri
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "NIS",
                        color = Ui.Orange, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(siswa.nis, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("NIS tidak dapat diubah", color = Ui.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))

                    AuthField(nama, { nama = it }, "Nama lengkap", enabled = !busy)
                    AuthField(kelas, { kelas = it }, "Kelas", enabled = !busy)
                    JurusanDropdown(jurusan, { jurusan = it }, enabled = !busy)
                }
                Spacer(Modifier.height(16.dp))

                // Ganti password
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "UBAH PASSWORD (OPSIONAL)",
                        color = Ui.Orange, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    AuthField(lama, { lama = it }, "Password lama", password = true, enabled = !busy)
                    AuthField(baru, { baru = it }, "Password baru", password = true, enabled = !busy)
                    AuthField(konfirmasi, { konfirmasi = it }, "Ulangi password baru", password = true, enabled = !busy)
                }

                if (shownError != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(shownError, color = Ui.Red, fontSize = 13.sp)
                }
                if (success != null && shownError == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(success, color = Ui.Orange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (busy) "MENYIMPAN..." else "SIMPAN PERUBAHAN",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { submit() }
                )
            }

            CopyrightFooter(Modifier.padding(bottom = 10.dp))
        }
    }
}