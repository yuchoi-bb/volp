package com.volp.travelbudget.data.repository

import com.volp.travelbudget.data.local.BookingDao
import com.volp.travelbudget.data.local.DayNoteEntity
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.local.toEntity
import com.volp.travelbudget.domain.booking.Booking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class BookingRepository(private val dao: BookingDao) {

    fun observeBookings(tripId: Long): Flow<List<Booking>> =
        dao.observeByTrip(tripId).map { list -> list.map { it.toDomain() } }

    suspend fun bookingsOnce(tripId: Long): List<Booking> =
        dao.findByTrip(tripId).map { it.toDomain() }

    suspend fun find(bookingId: Long): Booking? = dao.findById(bookingId)?.toDomain()

    suspend fun save(booking: Booking): Long =
        if (booking.id > 0L) {
            dao.update(booking.toEntity())
            booking.id
        } else {
            dao.insert(booking.toEntity())
        }

    suspend fun delete(bookingId: Long) = dao.deleteById(bookingId)

    /** 날짜별 메모. 화면에서는 날짜로 바로 찾아 쓴다. */
    fun observeNotes(tripId: Long): Flow<Map<LocalDate, String>> =
        dao.observeNotes(tripId).map { notes -> notes.associate { it.date to it.text } }

    suspend fun saveNote(tripId: Long, date: LocalDate, text: String) =
        dao.upsertNote(DayNoteEntity(tripId, date, text.trim(), System.currentTimeMillis()))
}
