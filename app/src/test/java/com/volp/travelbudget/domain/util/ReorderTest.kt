package com.volp.travelbudget.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {

    private val list = listOf("가", "나", "다", "라")

    @Test
    fun `아래로 옮기면 사이에 있던 것들이 올라온다`() {
        assertEquals(listOf("나", "다", "가", "라"), list.moved(0, 2))
    }

    @Test
    fun `위로 옮기면 사이에 있던 것들이 내려간다`() {
        assertEquals(listOf("가", "라", "나", "다"), list.moved(3, 1))
    }

    @Test
    fun `자리를 맞바꾸는 것이 아니다`() {
        // 맞바꾸기였다면 [라, 나, 다, 가]가 됐을 것이다.
        assertEquals(listOf("나", "다", "라", "가"), list.moved(0, 3))
    }

    @Test
    fun `제자리면 그대로 둔다`() {
        assertEquals(list, list.moved(2, 2))
    }

    @Test
    fun `범위를 벗어난 자리는 무시한다`() {
        assertEquals(list, list.moved(-1, 2))
        assertEquals(list, list.moved(0, 9))
        assertEquals(emptyList<String>(), emptyList<String>().moved(0, 1))
    }
}
