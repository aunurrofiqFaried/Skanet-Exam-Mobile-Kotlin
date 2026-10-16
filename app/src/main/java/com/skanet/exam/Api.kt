package com.skanet.exam

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ApiConfig {
    // Salin dari file lama: IP laptop kamu dan API key
    const val BASE_URL = "https://ganti dengan api anda/"
    const val API_KEY = "ganti api anda"
}

data class ExamConfig(
    val id: Int,
    val nama: String,
    val durasiMenit: Int,
    val jamMulaiLabel: String,
    val mulaiAt: Long,       // waktu mulai (elapsedRealtime HP)
    val status: String,      // "belum", "berjalan", "selesai" (dari server, per siswa)
    val alasan: String,      // alasan selesai: "waktu", "manual", "pelanggaran", atau ""
    val berakhirAt: Long     // untuk status "berjalan": waktu habis (elapsedRealtime HP)
)

data class StartResult(
    val url: String,
    val sisaMs: Long,
    val pelanggaran: Int,
    val maksPelanggaran: Int
)

data class ViolationResult(
    val pelanggaran: Int,
    val maks: Int,
    val dihentikan: Boolean
)

sealed interface ExamLoadState {
    object Loading : ExamLoadState
    object NoExam : ExamLoadState
    data class Error(val message: String) : ExamLoadState
    data class Ready(val config: ExamConfig) : ExamLoadState
}

data class AuthResult(val token: String, val siswa: Siswa)

class ApiException(message: String, val code: Int) : Exception(message)

object Api {

    suspend fun login(nis: String, password: String): Result<AuthResult> = safe {
        val res = call(
            "POST", "/api/login.php",
            JSONObject().put("nis", nis).put("password", password)
        )
        parseAuth(res)
    }

    suspend fun register(
        nis: String, nama: String, kelas: String,
        jurusan: String, password: String, kode: String
    ): Result<AuthResult> = safe {
        val res = call(
            "POST", "/api/register.php",
            JSONObject()
                .put("nis", nis)
                .put("nama", nama)
                .put("kelas", kelas)
                .put("jurusan", jurusan)
                .put("password", password)
                .put("kode", kode)
        )
        parseAuth(res)
    }

    suspend fun me(token: String): Result<Siswa> = safe {
        parseSiswa(call("GET", "/api/me.php", token = token).getJSONObject("siswa"))
    }

    suspend fun updateProfile(
        token: String,
        nama: String,
        kelas: String,
        jurusan: String,
        passwordLama: String,
        passwordBaru: String
    ): Result<Siswa> = safe {
        val body = JSONObject()
            .put("nama", nama)
            .put("kelas", kelas)
            .put("jurusan", jurusan)
        if (passwordBaru.isNotEmpty()) {
            body.put("password_lama", passwordLama)
            body.put("password_baru", passwordBaru)
        }
        parseSiswa(call("POST", "/api/profil_update.php", body, token).getJSONObject("siswa"))
    }

    suspend fun logout(token: String): Result<Unit> = safe {
        call("POST", "/api/logout.php", JSONObject(), token)
        Unit
    }

    suspend fun fetchConfig(token: String): Result<ExamConfig?> = safe {
        val res = call("GET", "/api/config.php", token = token)
        val receivedAt = SystemClock.elapsedRealtime()
        val u = res.optJSONObject("ujian")
        if (u == null) null else parseExam(u, receivedAt)
    }

    // Mulai ujian baru, atau lanjutkan yang sedang berjalan
    suspend fun startExam(token: String, ujianId: Int): Result<StartResult> = safe {
        val res = call(
            "POST", "/api/ujian_mulai.php",
            JSONObject().put("ujian_id", ujianId), token
        )
        val url = res.getString("url")
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Link ujian dari server tidak valid."
        }
        StartResult(
            url = url,
            sisaMs = res.getLong("sisa_detik") * 1000L,
            pelanggaran = res.optInt("pelanggaran", 0),
            maksPelanggaran = res.optInt("maks_pelanggaran", 0)
        )
    }

    suspend fun finishExam(token: String, ujianId: Int): Result<Unit> = safe {
        call("POST", "/api/ujian_selesai.php", JSONObject().put("ujian_id", ujianId), token)
        Unit
    }

    suspend fun reportViolation(
        token: String,
        ujianId: Int,
        jenis: String
    ): Result<ViolationResult> = safe {
        val res = call(
            "POST", "/api/ujian_pelanggaran.php",
            JSONObject().put("ujian_id", ujianId).put("jenis", jenis), token
        )
        ViolationResult(
            pelanggaran = res.optInt("pelanggaran", 0),
            maks = res.optInt("maks", 0),
            dihentikan = res.optBoolean("dihentikan", false)
        )
    }

    // ------------------------------------------------------------ internal

    private suspend fun <T> safe(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(
                Exception("Tidak dapat terhubung ke server. Periksa Wi-Fi dan alamat server.")
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Terjadi kesalahan."))
        }
    }

    private fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null
    ): JSONObject {
        val conn = URL(ApiConfig.BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-API-Key", ApiConfig.API_KEY)
            if (token != null) conn.setRequestProperty("X-Auth-Token", token)

            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject()
            }

            if (code !in 200..299) {
                throw ApiException(json.optString("error", "Server membalas kode $code."), code)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun parseSiswa(j: JSONObject) = Siswa(
        nama = j.getString("nama"),
        nis = j.getString("nis"),
        kelas = j.getString("kelas"),
        jurusan = j.getString("jurusan")
    )

    private fun parseAuth(j: JSONObject) = AuthResult(
        token = j.getString("token"),
        siswa = parseSiswa(j.getJSONObject("siswa"))
    )

    private fun parseExam(u: JSONObject, receivedAt: Long): ExamConfig {
        val label = try {
            val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .parse(u.getString("jam_mulai"))
            SimpleDateFormat("EEE, d MMM yyyy HH:mm", Locale("id", "ID"))
                .apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }
                .format(d!!) + " WIB"
        } catch (e: Exception) {
            u.optString("jam_mulai")
        }

        return ExamConfig(
            id = u.getInt("id"),
            nama = u.getString("nama"),
            durasiMenit = u.getInt("durasi_menit"),
            jamMulaiLabel = label,
            mulaiAt = receivedAt + u.getLong("mulai_dalam_detik") * 1000L,
            status = u.optString("status_siswa", "belum"),
            alasan = u.optString("alasan_selesai", ""),
            berakhirAt = receivedAt + u.optLong("sisa_detik", 0L) * 1000L
        )
    }
}
