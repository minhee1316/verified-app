package com.verified.incar

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 실제 기록의 기존 경로는 유지하고, 예시 사진·내보낸 파일만 별도로 보관한다. */
internal object RecordFiles {
    /** 완성된 임시 파일만 교체한다. 쓰기 실패 시 이전 사진·문서는 그대로 남긴다. */
    fun writeAtomically(target: File, write: (OutputStream) -> Unit): File {
        val parent = target.parentFile ?: throw IOException("저장 경로가 없어요")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("저장 폴더를 만들지 못했어요")
        val temporary = File.createTempFile("record_", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output -> write(output); output.fd.sync() }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return target
        } finally { temporary.delete() }
    }

    fun photoRoot(context: Context, demo: Boolean = false) =
        File(context.filesDir, if (demo) "handover_photos_trial" else "handover_photos")

    fun csvName(name: String, demo: Boolean) = if (demo) "시연_$name" else name

    fun csvPrefixes(demo: Boolean) = listOf("운행기록부_", "맡김이력서_").map { csvName(it, demo) }

    /** 맡김 한 건 PDF(맡김기록_…pdf). '내 기록 지우기'로 CSV와 함께 지운다. */
    fun pdfPrefix(demo: Boolean) = csvName("맡김기록_", demo)

    fun ownsCsv(name: String, demo: Boolean) = (name.endsWith(".csv") && csvPrefixes(demo).any { name.startsWith(it) }) ||
        (name.endsWith(".pdf") && name.startsWith(pdfPrefix(demo)))

    /** 주차 위치 사진(하나만 둔다). */
    fun parkingPhoto(context: Context, demo: Boolean = false) = File(photoRoot(context, demo), "parking.jpg")
}
