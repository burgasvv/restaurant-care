package org.burgas.service

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalTime
import kotlinx.serialization.Serializable
import org.burgas.plugin.Address
import org.burgas.plugin.Location
import org.burgas.plugin.Restaurant
import org.burgas.plugin.UUIDSerialization
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.time.format.DateTimeFormatter
import java.util.*

@Serializable
data class LocationRequest(
    @Serializable(with = UUIDSerialization::class)
    val restaurantId: UUID? = null,
    val address: AddressRequest? = null,
    val open: LocalTime? = null,
    val close: LocalTime? = null
)

@Serializable
data class LocationShortResponse(
    val address: AddressResponse? = null,
    val open: String? = null,
    val close: String? = null
)

@Serializable
data class LocationFullResponse(
    val restaurant: RestaurantShortResponse? = null,
    val address: AddressResponse? = null,
    val open: String? = null,
    val close: String? = null
)

fun ResultRow.toLocationShortResponse(): LocationShortResponse {
    val address = Address.selectAll().where { Address.id eq this[Location.addressId] }
        .map { resultRow -> resultRow.toAddressResponse() }
        .singleOrNull()
    return LocationShortResponse(
        address = address,
        open = this[Location.open]?.format(DateTimeFormatter.ofPattern("hh:mm")),
        close = this[Location.close]?.format(DateTimeFormatter.ofPattern("hh:mm"))
    )
}

fun ResultRow.toLocationFullResponse(): LocationFullResponse {
    val restaurantShortResponse = Restaurant.selectAll().where { Restaurant.id eq this[Location.restaurantId] }
        .map { resultRow -> resultRow.toRestaurantShortResponse() }
        .singleOrNull() ?: throw IllegalArgumentException("Restaurant not found")
    val address = Address.selectAll().where { Address.id eq this[Location.addressId] }
        .map { resultRow -> resultRow.toAddressResponse() }
        .singleOrNull() ?: throw IllegalArgumentException("Address not found")
    return LocationFullResponse(
        restaurant = restaurantShortResponse,
        address = address,
        open = this[Location.open]?.format(DateTimeFormatter.ofPattern("hh:mm")),
        close = this[Location.close]?.format(DateTimeFormatter.ofPattern("hh:mm"))
    )
}

fun InsertStatement<Number>.toLocation(locationRequest: LocationRequest, addressId: UUID) {
    this[Location.restaurantId] =
        locationRequest.restaurantId ?: throw IllegalArgumentException("Restaurant id is null")
    this[Location.addressId] = addressId
    this[Location.open] = (locationRequest.open ?: throw IllegalArgumentException("Open is null")).toJavaLocalTime()
    this[Location.close] = (locationRequest.close ?: throw IllegalArgumentException("Close is null")).toJavaLocalTime()
}

fun UpdateStatement.toLocation(locationRequest: LocationRequest, location: ResultRow, newAddressId: UUID?) {
    this[Location.restaurantId] = locationRequest.restaurantId ?: location[Location.restaurantId]
    this[Location.addressId] = newAddressId ?: location[Location.addressId]
    this[Location.open] = location[Location.open]
    this[Location.close] = location[Location.close]
}

class LocationService {

    val addressService = AddressService()

    suspend fun create(locationRequest: LocationRequest) = withContext(Dispatchers.Default) {
        val addressRequest = locationRequest.address ?: throw IllegalArgumentException("Address request is null")
        val addressId = addressService.create(addressRequest)
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Location.insert { insertStatement -> insertStatement.toLocation(locationRequest, addressId) }
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Location.selectAll()
                .map { resultRow -> resultRow.toLocationFullResponse() }
                .toList()
        }
    }

    suspend fun findById(restaurantId: UUID, addressId: UUID) = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Location.selectAll().where { (Location.restaurantId eq restaurantId) and (Location.addressId eq addressId) }
                .map { resultRow -> resultRow.toLocationFullResponse() }
                .singleOrNull() ?: throw IllegalArgumentException("Location not found")
        }
    }

    suspend fun update(locationRequest: LocationRequest, restaurantId: UUID, addressId: UUID) =
        withContext(Dispatchers.Default) {
            var newAddressId: UUID? = null
            if (locationRequest.address != null) {
                newAddressId = addressService.create(locationRequest.address)
            }
            var isUpdated = false
            transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                val location = Location.selectAll()
                    .where { (Location.restaurantId eq restaurantId) and (Location.addressId eq addressId) }
                    .singleOrNull() ?: throw IllegalArgumentException("Location not found")
                isUpdated = Location.update(
                    { (Location.restaurantId eq location[Location.restaurantId]) and (Location.addressId eq location[Location.addressId]) }
                ) { updateStatement ->
                    updateStatement.toLocation(locationRequest, location, newAddressId)
                } > 0
            }
            addressService.delete(addressId)
            isUpdated
        }

    suspend fun delete(restaurantId: UUID, addressId: UUID) = withContext(Dispatchers.Default) {
        var isDeleted = false
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            isDeleted = Location.deleteWhere { (Location.restaurantId eq restaurantId) and (Location.addressId eq addressId) } > 0
        }
        addressService.delete(addressId)
        isDeleted
    }
}

fun Application.configureLocationRouter() {

    val locationService = LocationService()

    routing {

        route("/api/v1") {

            get("/locations") {
                call.respond(HttpStatusCode.OK, locationService.findAll())
            }

            get("/locations/by-id") {
                val restaurantId = UUID.fromString(
                    call.parameters["restaurantId"] ?: throw IllegalArgumentException("Restaurant id is null")
                )
                val addressId = UUID.fromString(
                    call.parameters["addressId"] ?: throw IllegalArgumentException("Address id is null")
                )
                call.respond(HttpStatusCode.OK, locationService.findById(restaurantId, addressId))
            }

            authenticate("basic-auth-all") {

                post("/locations/create") {
                    val locationRequest = call.receive(LocationRequest::class)
                    locationService.create(locationRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/locations/update") {
                    val restaurantId = UUID.fromString(
                        call.parameters["restaurantId"] ?: throw IllegalArgumentException("Restaurant id is null")
                    )
                    val addressId = UUID.fromString(
                        call.parameters["addressId"] ?: throw IllegalArgumentException("Address id is null")
                    )
                    val locationRequest = call.receive(LocationRequest::class)
                    if (locationService.update(locationRequest, restaurantId, addressId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/locations/delete") {
                    val restaurantId = UUID.fromString(
                        call.parameters["restaurantId"] ?: throw IllegalArgumentException("Restaurant id is null")
                    )
                    val addressId = UUID.fromString(
                        call.parameters["addressId"] ?: throw IllegalArgumentException("Address id is null")
                    )
                    if (locationService.delete(restaurantId, addressId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}