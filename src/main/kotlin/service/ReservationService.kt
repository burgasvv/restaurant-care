package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.serialization.Serializable
import org.burgas.plugin.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.time.format.DateTimeFormatter
import java.util.*

@Serializable
data class ReservationRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    @Serializable(with = UUIDSerialization::class)
    val locationRestaurantId: UUID? = null,
    @Serializable(with = UUIDSerialization::class)
    val locationAddressId: UUID? = null,
    val name: String? = null,
    val phone: String? = null,
    val places: Int? = null,
    val startTime: LocalDateTime? = null
)

@Serializable
data class ReservationSearch(
    val name: String,
    val phone: String
)

@Serializable
data class ReservationResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val restaurant: RestaurantShortResponse? = null,
    val location: LocationShortResponse? = null,
    val name: String? = null,
    val phone: String? = null,
    val places: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val isStarted: Boolean? = null,
    val isFinished: Boolean? = null
)

fun ResultRow.toReservationResponse(): ReservationResponse {
    val restaurant = Restaurant.selectAll().where { Restaurant.id eq this[Restaurant.id] }
        .map { resultRow -> resultRow.toRestaurantShortResponse() }
        .singleOrNull() ?: throw IllegalArgumentException("Restaurant not found")

    val location = Location.selectAll()
        .where { (Location.restaurantId eq this[Restaurant.id]) and (Location.addressId eq this[Address.id]) }
        .map { resultRow -> resultRow.toLocationShortResponse() }
        .singleOrNull() ?: throw IllegalArgumentException("Location not found")

    return ReservationResponse(
        id = this[Reservation.id],
        restaurant = restaurant,
        location = location,
        name = this[Reservation.name],
        phone = this[Reservation.phone],
        places = this[Reservation.places],
        startTime = this[Reservation.startTime].format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
        endTime = if (this[Reservation.endTime] == null) null else this[Reservation.endTime]
            ?.format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
        isStarted = this[Reservation.isStarted],
        isFinished = this[Reservation.isFinished]
    )
}

fun InsertStatement<Number>.toReservation(reservationRequest: ReservationRequest) {
    this[Reservation.locationRestaurantId] =
        reservationRequest.locationRestaurantId ?: throw IllegalArgumentException("Restaurant id is null")
    this[Reservation.locationAddressId] =
        reservationRequest.locationAddressId ?: throw IllegalArgumentException("Location address id is null")
    this[Reservation.name] = reservationRequest.name ?: throw IllegalArgumentException("Name is null")
    this[Reservation.phone] = reservationRequest.phone ?: throw IllegalArgumentException("Phone is null")
    this[Reservation.places] = reservationRequest.places ?: throw IllegalArgumentException("Places is null")
    this[Reservation.startTime] =
        (reservationRequest.startTime ?: throw IllegalArgumentException("Start time is null")).toJavaLocalDateTime()
    this[Reservation.endTime] = null
    this[Reservation.isStarted] = false
    this[Reservation.isFinished] = false
}

fun UpdateStatement.toReservation(reservationRequest: ReservationRequest, reservation: ResultRow) {
    this[Reservation.id] = reservation[Reservation.id]
    this[Reservation.locationRestaurantId] =
        reservationRequest.locationRestaurantId ?: reservation[Reservation.locationRestaurantId]
    this[Reservation.locationAddressId] =
        reservationRequest.locationAddressId ?: reservation[Reservation.locationAddressId]
    this[Reservation.name] = reservationRequest.name ?: reservation[Reservation.name]
    this[Reservation.phone] = reservationRequest.phone ?: reservation[Reservation.phone]
    this[Reservation.places] = reservationRequest.places ?: reservation[Reservation.places]
    this[Reservation.startTime] = if (reservationRequest.startTime == null)
        reservation[Reservation.startTime] else reservationRequest.startTime.toJavaLocalDateTime()
    this[Reservation.endTime] = reservation[Reservation.endTime]
    this[Reservation.isStarted] = reservation[Reservation.isStarted]
    this[Reservation.isFinished] = reservation[Reservation.isFinished]
}

class ReservationService {

