package com.volp.travelbudget.data.transfer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.volp.travelbudget.data.sync.SyncCodec
import com.volp.travelbudget.data.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** 파일 한 벌에 몇 건이 담겼는지. */
data class TransferCount(val trips: Int, val records: Int)

/**
 * 기록을 파일 하나로 내보내고, 다른 기기에서 그 파일을 받아 합친다.
 *
 * 계정도 인터넷도 없이 두 기기를 맞추는 길이다. 해외에서 데이터가 안 되거나 기기를 바꿀 때가
 * 그렇다. 파일을 어디로 보낼지는 안드로이드가 알아서 물어보므로 앱은 내용만 만든다.
 *
 * 받은 파일은 덮어쓰지 않고 [SyncEngine.mergeIn]으로 합친다. 덮어쓰면 이 기기에서 넣은 것이
 * 사라지기 때문이다. 같은 기록은 uid로 짝지어 더 나중에 고친 쪽이 남는다.
 */
class RecordTransfer(
    private val context: Context,
    private val engine: SyncEngine,
) {

    /** 사용자가 고른 자리에 기록 한 벌을 쓴다. */
    suspend fun exportTo(uri: Uri): TransferCount = withContext(Dispatchers.IO) {
        val snapshot = engine.snapshot()
        val text = SyncCodec.toJson(snapshot)

        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray())
        } ?: error("파일을 쓸 수 없다")

        TransferCount(snapshot.trips.size, snapshot.recordCount)
    }

    /**
     * 보내기 화면에 넘길 파일을 만든다.
     *
     * 캐시에 두고 잠깐 읽을 권한만 준다. 파일 자체를 기기 어딘가에 남기지 않는다.
     */
    suspend fun exportForSharing(): Pair<Uri, TransferCount> = withContext(Dispatchers.IO) {
        val snapshot = engine.snapshot()
        val folder = File(context.cacheDir, "share").apply { mkdirs() }
        // 보낼 때마다 새로 쓰므로 지난 파일은 남겨 두지 않는다.
        folder.listFiles()?.forEach { it.delete() }

        val file = File(folder, fileName())
        file.writeText(SyncCodec.toJson(snapshot))

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        uri to TransferCount(snapshot.trips.size, snapshot.recordCount)
    }

    /** 받은 파일을 이 기기의 기록과 합친다. */
    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: error("파일을 읽을 수 없다")

        val remote = SyncCodec.fromJson(text)
        engine.mergeIn(remote).pulled
    }

    fun fileName(): String = "volp-기록-${LocalDate.now()}.json"
}
