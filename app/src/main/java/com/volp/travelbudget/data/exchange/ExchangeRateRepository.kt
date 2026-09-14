package com.volp.travelbudget.data.exchange

import com.volp.travelbudget.data.local.ExchangeRateDao
import com.volp.travelbudget.data.local.ExchangeRateEntity
import com.volp.travelbudget.data.update.UpdateChecker
import com.volp.travelbudget.domain.budget.CurrencyRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 환율을 받아 와 기기에 보관한다.
 *
 * 해외 카드 문자에는 원화 환산액이 오지 않기 때문에(`JPY 1,200` 처럼 현지 통화만 온다)
 * 앱이 들고 있는 환율이 곧 금액 정확도다. 그래서 하루에 한 번 갱신하고, 지출 한 건마다
 * 그때 쓴 환율을 함께 저장한다.
 *
 * 받아 오지 못하면 [CurrencyRates]의 기본값을 쓴다. 환율 때문에 기록 자체가 막히면 안 된다.
 */
class ExchangeRateRepository(
    private val dao: ExchangeRateDao,
    private val client: OkHttpClient = UpdateChecker.defaultClient(),
) {

    /** 지금 쓸 환율(1 [code] 당 원). 받아 둔 값이 없으면 기본값. */
    suspend fun rateFor(code: String): Double {
        if (CurrencyRates.isKrw(code)) return 1.0
        return dao.find(code)?.krwPerUnit ?: CurrencyRates.defaultRate(code)
    }

    /** 마지막 갱신이 오래됐으면 새로 받아 온다. @return 갱신했으면 true */
    suspend fun refreshIfStale(maxAgeMillis: Long = DEFAULT_MAX_AGE): Boolean {
        val last = dao.lastFetchedAt() ?: 0L
        if (System.currentTimeMillis() - last < maxAgeMillis) return false
        return refresh()
    }

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val rates = runCatching { fetchRates() }.getOrNull() ?: return@withContext false
        if (rates.isEmpty()) return@withContext false

        val now = System.currentTimeMillis()
        dao.upsertAll(rates.map { (code, krwPerUnit) -> ExchangeRateEntity(code, krwPerUnit, now) })
        true
    }

    /**
     * 원화를 기준으로 환율을 받아 온다.
     *
     * 응답은 `1원이 몇 단위인지`로 오므로 뒤집어 `1단위가 몇 원인지`로 바꾼다.
     */
    private fun fetchRates(): Map<String, Double> {
        val request = Request.Builder().url(ENDPOINT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyMap()

            val json = JSONObject(body)
            if (json.optString("result") != "success") return emptyMap()
            val rates = json.optJSONObject("rates") ?: return emptyMap()

            return CurrencyRates.currencies
                .mapNotNull { currency ->
                    if (CurrencyRates.isKrw(currency.code)) return@mapNotNull null
                    val perKrw = rates.optDouble(currency.code, 0.0)
                    if (perKrw <= 0.0) null else currency.code to 1.0 / perKrw
                }
                .toMap()
        }
    }

    private companion object {
        /** 키가 필요 없는 공개 환율 API. */
        const val ENDPOINT = "https://open.er-api.com/v6/latest/KRW"
        const val DEFAULT_MAX_AGE = 12 * 60 * 60 * 1000L
    }
}
