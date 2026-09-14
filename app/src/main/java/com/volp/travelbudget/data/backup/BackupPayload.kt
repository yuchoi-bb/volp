package com.volp.travelbudget.data.backup

import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 백업 파일 한 벌. 여행과 그 여행의 지출을 함께 담는다. */
data class TripBackup(
    val trip: Trip,
    val expenses: List<Expense>,
)

/**
 * 백업 파일을 만들고 읽는다.
 *
 * 사람이 드라이브에서 열어 볼 수 있도록 복원용 JSON과 열람용 CSV를 함께 올린다.
 */
object BackupPayload {

    const val FORMAT_VERSION = 1
    const val JSON_FILE_NAME = "volp-backup.json"
    const val CSV_FILE_NAME = "volp-expenses.csv"

    fun toJson(backups: List<TripBackup>, exportedAt: Long): String {
        val trips = JSONArray()
        backups.forEach { backup ->
            trips.put(tripToJson(backup))
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("trips", trips)
            .toString(2)
    }

    fun fromJson(text: String): List<TripBackup> {
        val root = JSONObject(text)
        val trips = root.optJSONArray("trips") ?: return emptyList()
        return (0 until trips.length()).map { index -> tripFromJson(trips.getJSONObject(index)) }
    }

    fun toCsv(backups: List<TripBackup>): String = buildString {
        appendLine("여행,날짜,항목,금액(원),현지금액,통화,메모")
        backups.forEach { backup ->
            backup.expenses.forEach { expense ->
                appendLine(
                    listOf(
                        backup.trip.title,
                        expense.date.toString(),
                        expense.category.label,
                        expense.amountKrw.toString(),
                        expense.originalAmount?.toString().orEmpty(),
                        expense.currencyCode,
                        expense.memo,
                    ).joinToString(",") { escapeCsv(it) },
                )
            }
        }
    }

    private fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun tripToJson(backup: TripBackup): JSONObject {
        val trip = backup.trip
        val expenses = JSONArray()
        backup.expenses.forEach { expense ->
            expenses.put(
                JSONObject()
                    .put("category", expense.category.name)
                    .put("amountKrw", expense.amountKrw)
                    .put("originalAmount", expense.originalAmount ?: JSONObject.NULL)
                    .put("currencyCode", expense.currencyCode)
                    .put("date", expense.date.toString())
                    .put("memo", expense.memo)
                    .put("createdAt", expense.createdAt),
            )
        }
        return JSONObject()
            .put("title", trip.title)
            .put("destinationKey", trip.destinationKey)
            .put("destinationName", trip.destinationName)
            .put("region", trip.region.name)
            .put("startDate", trip.startDate.toString())
            .put("endDate", trip.endDate.toString())
            .put("travelers", trip.travelers)
            .put("style", trip.style.name)
            .put("includeFlight", trip.includeFlight)
            .put("currencyCode", trip.currencyCode)
            .put("exchangeRate", trip.exchangeRate)
            .put("predictedBudget", budgetToJson(trip.predictedBudget))
            .put("plannedBudget", budgetToJson(trip.plannedBudget))
            .put("createdAt", trip.createdAt)
            .put("expenses", expenses)
    }

    private fun tripFromJson(json: JSONObject): TripBackup {
        val trip = Trip(
            title = json.optString("title"),
            destinationKey = json.optString("destinationKey"),
            destinationName = json.optString("destinationName"),
            region = Region.fromName(json.optString("region")),
            startDate = LocalDate.parse(json.getString("startDate")),
            endDate = LocalDate.parse(json.getString("endDate")),
            travelers = json.optInt("travelers", 1),
            style = TravelStyle.fromName(json.optString("style")),
            includeFlight = json.optBoolean("includeFlight", true),
            currencyCode = json.optString("currencyCode", "KRW"),
            exchangeRate = json.optDouble("exchangeRate", 1.0),
            predictedBudget = budgetFromJson(json.optJSONObject("predictedBudget")),
            plannedBudget = budgetFromJson(json.optJSONObject("plannedBudget")),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        )

        val expensesJson = json.optJSONArray("expenses") ?: JSONArray()
        val expenses = (0 until expensesJson.length()).map { index ->
            val item = expensesJson.getJSONObject(index)
            Expense(
                tripId = 0L,
                category = ExpenseCategory.fromName(item.optString("category")),
                amountKrw = item.optLong("amountKrw"),
                originalAmount = if (item.isNull("originalAmount")) null else item.optDouble("originalAmount"),
                currencyCode = item.optString("currencyCode", "KRW"),
                date = LocalDate.parse(item.getString("date")),
                memo = item.optString("memo"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        return TripBackup(trip, expenses)
    }

    private fun budgetToJson(budget: Map<ExpenseCategory, Long>): JSONObject {
        val json = JSONObject()
        budget.forEach { (category, amount) -> json.put(category.name, amount) }
        return json
    }

    private fun budgetFromJson(json: JSONObject?): Map<ExpenseCategory, Long> {
        if (json == null) return emptyMap()
        return ExpenseCategory.entries
            .mapNotNull { category ->
                if (json.has(category.name)) category to json.optLong(category.name) else null
            }
            .toMap()
    }
}
