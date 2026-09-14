package com.volp.travelbudget.data.sync

import com.volp.travelbudget.data.local.DeletionEntity
import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.booking.BookingType
import com.volp.travelbudget.domain.cash.CashTopUp
import com.volp.travelbudget.domain.cash.TopUpKind
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.PaymentMethod
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.purchase.Purchase
import com.volp.travelbudget.domain.purchase.PurchaseKind
import com.volp.travelbudget.domain.purchase.PurchaseStatus
import com.volp.travelbudget.domain.travel.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 기록 한 벌을 드라이브에 올릴 글자로 바꾸고 다시 읽어 온다.
 *
 * 복원용 JSON과 함께 사람이 열어 보는 CSV도 만든다.
 */
object SyncCodec {

    fun toJson(snapshot: SyncSnapshot): String {
        val trips = JSONArray()
        snapshot.trips.forEach { trips.put(tripBundleToJson(it)) }

        val purchases = JSONArray()
        snapshot.purchases.forEach { purchases.put(purchaseToJson(it.purchase, it.tripUid)) }

        val deletions = JSONArray()
        snapshot.deletions.forEach { entry ->
            deletions.put(
                JSONObject()
                    .put("entity", entry.entity)
                    .put("uid", entry.uid)
                    .put("deletedAt", entry.deletedAt),
            )
        }

        return JSONObject()
            .put("version", SyncSnapshot.FORMAT_VERSION)
            .put("exportedAt", snapshot.exportedAt)
            .put("trips", trips)
            .put("purchases", purchases)
            .put("deletions", deletions)
            .toString(2)
    }

    fun fromJson(text: String): SyncSnapshot {
        if (text.isBlank()) return SyncSnapshot()
        val root = JSONObject(text)

        val tripsJson = root.optJSONArray("trips") ?: JSONArray()
        val trips = (0 until tripsJson.length()).map { tripBundleFromJson(tripsJson.getJSONObject(it)) }

        val purchases = root.optJSONArray("purchases").map { purchaseRecordFromJson(it) }

        val deletionsJson = root.optJSONArray("deletions") ?: JSONArray()
        val deletions = (0 until deletionsJson.length()).map { index ->
            val item = deletionsJson.getJSONObject(index)
            DeletionEntity(
                entity = item.optString("entity"),
                uid = item.optString("uid"),
                deletedAt = item.optLong("deletedAt"),
            )
        }.filter { it.uid.isNotBlank() }

        return SyncSnapshot(
            version = root.optInt("version", 1),
            exportedAt = root.optLong("exportedAt"),
            trips = trips,
            purchases = purchases,
            deletions = deletions,
        )
    }

