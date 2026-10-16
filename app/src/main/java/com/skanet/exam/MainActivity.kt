package com.skanet.exam

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { CHECKLIST, AUTH, DASHBOARD, PROFILE, EXAM }

class MainActivity : ComponentActivity() {

    companion object {
        private const val KEY_TOKEN = "token"

        // Lama splash screen dalam milidetik. Ubah sesuai selera (2500 = 2,5 detik).
        private const val SPLASH_MS = 3000L
    }

    private var screen by mutableStateOf(Screen.CHECKLIST)
    private val examActive: Boolean get() = screen == Screen.EXAM

    // Akun
    private var siswa by mutableStateOf<Siswa?>(null)
    private var authChecking by mutableStateOf(false)
    private var authBusy by mutableStateOf(false)
    private var authError by mutableStateOf<String?>(null)

    // Profil
    private var profileBusy by mutableStateOf(false)
    private var profileError by mutableStateOf<String?>(null)
    private var profileSuccess by mutableStateOf<String?>(null)
    private var photoVersion by mutableStateOf(0)

    // Data dari server
    private var examState: ExamLoadState by mutableStateOf(ExamLoadState.Loading)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)

    // Ujian yang sedang berjalan
    private var examUrl by mutableStateOf("")
    private var examEndAt by mutableStateOf(0L)   // waktu selesai (elapsedRealtime)
    private var activeExamId = -1
    private var timeUp = false

    private var violations by mutableStateOf(0)

    private var maxViolations by mutableStateOf(0)

    private var armed = false   // true setelah benar-benar terkunci
    private var ready = false   // true setelah dialog izin notifikasi selesai

    private val prefs by lazy { getSharedPreferences("ujian", MODE_PRIVATE) }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            ready = true
            if (examActive) tryLock()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tahan splash screen selama SPLASH_MS
        val splash = installSplashScreen()
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        Handler(Looper.getMainLooper()).postDelayed({ keepSplash = false }, SPLASH_MS)

        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SECURE
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ready = true
        }

        setContent {
            when (screen) {
                Screen.CHECKLIST -> ChecklistScreen(
                    onContinue = { onChecklistDone() },
                    onFix = { openFix(it) }
                )
                Screen.AUTH -> AuthScreen(
                    checking = authChecking,
                    busy = authBusy,
                    error = authError,
                    onLogin = { nis, pass -> doLogin(nis, pass) },
                    onRegister = { nis, nama, kelas, jurusan, pass, kode ->
                        doRegister(nis, nama, kelas, jurusan, pass, kode)
                    }
                )
                Screen.DASHBOARD -> {
                    val s = siswa
                    if (s != null) {
                        DashboardScreen(
                            siswa = s,
                            photoVersion = photoVersion,
                            state = examState,
                            busy = busy,
                            message = message,
                            onRetry = { loadConfig(silent = false) },
                            onRefresh = { loadConfig(silent = true) },
                            onStartExam = { startExam() },
                            onOpenProfile = { openProfile() },
                            onLogout = { doLogout() }
                        )
                    }
                }
                Screen.PROFILE -> {
                    val s = siswa
                    if (s != null) {
                        ProfileScreen(
                            siswa = s,
                            photoVersion = photoVersion,
                            busy = profileBusy,
                            error = profileError,
                            success = profileSuccess,
                            onPickPhoto = { savePhoto(it) },
                            onRemovePhoto = { removePhoto() },
                            onSave = { nama, kelas, jurusan, lama, baru ->
                                saveProfile(nama, kelas, jurusan, lama, baru)
                            },
                            onBack = { screen = Screen.DASHBOARD }
                        )
                    }
                }
                Screen.EXAM -> ExamScreen(
                    url = examUrl,
                    endAt = examEndAt,
                    violations = violations,
                    maxViolations = maxViolations,
                    onBackPressed = {
                        if (!timeUp) {
                            startAlarm(5000L)
                            reportViolation("back")
                        }
                    },
                    onTimeUp = { onExamTimeUp() },
                    onFinish = { finishExam() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (examActive) tryLock()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!examActive) return

        if (hasFocus) {
            hideSystemBars()
            if (isLocked() && !timeUp) armed = true
            stopAlarm()
        } else if (armed) {
            startAlarm(0L)
            reportViolation("keluar_app")
        }
    }

    // ---------------------------------------------------------- Akun

    private fun savedToken(): String? = prefs.getString(KEY_TOKEN, null)

    // Setelah checklist lulus: coba masuk otomatis dengan token tersimpan
    private fun onChecklistDone() {
        screen = Screen.AUTH
        val token = savedToken() ?: return

        authChecking = true
        authError = null
        lifecycleScope.launch {
            Api.me(token)
                .onSuccess { enterDashboard(it) }
                .onFailure { err ->
                    if ((err as? ApiException)?.code == 401) {
                        prefs.edit().remove(KEY_TOKEN).apply()
                    } else {
                        authError = err.message
                    }
                }
            authChecking = false
        }
    }

    private fun doLogin(nis: String, password: String) {
        authBusy = true
        authError = null
        lifecycleScope.launch {
            Api.login(nis, password)
                .onSuccess { saveSessionAndEnter(it) }
                .onFailure { authError = it.message }
            authBusy = false
        }
    }

    private fun doRegister(
        nis: String, nama: String, kelas: String,
        jurusan: String, password: String, kode: String
    ) {
        authBusy = true
        authError = null
        lifecycleScope.launch {
            Api.register(nis, nama, kelas, jurusan, password, kode)
                .onSuccess { saveSessionAndEnter(it) }
                .onFailure { authError = it.message }
            authBusy = false
        }
    }

    private fun saveSessionAndEnter(result: AuthResult) {
        prefs.edit().putString(KEY_TOKEN, result.token).apply()
        enterDashboard(result.siswa)
    }

    private fun enterDashboard(s: Siswa) {
        siswa = s
        authError = null
        message = null
        screen = Screen.DASHBOARD
        loadConfig(silent = false)
    }

    private fun doLogout() {
        val token = savedToken()
        clearSession()
        if (token != null) {
            lifecycleScope.launch { Api.logout(token) }
        }
    }

    private fun clearSession(reason: String? = null) {
        prefs.edit().remove(KEY_TOKEN).apply()
        siswa = null
        examState = ExamLoadState.Loading
        message = null
        profileError = null
        profileSuccess = null
        authError = reason
        screen = Screen.AUTH
    }

    // ---------------------------------------------------------- Profil

    private fun openProfile() {
        profileError = null
        profileSuccess = null
        screen = Screen.PROFILE
    }

    private fun saveProfile(
        nama: String, kelas: String, jurusan: String,
        passwordLama: String, passwordBaru: String
    ) {
        val token = savedToken()
        if (token == null) {
            clearSession()
            return
        }
        profileBusy = true
        profileError = null
        profileSuccess = null

        lifecycleScope.launch {
            Api.updateProfile(token, nama, kelas, jurusan, passwordLama, passwordBaru)
                .onSuccess {
                    siswa = it
                    profileSuccess = if (passwordBaru.isNotEmpty()) {
                        "Profil dan password berhasil diperbarui."
                    } else {
                        "Profil berhasil diperbarui."
                    }
                }
                .onFailure { err ->
                    if ((err as? ApiException)?.code == 401) {
                        clearSession("Sesi berakhir. Silakan masuk lagi.")
                    } else {
                        profileError = err.message
                    }
                }
            profileBusy = false
        }
    }

    // Foto disimpan hanya di HP ini, tidak dikirim ke server
    private fun savePhoto(uri: Uri) {
        val nis = siswa?.nis ?: return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ProfilePhoto.save(applicationContext, nis, uri)
            }
            if (ok) {
                photoVersion += 1
                profileError = null
                profileSuccess = "Foto profil diperbarui."
            } else {
                profileSuccess = null
                profileError = "Gagal memproses foto. Coba pilih gambar lain."
            }
        }
    }

    private fun removePhoto() {
        val nis = siswa?.nis ?: return
        ProfilePhoto.delete(applicationContext, nis)
        photoVersion += 1
        profileError = null
        profileSuccess = "Foto profil dihapus."
    }

    // ---------------------------------------------------------- Server

    private fun loadConfig(silent: Boolean) {
        val token = savedToken()
        if (token == null) {
            clearSession()
            return
        }
        if (!silent) examState = ExamLoadState.Loading

        lifecycleScope.launch {
            Api.fetchConfig(token)
                .onSuccess { cfg ->
                    examState = if (cfg == null) {
                        ExamLoadState.NoExam
                    } else {
                        ExamLoadState.Ready(cfg)
                    }
                }
                .onFailure { err ->
                    if ((err as? ApiException)?.code == 401) {
                        clearSession("Sesi berakhir. Silakan masuk lagi.")
                    } else if (!silent) {
                        examState = ExamLoadState.Error(
                            err.message ?: "Gagal terhubung ke server."
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------------- Ujian

    // Mulai ujian baru, atau lanjutkan jika sedang berjalan (server yang menentukan)
    private fun startExam() {
        if (busy) return
        val token = savedToken()
        if (token == null) {
            clearSession()
            return
        }
        val cfg = (examState as? ExamLoadState.Ready)?.config
        if (cfg == null) {
            message = "Jadwal ujian belum dimuat."
            return
        }

        busy = true
        message = null
        lifecycleScope.launch {
            val result = Api.startExam(token, cfg.id)
            busy = false
            result
                .onSuccess { r -> beginExam(cfg.id, r) }
                .onFailure { err ->
                    if ((err as? ApiException)?.code == 401) {
                        clearSession("Sesi berakhir. Silakan masuk lagi.")
                    } else {
                        message = err.message
                        loadConfig(silent = true)
                    }
                }
        }
    }

    private fun beginExam(examId: Int, r: StartResult) {
        timeUp = false
        activeExamId = examId
        examUrl = r.url
        violations = r.pelanggaran
        maxViolations = r.maksPelanggaran
        examEndAt = SystemClock.elapsedRealtime() + r.sisaMs
        screen = Screen.EXAM
        tryLock()
    }

    // Countdown habis: alarm dinonaktifkan, siswa tinggal menekan "Selesai Ujian"
    private fun onExamTimeUp() {
        timeUp = true
        armed = false
        stopAlarm()
    }

    private fun finishExam() {
        armed = false   // matikan dulu sebelum lepas kunci
        stopAlarm()

        val token = savedToken()
        val examId = activeExamId

        // Tandai selesai di tampilan lebih dulu supaya tombol tidak sempat aktif lagi
        (examState as? ExamLoadState.Ready)?.let {
            examState = ExamLoadState.Ready(it.config.copy(status = "selesai"))
        }

        message = null
        screen = Screen.DASHBOARD
        if (isLocked()) stopLockTask()
        showSystemBars()

        lifecycleScope.launch {
            if (token != null && examId >= 0) {
                Api.finishExam(token, examId)
            }
            loadConfig(silent = true)
        }
    }

    // Lapor pelanggaran ke server. Jika server memutuskan batas tercapai, ujian dihentikan.
    private fun reportViolation(jenis: String) {
        val token = savedToken() ?: return
        val examId = activeExamId
        if (examId < 0) return

        lifecycleScope.launch {
            Api.reportViolation(token, examId, jenis).onSuccess { r ->
                if (examActive && examId == activeExamId) {
                    violations = r.pelanggaran
                    maxViolations = r.maks
                    if (r.dihentikan) {
                        forceFinish(
                            if (r.maks > 0) {
                                "Ujian dihentikan karena pelanggaran mencapai batas (${r.maks}x)."
                            } else {
                                "Ujian dihentikan karena pelanggaran."
                            }
                        )
                    }
                }
            }
        }
    }

    // Ujian diakhiri paksa oleh server (pelanggaran melewati batas)
    private fun forceFinish(reason: String) {
        armed = false
        stopAlarm()

        (examState as? ExamLoadState.Ready)?.let {
            examState = ExamLoadState.Ready(
                it.config.copy(status = "selesai", alasan = "pelanggaran")
            )
        }

        message = reason
        screen = Screen.DASHBOARD
        if (isLocked()) stopLockTask()
        showSystemBars()
        loadConfig(silent = true)
    }

    // ---------------------------------------------------------- Kunci

    private fun tryLock() {
        if (!ready) return
        hideSystemBars()
        if (!isLocked()) startLockTask()
    }

    private fun isLocked(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    // ---------------------------------------------------------- Alarm & lain-lain

    private fun startAlarm(durationMs: Long) {
        val intent = Intent(this, AlarmService::class.java)
            .putExtra(AlarmService.EXTRA_DURATION_MS, durationMs)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAlarm() {
        stopService(Intent(this, AlarmService::class.java))
    }

    private fun openFix(id: CheckId) {
        val intent = when (id) {
            CheckId.INTERNET -> Intent(Settings.ACTION_WIFI_SETTINGS)
            CheckId.TIME -> Intent(Settings.ACTION_DATE_SETTINGS)
            CheckId.NOTIF ->
                if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
            CheckId.BATTERY -> null
        }
        intent?.let { startActivity(it) }
    }
}