    suspend fun create(reservationRequest: ReservationRequest) = withContext(Dispatchers.Default) {
        val locationRestaurantId = reservationRequest.locationRestaurantId
            ?: throw IllegalArgumentException("Restaurant id is null")

        val locationAddressId = reservationRequest.locationAddressId
            ?: throw IllegalArgumentException("Location address id is null")

        val places = if (reservationRequest.places != null && reservationRequest.places > 0)
            reservationRequest.places else throw IllegalArgumentException("Reservation places is wrong amount")

        val startLocalDateTime = reservationRequest.startTime ?: throw IllegalArgumentException("Start time is null")
        val startDate = startLocalDateTime.toJavaLocalDateTime().toLocalDate()

        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {

            val operation = (Location.restaurantId eq locationRestaurantId) and
                    (Location.addressId eq locationAddressId)

            val restaurantDateReservations = Reservation.selectAll()
                .where {
                    (Reservation.startTime.date() eq startDate) and
                            (Reservation.locationRestaurantId eq locationRestaurantId) and
                            (Reservation.locationAddressId eq locationAddressId) and
                            (Reservation.isFinished eq false) and (Reservation.isStarted eq false)
                }
                .toList()

            var dateReservedPlaces = 0
            if (!restaurantDateReservations.isEmpty()) {
                dateReservedPlaces = restaurantDateReservations.map { resultRow -> resultRow[Reservation.places] }
                    .reduce { acc, i -> acc + i }
            }

            val location = Location.selectAll().where { operation }
                .singleOrNull() ?: throw IllegalArgumentException("Location not found")

            val localTime = startLocalDateTime.toJavaLocalDateTime().toLocalTime()
            if (localTime.isAfter(location[Location.open]) && localTime.isBefore(location[Location.close])) {

                if (location[Location.places] < places) {
                    throw IllegalArgumentException("Location restaurant capacity not enough for reservation")
                }

                val freePlaces = location[Location.places] - dateReservedPlaces

                if (freePlaces >= places) {
                    Reservation.insert { insertStatement -> insertStatement.toReservation(reservationRequest) }

                } else {
                    throw IllegalArgumentException("Location restaurant places not enough for reservation")
                }

            } else {
                throw IllegalArgumentException("Reservation in wrong restaurant location work time")
            }
        }
    }

