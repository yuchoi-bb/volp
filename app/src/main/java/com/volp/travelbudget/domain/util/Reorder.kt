package com.volp.travelbudget.domain.util

/**
 * 목록에서 한 칸을 뽑아 다른 자리에 끼워 넣는다.
 *
 * 끌어서 순서를 바꿀 때 쓴다. 자리를 맞바꾸는 것(swap)과 다르다. 손가락으로 세 칸 아래로
 * 옮기면 사이에 있던 것들이 한 칸씩 올라와야 하지, 맨 아래 것과 자리를 바꾸는 것이 아니다.
 *
 * 범위를 벗어난 자리는 그대로 둔다. 화면에서 온 값이라 늘 옳다고 믿을 수 없다.
 */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to) return this
    if (from !in indices || to !in indices) return this

    return toMutableList().apply { add(to, removeAt(from)) }
}
