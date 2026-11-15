package com.verified.incar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import com.verified.incar.core.HandKind
import com.verified.incar.core.VerdictEngine
import com.verified.incar.ui.HandoverPhotos
import com.verified.incar.ui.handoverPhotoCount
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordFileIsolationTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun demoPhotos_doNotAttachToRealRecordsAndResetOnlyDeletesDemoPhotos() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val realPending = HandoverPhotos.pendingDir(app, HandKind.SERVICE)
        val demoPending = HandoverPhotos.pendingDir(app, HandKind.SERVICE, demo = true)
        HandoverPhotos.save(realPending, HandoverPhotos.BEFORE, "FRONT", bitmap)
        HandoverPhotos.save(demoPending, HandoverPhotos.BEFORE, "REAR", bitmap)
        val start = 12_345L
        assertEquals(1, HandoverPhotos.attach(app, HandKind.SERVICE, start, demo = true))
        assertEquals(1, HandoverPhotos.count(realPending, HandoverPhotos.BEFORE))
        assertEquals(0, handoverPhotoCount(app, start))
        assertEquals(1, handoverPhotoCount(app, start, demo = true))
        assertEquals(1, HandoverPhotos.attach(app, HandKind.SERVICE, start))
        assertTrue(HandoverPhotos.file(HandoverPhotos.recordDir(app, start), HandoverPhotos.BEFORE, "FRONT").exists())
        assertFalse(HandoverPhotos.file(HandoverPhotos.recordDir(app, start), HandoverPhotos.BEFORE, "REAR").exists())
        Store(app, ns = Store.TRIAL_NS).wipeAll()
        assertEquals(1, handoverPhotoCount(app, start))
        assertEquals(0, handoverPhotoCount(app, start, demo = true))
        bitmap.recycle()
    }

    @Test fun demoCsvExportsAreNamedAndClearedSeparatelyFromRealExports() {
        val provider = RecordingMediaProvider().apply { attachInfo(app, ProviderInfo().apply { authority = "media" }) }
        ShadowContentResolver.registerProviderInternal("media", provider)
        val flags = app.applicationInfo.flags
        app.applicationInfo.flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val vm = VerifiedRuntime(app, simTicker = false)
        try {
            assertTrue(vm.ui.value.trial)
            val date = VerdictEngine.date(vm.ui.value.nowMs)
            val names = listOf("운행기록부_$date.csv", "맡김이력서_$date.csv")
            val roots = listOfNotNull(app.getExternalFilesDir(null), app.filesDir)
            val realFiles = roots.flatMap { dir -> names.map { File(dir, it).apply { writeText("실제 기록") } } }
            vm.exportTrips()
            vm.exportHistory()
            val exportRoot = app.getExternalFilesDir(null) ?: app.filesDir
            names.forEach { assertTrue(File(exportRoot, "시연_$it").exists()) }
            assertTrue(vm.ui.value.toast.contains("시연_맡김이력서_"))
            realFiles.forEach { assertEquals("실제 기록", it.readText()) }
            // 내부/외부 앱 저장소와 공용 다운로드 삭제 조건 모두 현재 모드에만 적용한다.
            File(app.filesDir, "시연_${names.first()}").writeText("예시 기록")
            Store(app, ns = Store.TRIAL_NS).clearRecords()
            assertEquals(listOf("시연_운행기록부_%.csv", "시연_맡김이력서_%.csv", "시연_맡김기록_%.pdf"), provider.lastDelete)
            roots.forEach { dir -> names.forEach { assertFalse(File(dir, "시연_$it").exists()) } }
            realFiles.forEach { assertTrue(it.exists()) }
            val demoFile = File(exportRoot, "시연_${names.first()}").apply { writeText("예시 기록") }
            Store(app).clearRecords()
            assertEquals(listOf("운행기록부_%.csv", "맡김이력서_%.csv", "맡김기록_%.pdf"), provider.lastDelete)
            realFiles.forEach { assertFalse(it.exists()) }
            assertTrue(demoFile.exists())
        } finally {
            vm.close()
            app.applicationInfo.flags = flags
        }
    }

    /** 다운로드 폴더 파일은 다 쓴 뒤에만 공개하고, 공개하지 못하면 미완성 항목을 지운 뒤 앱 폴더에 남긴다. */
    @Test fun downloadExports_stayPendingUntilFullyWritten() {
        val provider = PendingMediaProvider().apply { attachInfo(app, ProviderInfo().apply { authority = "media" }) }
        ShadowContentResolver.registerProviderInternal("media", provider)
        val flags = app.applicationInfo.flags
        app.applicationInfo.flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val vm = VerifiedRuntime(app, simTicker = false)
        try {
            vm.exportHistory()
            assertEquals(1, provider.inserted?.getAsInteger(android.provider.MediaStore.MediaColumns.IS_PENDING))
            assertEquals(0, provider.published?.getAsInteger(android.provider.MediaStore.MediaColumns.IS_PENDING))
            assertFalse(provider.deleted)
            assertTrue(vm.ui.value.toast, vm.ui.value.toast.contains("다운로드 폴더"))
            provider.publishResult = 0
            vm.exportTrips()
            assertTrue(provider.deleted)
            assertTrue(vm.ui.value.toast, vm.ui.value.toast.contains("차 안에 저장"))
        } finally {
            vm.close()
            app.applicationInfo.flags = flags
        }
    }

    class PendingMediaProvider : ContentProvider() {
        var inserted: ContentValues? = null
        var published: ContentValues? = null
        var publishResult = 1
        var deleted = false
        override fun onCreate() = true
        override fun getType(uri: Uri) = "text/csv"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            inserted = ContentValues(values)
            return Uri.withAppendedPath(uri, "1")
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            if (selection == null) deleted = true
            return 1
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            published = ContentValues(values)
            return publishResult
        }
        override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor =
            android.os.ParcelFileDescriptor.open(File.createTempFile("export", ".csv"),
                android.os.ParcelFileDescriptor.MODE_READ_WRITE or android.os.ParcelFileDescriptor.MODE_CREATE or android.os.ParcelFileDescriptor.MODE_TRUNCATE)
    }

    class RecordingMediaProvider : ContentProvider() {
        var lastDelete = emptyList<String>()
        override fun onCreate() = true
        override fun getType(uri: Uri) = "text/csv"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null // 앱 전용 CSV 저장 경로도 검증한다.
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            lastDelete = selectionArgs?.toList().orEmpty()
            return 0
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
