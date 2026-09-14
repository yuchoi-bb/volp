package com.volp.travelbudget.domain.sync

import java.security.SecureRandom

/**
 * 기기끼리 같은 기록을 보게 묶어 주는 코드.
 *
 * 이 코드를 아는 기기가 곧 같은 사용자이므로 **비밀번호처럼 다뤄야 한다**. 그래서 짧고 외우기
 * 좋은 코드 대신 무작위로 길게 만든다. 헷갈리는 글자(0, O, 1, I)는 빼서 손으로 옮겨 적을 수 있게 한다.
 */
object SyncCode {

    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LENGTH = 16
    private const val GROUP = 4

    private val random = SecureRandom()

    /** `A3F9-K2MP-7XQR-5TWB` 꼴의 새 코드. */
    fun newCode(): String {
        val chars = CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return chars.concatToString().chunked(GROUP).joinToString("-")
    }

    /**
     * 사람이 옮겨 적은 코드를 저장할 꼴로 다듬는다.
     *
     * 대소문자와 줄표는 신경 쓰지 않아도 되게 한다. 알파벳에 없는 글자는 사람이 잘못 읽은
     * 것이므로 모양이 비슷한 글자로 돌려 준다.
     */
    fun normalize(raw: String): String {
        val cleaned = raw.uppercase()
            .replace('O', 'Q')
            .replace('0', 'Q')
            .replace('I', 'J')
            .replace('1', 'J')
            .filter { it in ALPHABET }
        return cleaned.chunked(GROUP).joinToString("-")
    }

    /** 쓸 수 있는 코드인지. 길이가 맞아야 남의 기록에 잘못 들어가는 일이 없다. */
    fun isValid(code: String): Boolean =
        code.filter { it in ALPHABET }.length == LENGTH
}
