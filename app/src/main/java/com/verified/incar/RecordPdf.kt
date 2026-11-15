package com.verified.incar

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.verified.incar.core.HandoverRecord
import com.verified.incar.core.MyHyundai
import com.verified.incar.core.VerdictEngine
import com.verified.incar.ui.HandoverPhotos
import java.io.OutputStream

/**
 * 맡김 한 건을 PDF 한 부로: 기본 정보 · 숫자 · 메모 · 시간순 근거 · 바뀐 것 · 같은 각도 사진.
 * 앱이 차에서 읽은 값과 차주가 적은 메모를 나눠 쓰고, 예시 차량 기록이면 맨 위에 밝힌다.
 */
internal object RecordPdf {
    /** 메모의 줄바꿈과 보조 평면 문자(이모지 등)를 잘라 버리지 않고 줄을 나눈다. */
    internal fun wrappedLines(text: String, paint: Paint, width: Float): List<String> =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').flatMap { paragraph ->
            val result = mutableListOf<String>()
            var line = ""
            paragraph.codePoints().toArray().forEach { point ->
                val next = String(Character.toChars(point))
                if (line.isNotEmpty() && paint.measureText(line + next) > width) { result += line; line = "" }
                line += next
            }
            result + line
        }

    fun kindLabel(r: HandoverRecord) = if (r.kind == "맡김") "정비 맡김" else r.kind

    fun fileName(r: HandoverRecord, demo: Boolean) =
        RecordFiles.csvName("맡김기록_${kindLabel(r).replace(" ", "")}_${VerdictEngine.date(r.startMs)}_${VerdictEngine.hhmm(r.startMs).replace(":", "")}.pdf", demo)

    /** 문서에 쓸 줄. "# "로 시작하면 소제목. 사진은 따로 그린다. */
    fun lines(r: HandoverRecord, demo: Boolean): List<String> = buildList {
        if (demo) add("※ 예시 차량으로 만든 기록이에요(실제 차 신호가 아님)")
        add("# 기본 정보")
        add("종류: ${kindLabel(r)}")
        add("맡긴 시각: ${VerdictEngine.date(r.startMs)} ${VerdictEngine.hhmm(r.startMs)}")
        add("돌려받은 시각: ${VerdictEngine.date(r.endMs)} ${VerdictEngine.hhmm(r.endMs)}")
        r.dueMs?.let { due ->
            val late = -Math.floorDiv(due - r.endMs, 60_000L)
            add("반납 예정: ${VerdictEngine.hhmm(due)} · " + if (late > 0) "예정보다 ${late}분 늦게 돌려받음" else "예정 안에 돌려받음")
        }
        add("결과: " + if (r.ok) "문제 없이 돌려받음" else "확인 필요")
        add("# 차가 읽은 값")
        add("남이 운전한 거리: " + (r.km?.let { "%.1fkm".format(java.util.Locale.ROOT, it) } ?: "확인 불가"))
        if (r.recordsDrivingStats) {
            add("최고 속도: " + if (r.speedKnown) "%.0fkm/h".format(java.util.Locale.ROOT, r.maxSpeed) else "확인 불가")
            add("급감속: " + if (r.speedKnown) "${r.brakes}회" else "확인 불가")
        } else add("대리운전은 기사 안내 범위에 따라 속도·급감속을 기록하지 않아요")
        if (r.memo.isNotBlank()) { add("# 차주 메모"); add(r.memo) }
        add("# 시간순 기록")
        if (r.proof.isEmpty()) {
            add("${VerdictEngine.hhmm(r.startMs)} 맡김 시작")
            add("${VerdictEngine.hhmm(r.endMs)} 돌려받음")
        } else r.proof.forEach { add("${VerdictEngine.hhmm(it.atMs)} ${it.what} · ${it.src}") }
        add("# 바뀐 것")
        if (r.changes.isEmpty()) add("없음") else r.changes.forEach { add("· $it") }
        add("# 안내")
        add(MyHyundai.RESUME_NOTE)
    }

    fun render(context: Context, r: HandoverRecord, demo: Boolean, out: OutputStream) {
        val doc = PdfDocument()
        val w = 595f
        val left = 48f
        val width = w - left * 2
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; isFakeBoldText = true; color = 0xFF002C5F.toInt() }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; isFakeBoldText = true; color = 0xFF002C5F.toInt() }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF111827.toInt() }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF5B6676.toInt() }
        var no = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let(doc::finishPage)
            page = null
            no++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, no).create())
            y = 56f
            page!!.canvas.drawText("현대 카가드 · 맡김 기록 · ${no}쪽", left, 820f, small)
        }
        fun write(s: String, p: Paint, before: Float = 0f) {
            y += before
            wrappedLines(s, p, width).forEach { line ->
                if (y > 790f) newPage()
                page!!.canvas.drawText(line, left, y, p)
                y += p.textSize * 1.55f
            }
        }
        try {
        newPage()
        write("${kindLabel(r)} 기록 · ${VerdictEngine.date(r.startMs)}", title)
        lines(r, demo).forEach { if (it.startsWith("# ")) write(it.removePrefix("# "), head, 8f) else write(it, body) }
        // 같은 각도 사진: 맡길 때 | 돌려받을 때
        val dir = HandoverPhotos.recordDir(context, r.startMs, demo)
        val pairs = HandoverPhotos.ANGLES.map { (a, label) -> Triple(label, HandoverPhotos.file(dir, HandoverPhotos.BEFORE, a), HandoverPhotos.file(dir, HandoverPhotos.AFTER, a)) }
            .filter { it.second.exists() || it.third.exists() }
        if (pairs.isNotEmpty()) {
            write("같은 각도 사진 (왼쪽: 맡길 때, 오른쪽: 돌려받을 때)", head, 8f)
            val boxW = (width - 60f) / 2
            val boxH = boxW * 9f / 16f
            pairs.forEach { (label, before, after) ->
                if (y + boxH + 20f > 790f) newPage()
                page!!.canvas.drawText(label, left, y + 12f, body)
                listOf(before, after).forEachIndexed { i, f ->
                    val x = left + 60f + i * (boxW + 0f)
                    val rect = RectF(x, y, x + boxW - 6f, y + boxH)
                    HandoverPhotos.decode(f, 900)?.let { bmp ->
                        try {
                        // 비율을 지키며 칸 안에 맞춘다
                        val s = minOf(rect.width() / bmp.width, rect.height() / bmp.height)
                        val dw = bmp.width * s
                        val dh = bmp.height * s
                        val dst = RectF(rect.left + (rect.width() - dw) / 2, rect.top + (rect.height() - dh) / 2, rect.left + (rect.width() + dw) / 2, rect.top + (rect.height() + dh) / 2)
                        page!!.canvas.drawBitmap(bmp, null, dst, null)
                        page!!.canvas.drawText("${VerdictEngine.date(f.lastModified())} ${VerdictEngine.hhmm(f.lastModified())}", rect.left, rect.bottom + 10f, small)
                        } finally { bmp.recycle() }
                    } ?: page!!.canvas.drawText("사진 없음", rect.left, rect.top + 14f, small)
                }
                y += boxH + 22f
            }
        }
        page?.let(doc::finishPage)
        page = null
        doc.writeTo(out)
        } finally {
            // 저장 공간 부족·사진 읽기 실패 뒤 재시도해도 네이티브 PDF 자원을 쌓지 않는다.
            page?.let { runCatching { doc.finishPage(it) } }
            doc.close()
        }
    }
}
