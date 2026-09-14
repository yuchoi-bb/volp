package com.volp.travelbudget.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCodeTest {

    @Test
    fun `새 코드는 네 글자씩 끊어진 열여섯 글자다`() {
        val code = SyncCode.newCode()

        assertEquals(19, code.length)
        assertEquals(4, code.split("-").size)
        assertTrue(SyncCode.isValid(code))
    }

    @Test
    fun `코드는 매번 다르다`() {
        assertNotEquals(SyncCode.newCode(), SyncCode.newCode())
    }

    @Test
    fun `옮겨 적은 코드는 대소문자와 줄표를 가리지 않는다`() {
        val code = SyncCode.newCode()
        val typed = code.lowercase().replace("-", " ")

        assertEquals(code, SyncCode.normalize(typed))
    }

    @Test
    fun `헷갈리는 글자는 비슷한 글자로 돌려 준다`() {
        assertEquals("QJQJ", SyncCode.normalize("O1 0i"))
    }

    @Test
    fun `길이가 모자란 코드는 쓰지 않는다`() {
        assertTrue(!SyncCode.isValid("ABCD-EFGH"))
        assertTrue(!SyncCode.isValid(""))
    }
}
