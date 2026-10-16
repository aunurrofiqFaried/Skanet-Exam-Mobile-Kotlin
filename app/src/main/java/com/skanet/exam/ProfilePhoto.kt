package com.skanet.exam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

object ProfilePhoto {

    private const val SIZE = 512

    private fun file(context: Context, nis: String): File {
        val safe = nis.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.filesDir, "profile_$safe.jpg")
    }

    fun load(context: Context, nis: String): Bitmap? {
        val f = file(context, nis)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    fun delete(context: Context, nis: String) {
        file(context, nis).delete()
    }

    // Mengembalikan true jika berhasil. Jalankan di thread latar belakang.
    fun save(context: Context, nis: String, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver

            // 1) Baca ukuran asli
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            // 2) Decode dengan pengecilan awal supaya hemat memori
            var sample = 1
            while (bounds.outWidth / sample > SIZE * 2 && bounds.outHeight / sample > SIZE * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            // 3) Putar sesuai orientasi EXIF (foto dari kamera)
            val rotation = runCatching {
                resolver.openInputStream(uri)?.use { stream ->
                    when (
                        ExifInterface(stream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    ) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            }.getOrDefault(0f)

            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height, matrix, true
            )

            // 4) Potong persegi dari tengah, lalu kecilkan
            val side = minOf(rotated.width, rotated.height)
            val x = (rotated.width - side) / 2
            val y = (rotated.height - side) / 2
            val square = Bitmap.createBitmap(rotated, x, y, side, side)
            val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)

            // 5) Simpan di penyimpanan internal aplikasi
            file(context, nis).outputStream().use {
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}