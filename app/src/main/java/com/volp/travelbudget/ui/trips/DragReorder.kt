package com.volp.travelbudget.ui.trips

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 목록을 길게 눌러 끌어 옮기는 상태.
 *
 * 화면에 보이는 칸들의 위치를 직접 보고 어디에 놓을지 정한다. 칸 높이를 하나로 가정하면
 * 제목이 길어 두 줄이 된 여행에서 어긋나기 때문이다.
 *
 * @param key 이 자리의 항목을 가려내는 열쇠. 목록에 머리글이나 꼬리말이 섞여 있어도
 *   여행 카드끼리만 자리를 바꾸게 한다.
 */
class DragReorderState(
    private val listState: LazyListState,
    private val canDrag: (LazyListItemInfo) -> Boolean,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** 지금 손가락에 붙어 있는 칸. 없으면 끌고 있지 않다. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** 그 칸을 원래 자리에서 얼마나 옮겼는지. 카드를 그릴 때 이만큼 밀어 준다. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var startOffset = 0
    private var startSize = 0

    /** 화면 끝으로 끌고 갔을 때 목록을 저절로 밀어 올리기 위한 신호. */
    private val autoScroll = Channel<Float>(Channel.CONFLATED)
    val autoScrollRequests = autoScroll.receiveAsFlow()

    fun onDragStart(position: Offset) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            position.y.toInt() in info.offset..(info.offset + info.size)
        } ?: return
        if (!canDrag(item)) return

        draggingIndex = item.index
        startOffset = item.offset
        startSize = item.size
        offset = 0f
    }

    fun onDrag(deltaY: Float) {
        val current = draggingIndex ?: return
        offset += deltaY

        val top = startOffset + offset
        val middle = top + startSize / 2f

        // 끌고 있는 칸의 한가운데가 다른 칸의 몸통에 들어가면 그 자리와 바꾼다.
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.index != current &&
                canDrag(info) &&
                middle.toInt() in info.offset..(info.offset + info.size)
        }

        if (target != null) {
            onMove(current, target.index)
            // 자리가 바뀌었으니 기준점도 그 자리로 옮긴다. 그래야 손가락과 카드가 어긋나지 않는다.
            offset -= (target.offset - startOffset)
            startOffset = target.offset
            startSize = target.size
            draggingIndex = target.index
            return
        }

        requestAutoScrollIfNeeded(top)
    }

    fun onDragEnd() {
        if (draggingIndex != null) onDrop()
        draggingIndex = null
        offset = 0f
    }

    /** 이 칸을 지금 끌고 있는지. 그릴 때 위로 띄워 주려고 본다. */
    fun isDragging(index: Int): Boolean = draggingIndex == index

    private fun requestAutoScrollIfNeeded(top: Float) {
        val viewport = listState.layoutInfo.viewportEndOffset
        val bottom = top + startSize

        val amount = when {
            top < EDGE -> -(EDGE - top).coerceAtMost(MAX_SCROLL_STEP)
            bottom > viewport - EDGE -> (bottom - (viewport - EDGE)).coerceAtMost(MAX_SCROLL_STEP)
            else -> return
        }
        autoScroll.trySend(amount)
    }

    /** 실제로 목록을 미는 일. 화면 쪽에서 코루틴으로 받아 처리한다. */
    suspend fun scrollBy(amount: Float) {
        listState.scrollBy(amount)
    }

    private companion object {
        /** 이 안쪽까지 끌고 가면 목록이 저절로 밀린다. */
        const val EDGE = 120f
        const val MAX_SCROLL_STEP = 24f
    }
}

@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    canDrag: (LazyListItemInfo) -> Boolean,
    onMove: (Int, Int) -> Unit,
    onDrop: () -> Unit,
): DragReorderState {
    val latestMove by rememberUpdatedState(onMove)
    val latestDrop by rememberUpdatedState(onDrop)
    val latestCanDrag by rememberUpdatedState(canDrag)

    return remember(listState) {
        DragReorderState(
            listState = listState,
            canDrag = { latestCanDrag(it) },
            onMove = { from, to -> latestMove(from, to) },
            onDrop = { latestDrop() },
        )
    }
}

/**
 * 길게 눌러 끌기를 목록에 붙인다.
 *
 * @param enabled 끌어 옮길 수 있는 차례일 때만 켠다. 날짜순으로 보는 중에 끌면 손을 떼는 순간
 *   제자리로 돌아가 사용자가 고장 났다고 여긴다.
 */
fun Modifier.dragReorder(state: DragReorderState, enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(state, enabled) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position -> state.onDragStart(position) },
                onDrag = { change, amount ->
                    change.consume()
                    state.onDrag(amount.y)
                },
                onDragEnd = { state.onDragEnd() },
                onDragCancel = { state.onDragEnd() },
            )
        }
    }
