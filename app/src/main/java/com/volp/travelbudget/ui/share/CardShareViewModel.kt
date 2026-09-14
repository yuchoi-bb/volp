package com.volp.travelbudget.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.capture.CardCaptureHandler
import com.volp.travelbudget.data.local.CaptureSource
import com.volp.travelbudget.domain.cardsms.CardMessageParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** 공유한 카드 문자를 넣어 본 결과. */
enum class CardShareState {
    Working,

    /** 미확인함이나 여행 지출로 들어갔다. */
    Saved,

    /** 같은 결제가 이미 있어 넣지 않았다. */
    Duplicate,

    /** 카드 결제 문자로 읽히지 않았다. */
    Unreadable,
}

/**
 * 공유로 들어온 카드 결제 문자를 받는다.
 *
 * 사람이 직접 넘긴 것이므로 자동 수집을 꺼 두었더라도 받는다. 공유한 사람의 뜻이 분명하다.
 */
class CardShareViewModel(
    private val captureHandler: CardCaptureHandler,
    private val text: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CardShareState.Working)
    val state: StateFlow<CardShareState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (CardMessageParser.parse(text, LocalDateTime.now(), null) == null) {
                _state.value = CardShareState.Unreadable
                return@launch
            }

            val saved = runCatching {
                captureHandler.handle(
                    body = text,
                    sender = null,
                    source = CaptureSource.SHARED,
                    manual = true,
                )
            }.getOrDefault(false)

            _state.value = if (saved) CardShareState.Saved else CardShareState.Duplicate
        }
    }
}