    fun toCsv(snapshot: SyncSnapshot): String = buildString {
        appendLine("여행,날짜,항목,금액(원),현지금액,통화,적용환율,메모")
        snapshot.trips.forEach { bundle ->
            bundle.expenses.sortedBy { it.date }.forEach { expense ->
                appendLine(
                    listOf(
                        bundle.trip.title,
                        expense.date.toString(),
                        expense.category.label,
                        expense.amountKrw.toString(),
                        expense.originalAmount?.toString().orEmpty(),
                        expense.currencyCode,
                        expense.exchangeRate?.toString().orEmpty(),
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

    // ---- 여행 ----

    private fun tripBundleToJson(bundle: TripBundle): JSONObject =
        tripToJson(bundle.trip)
            .put("expenses", JSONArray().apply { bundle.expenses.forEach { put(expenseToJson(it)) } })
            .put("bookings", JSONArray().apply { bundle.bookings.forEach { put(bookingToJson(it)) } })
            .put("stops", JSONArray().apply { bundle.stops.forEach { put(stopToJson(it)) } })
            .put("notes", JSONArray().apply { bundle.notes.forEach { put(noteToJson(it)) } })
            .put("packing", JSONArray().apply { bundle.packing.forEach { put(packingToJson(it)) } })
            .put("cash", JSONArray().apply { bundle.cash.forEach { put(cashToJson(it)) } })

    fun tripToJson(trip: Trip): JSONObject {
        return JSONObject()
            .put("uid", trip.uid)
            .put("updatedAt", trip.updatedAt)
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
            .put("billedTotalKrw", trip.billedTotalKrw ?: JSONObject.NULL)
            .put("settlementFactor", trip.settlementFactor)
            .put("latitude", trip.latitude ?: JSONObject.NULL)
            .put("longitude", trip.longitude ?: JSONObject.NULL)
            .put("sortOrder", trip.sortOrder)
            .put("createdAt", trip.createdAt)
    }

    private fun tripBundleFromJson(json: JSONObject): TripBundle {
        val trip = tripFromJson(json)

        return TripBundle(
            trip = trip,
            expenses = json.optJSONArray("expenses").map { expenseFromJson(it) },
            bookings = json.optJSONArray("bookings").map { bookingFromJson(it) },
            stops = json.optJSONArray("stops").map { stopFromJson(it) },
            notes = json.optJSONArray("notes").map { noteFromJson(it) },
            packing = json.optJSONArray("packing").map { packingFromJson(it) },
            cash = json.optJSONArray("cash").map { cashFromJson(it) },
        )
    }

    fun tripFromJson(json: JSONObject): Trip {
        return Trip(
            uid = json.optString("uid"),
            updatedAt = json.optLong("updatedAt"),
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
            billedTotalKrw = if (json.isNull("billedTotalKrw")) null else json.optLong("billedTotalKrw"),
            settlementFactor = json.optDouble("settlementFactor", 1.0),
            latitude = if (json.isNull("latitude")) null else json.optDouble("latitude"),
            longitude = if (json.isNull("longitude")) null else json.optDouble("longitude"),
            sortOrder = json.optInt("sortOrder"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    // ---- 지출 ----

    fun expenseToJson(expense: Expense) = JSONObject()
        .put("uid", expense.uid)
        .put("updatedAt", expense.updatedAt)
        .put("category", expense.category.name)
        .put("amountKrw", expense.amountKrw)
        .put("originalAmount", expense.originalAmount ?: JSONObject.NULL)
        .put("currencyCode", expense.currencyCode)
        .put("date", expense.date.toString())
        .put("memo", expense.memo)
        .put("exchangeRate", expense.exchangeRate ?: JSONObject.NULL)
        .put("method", expense.method.name)
        .put("createdAt", expense.createdAt)

    fun expenseFromJson(json: JSONObject) = Expense(
        uid = json.optString("uid"),
        updatedAt = json.optLong("updatedAt"),
        tripId = 0L,
        category = ExpenseCategory.fromName(json.optString("category")),
        amountKrw = json.optLong("amountKrw"),
        originalAmount = if (json.isNull("originalAmount")) null else json.optDouble("originalAmount"),
        currencyCode = json.optString("currencyCode", "KRW"),
        date = LocalDate.parse(json.getString("date")),
        memo = json.optString("memo"),
        exchangeRate = if (json.isNull("exchangeRate")) null else json.optDouble("exchangeRate"),
        method = PaymentMethod.fromName(json.optString("method")),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
    )

    // ---- 예약 ----

    fun bookingToJson(booking: Booking) = JSONObject()
        .put("uid", booking.uid)
        .put("updatedAt", booking.updatedAt)
        .put("type", booking.type.name)
        .put("title", booking.title)
        .put("provider", booking.provider)
        .put("confirmationCode", booking.confirmationCode)
        .put("startAt", booking.startAt.toString())
        .put("endAt", booking.endAt?.toString() ?: JSONObject.NULL)
        .put("fromName", booking.fromName)
        .put("fromCode", booking.fromCode)
        .put("toName", booking.toName)
        .put("toCode", booking.toCode)
        .put("address", booking.address)
        .put("seat", booking.seat)
        .put("gate", booking.gate)
        .put("terminal", booking.terminal)
        .put("memo", booking.memo)
        .put("latitude", booking.point?.latitude ?: JSONObject.NULL)
        .put("longitude", booking.point?.longitude ?: JSONObject.NULL)

    fun bookingFromJson(json: JSONObject) = Booking(
        uid = json.optString("uid"),
        updatedAt = json.optLong("updatedAt"),
        tripId = 0L,
        type = BookingType.fromName(json.optString("type")),
        title = json.optString("title"),
        provider = json.optString("provider"),
        confirmationCode = json.optString("confirmationCode"),
        startAt = LocalDateTime.parse(json.getString("startAt")),
        endAt = if (json.isNull("endAt")) null else LocalDateTime.parse(json.getString("endAt")),
        fromName = json.optString("fromName"),
        fromCode = json.optString("fromCode"),
        toName = json.optString("toName"),
        toCode = json.optString("toCode"),
        address = json.optString("address"),
        seat = json.optString("seat"),
        gate = json.optString("gate"),
        terminal = json.optString("terminal"),
        memo = json.optString("memo"),
        point = pointOf(json),
    )

    // ---- 일정 장소 ----

    fun stopToJson(stop: ItineraryStop) = JSONObject()
        .put("uid", stop.uid)
        .put("updatedAt", stop.updatedAt)
        .put("date", stop.date.toString())
        .put("sortOrder", stop.sortOrder)
        .put("name", stop.name)
        .put("address", stop.address)
        .put("startTime", stop.startTime ?: JSONObject.NULL)
        .put("memo", stop.memo)
        .put("latitude", stop.point?.latitude ?: JSONObject.NULL)
        .put("longitude", stop.point?.longitude ?: JSONObject.NULL)

    fun stopFromJson(json: JSONObject) = ItineraryStop(
        uid = json.optString("uid"),
        updatedAt = json.optLong("updatedAt"),
        tripId = 0L,
        date = LocalDate.parse(json.getString("date")),
        sortOrder = json.optInt("sortOrder"),
        name = json.optString("name"),
        address = json.optString("address"),
        point = pointOf(json),
        startTime = if (json.isNull("startTime")) null else json.optString("startTime"),
        memo = json.optString("memo"),
    )

    // ---- 메모와 준비물 ----

    fun noteToJson(note: DayNote) = JSONObject()
        .put("date", note.date.toString())
        .put("text", note.text)
        .put("updatedAt", note.updatedAt)

    fun noteFromJson(json: JSONObject) = DayNote(
        date = LocalDate.parse(json.getString("date")),
        text = json.optString("text"),
        updatedAt = json.optLong("updatedAt"),
    )

    fun packingToJson(check: PackingCheck) = JSONObject()
        .put("itemName", check.itemName)
        .put("checked", check.checked)
        .put("updatedAt", check.updatedAt)

    fun packingFromJson(json: JSONObject) = PackingCheck(
        itemName = json.optString("itemName"),
        checked = json.optBoolean("checked"),
        updatedAt = json.optLong("updatedAt"),
    )

    // ---- 현금 ----

    fun cashToJson(topUp: CashTopUp) = JSONObject()
        .put("uid", topUp.uid)
        .put("updatedAt", topUp.updatedAt)
        .put("kind", topUp.kind.name)
        .put("currencyCode", topUp.currencyCode)
        .put("amount", topUp.amount)
        .put("krwPaid", topUp.krwPaid)
        .put("date", topUp.date.toString())
        .put("memo", topUp.memo)
        .put("createdAt", topUp.createdAt)

    fun cashFromJson(json: JSONObject) = CashTopUp(
        uid = json.optString("uid"),
        updatedAt = json.optLong("updatedAt"),
        tripId = 0L,
        kind = TopUpKind.fromName(json.optString("kind")),
        currencyCode = json.optString("currencyCode", "KRW"),
        amount = json.optDouble("amount", 0.0),
        krwPaid = json.optLong("krwPaid"),
        date = LocalDate.parse(json.getString("date")),
        memo = json.optString("memo"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
    )

    // ---- 구매 ----

    fun purchaseToJson(purchase: Purchase, tripUid: String?) = JSONObject()
        .put("uid", purchase.uid)
        .put("updatedAt", purchase.updatedAt)
        .put("tripUid", tripUid ?: JSONObject.NULL)
        .put("kind", purchase.kind.name)
        .put("title", purchase.title)
        .put("merchant", purchase.merchant)
        .put("amountKrw", purchase.amountKrw)
        .put("originalAmount", purchase.originalAmount ?: JSONObject.NULL)
        .put("currencyCode", purchase.currencyCode)
        .put("orderedOn", purchase.orderedOn?.toString() ?: JSONObject.NULL)
        .put("eta", purchase.eta?.toString() ?: JSONObject.NULL)
        .put("status", purchase.status.name)
        .put("orderNumber", purchase.orderNumber)
        .put("trackingNumber", purchase.trackingNumber)
        .put("carrier", purchase.carrier)
        .put("memo", purchase.memo)
        .put("sourceText", purchase.sourceText)
        .put("createdAt", purchase.createdAt)

    fun purchaseRecordFromJson(json: JSONObject) = PurchaseRecord(
        purchase = Purchase(
            uid = json.optString("uid"),
            updatedAt = json.optLong("updatedAt"),
            kind = PurchaseKind.fromName(json.optString("kind")),
            title = json.optString("title"),
            merchant = json.optString("merchant"),
            amountKrw = json.optLong("amountKrw"),
            originalAmount = if (json.isNull("originalAmount")) null else json.optDouble("originalAmount"),
            currencyCode = json.optString("currencyCode", "KRW"),
            orderedOn = dateOrNull(json, "orderedOn"),
            eta = dateOrNull(json, "eta"),
            status = PurchaseStatus.fromName(json.optString("status")),
            orderNumber = json.optString("orderNumber"),
            trackingNumber = json.optString("trackingNumber"),
            carrier = json.optString("carrier"),
            memo = json.optString("memo"),
            sourceText = json.optString("sourceText"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        ),
        tripUid = if (json.isNull("tripUid")) null else json.optString("tripUid").ifBlank { null },
    )

    private fun dateOrNull(json: JSONObject, key: String): LocalDate? =
        if (json.isNull(key)) null else runCatching { LocalDate.parse(json.getString(key)) }.getOrNull()

    // ---- 도우미 ----

    private fun pointOf(json: JSONObject): GeoPoint? =
        if (json.isNull("latitude") || json.isNull("longitude")) {
            null
        } else {
            GeoPoint(json.optDouble("latitude"), json.optDouble("longitude"))
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

    fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            runCatching { transform(getJSONObject(index)) }.getOrNull()
        }
    }
}
