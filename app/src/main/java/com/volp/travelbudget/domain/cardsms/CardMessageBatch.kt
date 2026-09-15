package com.volp.travelbudget.domain.cardsms

import java.time.LocalDateTime

/**
 * 한 덩어리로 들어온 글에서 카드 결제 문자를 여러 건 갈라 읽는다.
 *
 * 문자 앱에서 여러 건을 골라 공유하면 한 덩어리로 붙어서 온다. 앱마다 사이에 빈 줄을 넣기도 하고
 * 보낸 사람과 시각을 앞에 붙이기도 해서, 하나의 서식을 기대할 수 없다. 그래서 **결제 문자가
 * 시작되는 자리**를 찾아 자른 뒤 조각마다 읽어 본다. 읽히지 않는 조각은 버린다.
 */
object CardMessageBatch {

    /** 통신사가 붙이는 머리말. 문자 하나가 여기서 시작한다. */
    private val HEADERS = listOf("[Web발신]", "[국제발신]")

    /**
     * 카드사 이름으로 시작하는 줄.
     *
     * 뒤에 카드 번호나 `카드`가 바로 붙는 것만 인정한다. 그러지 않으면 `하나로마트` 같은 가맹점
     * 이름에서 문자가 잘린다.
     */
    private val ISSUER_HEAD = Regex("^\\[?(삼성|신한|하나)(카드\\]?|[0-9])")

    /**
     * 덩어리를 문자 하나씩으로 가른다.
     *
     * 자를 자리를 못 찾으면 통째로 하나로 본다. 한 건만 공유한 경우가 그렇다.
     */
    fun split(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val lines = trimmed.lines()
        val starters = lines.map { it.startsAtNewMessage() }

        // `[Web발신]` 다음 줄이 또 카드사 이름이면 같은 문자다. 앞줄도 첫머리면 자르지 않는다.
        val starts = lines.indices.filter { index ->
            starters[index] && !previousIsStarter(lines, starters, index)
        }
        if (starts.size <= 1) return listOf(trimmed)

        return starts.mapIndexed { order, from ->
            val to = starts.getOrNull(order + 1) ?: lines.size
            lines.subList(from, to).joinToString("\n").trim()
        }.filter { it.isNotEmpty() }
    }

    /**
     * 덩어리에서 읽히는 결제를 모두 읽어 낸다.
     *
     * 같은 결제가 두 번 들어 있으면(문자와 알림을 함께 공유하는 등) 한 건만 남긴다.
     */
    fun parseAll(text: String, receivedAt: LocalDateTime): List<CardTransaction> =
        split(text)
            .mapNotNull { CardMessageParser.parse(it, receivedAt) }
            .distinctBy { it.fingerprint() }

    /**
     * 같은 결제인지 가려내는 값.
     *
     * 카드사·카드·금액·시각·가맹점이 모두 같으면 같은 결제로 본다. 문자와 알림이 같은 결제를
     * 두 번 물고 오는 일이 흔하다.
     */
    private fun CardTransaction.fingerprint(): String =
        listOf(issuer.name, cardLabel, kind.name, amount.toString(), currencyCode, occurredAt.toString(), merchant)
            .joinToString("|")

    private fun String.startsAtNewMessage(): Boolean {
        val line = trim()
        if (line.isEmpty()) return false
        // 줄 한가운데 카드사 이름이 나오는 것은 가맹점 이름일 수 있다. 첫머리만 인정한다.
        return HEADERS.any { line.startsWith(it) } || ISSUER_HEAD.containsMatchIn(line)
    }

    private fun previousIsStarter(lines: List<String>, starters: List<Boolean>, index: Int): Boolean {
        for (before in index - 1 downTo 0) {
            if (lines[before].isBlank()) continue
            return starters[before]
        }
        return false
    }
}