    suspend fun findById(reservationId: UUID) = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Reservation
                .leftJoin(Restaurant, { Reservation.locationRestaurantId }, { Restaurant.id })
                .leftJoin(Address, { Reservation.locationAddressId }, { Address.id })
                .selectAll()
                .where { Reservation.id eq reservationId }
                .map { resultRow -> resultRow.toReservationResponse() }
                .singleOrNull() ?: throw IllegalArgumentException("Reservation not found")
        }
    }

    suspend fun findByClient(reservationSearch: ReservationSearch) = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Reservation
                .leftJoin(Restaurant, { Reservation.locationRestaurantId }, { Restaurant.id })
                .leftJoin(Address, { Reservation.locationAddressId }, { Address.id })
                .selectAll()
                .where { (Reservation.name eq reservationSearch.name) and (Reservation.phone eq reservationSearch.phone) }
                .map { resultRow -> resultRow.toReservationResponse() }
                .toList()
        }
    }

    suspend fun update(reservationRequest: ReservationRequest) = withContext(Dispatchers.Default) {
        val reservationId = reservationRequest.id ?: throw IllegalArgumentException("Reservation id is null")

        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val reservation = Reservation.selectAll()
                .where { (Reservation.id eq reservationId) and (Reservation.isFinished eq false) }
                .singleOrNull() ?: throw IllegalArgumentException("Reservation not found")

            val places = if (reservationRequest.places != null && reservationRequest.places > 0)
                reservationRequest.places else reservation[Reservation.places]

            val locationRestaurantId =
                reservationRequest.locationRestaurantId ?: reservation[Reservation.locationRestaurantId]
            val locationAddressId = reservationRequest.locationAddressId ?: reservation[Reservation.locationAddressId]
            val operation =
                (Location.restaurantId eq locationRestaurantId) and (Location.addressId eq locationAddressId)

            val startLocalDateTime = reservationRequest.startTime
            val startDate =
                if (startLocalDateTime != null) startLocalDateTime.toJavaLocalDateTime().toLocalDate()
                else reservation[Reservation.startTime].toLocalDate()

            val restaurantDateReservations = Reservation.selectAll()
                .where {
                    (Reservation.startTime.date() eq startDate) and
                            (Reservation.locationRestaurantId eq locationRestaurantId) and
                            (Reservation.locationAddressId eq locationAddressId) and
                            (Reservation.isFinished eq false) and
                            (Reservation.isStarted eq false) and
                            (Reservation.id neq reservation[Reservation.id])
                }
                .toList()

            var dateReservedPlaces = 0
            if (!restaurantDateReservations.isEmpty()) {
                dateReservedPlaces = restaurantDateReservations.map { resultRow -> resultRow[Reservation.places] }
                    .reduce { acc, i -> acc + i }
            }

            val location = Location.selectAll().where { operation }
                .singleOrNull() ?: throw IllegalArgumentException("Location not found")

            val startTime =
                if (startLocalDateTime != null) startLocalDateTime.toJavaLocalDateTime().toLocalTime()
                else reservation[Reservation.startTime].toLocalTime()

            if (startTime.isAfter(location[Location.open]) && startTime.isBefore(location[Location.close])) {

                if (location[Location.places] < places) {
                    throw IllegalArgumentException("Location restaurant capacity not enough for reservation")
                }

                val freePlaces = location[Location.places] - dateReservedPlaces

                if (freePlaces >= places) {
                    Reservation.update({ Reservation.id eq reservation[Reservation.id] }) { updateStatement ->
                        updateStatement.toReservation(reservationRequest, reservation)
                    } > 0

                } else {
                    throw IllegalArgumentException("Location restaurant places not enough for reservation")
                }

            } else {
                throw IllegalArgumentException("Reservation in wrong restaurant location work time")
            }
        }
    }

    suspend fun start(reservationId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Reservation.update({ Reservation.id eq reservationId }) { updateStatement ->
                updateStatement[Reservation.isStarted] = true
            } > 0
        }
    }

    suspend fun finish(reservationId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Reservation.update({ Reservation.id eq reservationId }) { updateStatement ->
                updateStatement[Reservation.endTime] = java.time.LocalDateTime.now()
                updateStatement[Reservation.isFinished] = true
            } > 0
        }
    }
}

fun Application.configureReservationRouter() {

    val reservationService = ReservationService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/reservations/finish", false)) {

                val principal =
                    call.principal<UserPasswordCredential>() ?: throw IllegalArgumentException("Not authenticated")
                val reservationId = UUID.fromString(
                    call.parameters["reservationId"] ?: throw IllegalArgumentException("Reservation id is null")
                )

                val reservation = Reservation
                    .leftJoin(Employee, { Reservation.locationRestaurantId }, { Employee.locationRestaurantId })
                    .leftJoin(Identity, { Employee.identityId }, { Identity.id })
                    .selectAll()
                    .where { Reservation.id eq reservationId }
                    .singleOrNull() ?: throw IllegalArgumentException("Reservation of restaurant not found")

                if (reservation[Identity.email] == principal.name) {
                    proceed()

                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }
            proceed()
        }

        route("/api/v1/reservations") {

            post("/create") {
                val reservationRequest = call.receive(ReservationRequest::class)
                reservationService.create(reservationRequest)
                call.respond(HttpStatusCode.Created)
            }

            get("/by-id") {
                val reservationId = UUID.fromString(
                    call.parameters["reservationId"] ?: throw IllegalArgumentException("ReservationId is null")
                )
                call.respond(HttpStatusCode.OK, reservationService.findById(reservationId))
            }

            get("/by-client") {
                val reservationSearch = call.receive(ReservationSearch::class)
                call.respond(HttpStatusCode.OK, reservationService.findByClient(reservationSearch))
            }

            put("/update") {
                val reservationRequest = call.receive(ReservationRequest::class)
                if (reservationService.update(reservationRequest)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            authenticate("basic-auth-all") {

                put("/start") {
                    val reservationId = UUID.fromString(
                        call.parameters["reservationId"] ?: throw IllegalArgumentException("ReservationId is null")
                    )
                    if (reservationService.start(reservationId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                put("/finish") {
                    val reservationId = UUID.fromString(
                        call.parameters["reservationId"] ?: throw IllegalArgumentException("ReservationId is null")
                    )
                    if (reservationService.finish(reservationId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}