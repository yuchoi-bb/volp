package com.volp.travelbudget.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.transfer.RecordTransfer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 파일로 주고받기가 지금 어디까지 갔는지. */
sealed interface TransferStatus {
    data object Idle : TransferStatus
    data class Working(val message: String) : TransferStatus

    /** 보내기 화면을 띄워야 하는 상태. 어디로 보낼지는 안드로이드가 묻는다. */
    data class ReadyToSend(val uri: Uri, val message: String) : TransferStatus
    data class Done(val message: String) : TransferStatus
    data class Failed(val message: String) : TransferStatus
}

/**
 * 계정 없이 기록을 주고받는다.
 *
 * 받은 파일은 덮어쓰지 않고 합친다. 두 기기에서 따로 넣은 것이 둘 다 남아야 하기 때문이다.
 */
class RecordTransferViewModel(
    private val transfer: RecordTransfer,
) : ViewModel() {

    private val _status = MutableStateFlow<TransferStatus>(TransferStatus.Idle)
    val status: StateFlow<TransferStatus> = _status.asStateFlow()

    fun suggestedFileName(): String = transfer.fileName()

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _status.value = TransferStatus.Working("파일로 쓰는 중")
            _status.value = runCatching { transfer.exportTo(uri) }
                .fold(
                    onSuccess = { TransferStatus.Done("여행 ${it.trips}건 · 기록 ${it.records}건을 파일에 담았다") },
                    onFailure = { TransferStatus.Failed(it.message ?: "파일을 쓰지 못했다") },
                )
        }
    }

    fun prepareSend() {
        viewModelScope.launch {
            _status.value = TransferStatus.Working("보낼 파일을 만드는 중")
            _status.value = runCatching { transfer.exportForSharing() }
                .fold(
                    onSuccess = { (uri, count) ->
                        TransferStatus.ReadyToSend(
                            uri = uri,
                            message = "여행 ${count.trips}건 · 기록 ${count.records}건을 보냈다",
                        )
                    },
                    onFailure = { TransferStatus.Failed(it.message ?: "파일을 만들지 못했다") },
                )
        }
    }

    /** 보내기 화면을 띄운 뒤. 실제로 어디로 갔는지는 앱이 알 수 없다. */
    fun sent(message: String) {
        _status.value = TransferStatus.Done(message)
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _status.value = TransferStatus.Working("파일을 읽어 합치는 중")
            _status.value = runCatching { transfer.importFrom(uri) }
                .fold(
                    onSuccess = { pulled ->
                        if (pulled == 0) {
                            TransferStatus.Done("새로 들어온 기록은 없다. 이미 같은 내용을 갖고 있다")
                        } else {
                            TransferStatus.Done("기록 ${pulled}건을 받아 합쳤다")
                        }
                    },
                    onFailure = { TransferStatus.Failed("이 파일에서 기록을 읽지 못했다") },
                )
        }
    }

    fun clear() {
        _status.value = TransferStatus.Idle
    }
}
