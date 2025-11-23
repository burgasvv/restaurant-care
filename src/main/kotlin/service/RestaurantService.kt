package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.burgas.plugin.Address
import org.burgas.plugin.Location
import org.burgas.plugin.Restaurant
import org.burgas.plugin.UUIDSerialization
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.util.*

@Serializable
data class RestaurantRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class RestaurantShortResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class RestaurantFullResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val locations: List<LocationShortResponse>? = null
)

fun ResultRow.toRestaurantShortResponse(): RestaurantShortResponse {
    return RestaurantShortResponse(
        id = this[Restaurant.id],
        name = this[Restaurant.name],
        description = this[Restaurant.description]
    )
}

fun ResultRow.toRestaurantFullResponse(locations: List<LocationShortResponse>): RestaurantFullResponse {
    return RestaurantFullResponse(
        id = this[Restaurant.id],
        name = this[Restaurant.name],
        description = this[Restaurant.description],
        locations = locations
    )
}

fun InsertStatement<Number>.toRestaurant(restaurantRequest: RestaurantRequest) {
    this[Restaurant.name] = restaurantRequest.name ?: throw IllegalArgumentException("Restaurant name is null")
    this[Restaurant.description] = restaurantRequest.description ?: throw IllegalArgumentException("Restaurant description is null")
}

fun UpdateStatement.toRestaurant(restaurantRequest: RestaurantRequest, restaurant: ResultRow) {
    this[Restaurant.id] = restaurant[Restaurant.id]
    this[Restaurant.name] = restaurantRequest.name ?: restaurant[Restaurant.name]
    this[Restaurant.description] = restaurantRequest.description ?: restaurant[Restaurant.description]
}

class RestaurantService {

    suspend fun create(restaurantRequest: RestaurantRequest) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Restaurant.insert { insertStatement -> insertStatement.toRestaurant(restaurantRequest) }
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Restaurant.selectAll()
                .map { resultRow -> resultRow.toRestaurantShortResponse() }
                .toList()
        }
    }

    suspend fun findById(restaurantId: UUID) = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            val locations = (Restaurant leftJoin Location)
                .select(Location.fields)
                .where { Restaurant.id eq restaurantId }
                .map { resultRow -> resultRow.toLocationShortResponse() }
                .toList()
            println(locations)
            Restaurant.selectAll().where { Restaurant.id eq restaurantId }
                .map { resultRow -> resultRow.toRestaurantFullResponse(locations) }
                .singleOrNull() ?: throw IllegalArgumentException("Restaurant not found")
        }
    }

    suspend fun update(restaurantRequest: RestaurantRequest) = withContext(Dispatchers.Default) {
        val restaurantId = restaurantRequest.id ?: throw IllegalArgumentException("Restaurant id is null")
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val restaurant = Restaurant.selectAll().where { Restaurant.id eq restaurantId }
                .singleOrNull() ?: throw IllegalArgumentException("Restaurant not found")
            Restaurant.update({ Restaurant.id eq restaurant[Restaurant.id] }) { updateStatement ->
                updateStatement.toRestaurant(restaurantRequest, restaurant)
            }
        } > 0
    }

    suspend fun delete(restaurantId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val locations = Location.selectAll().where { Location.restaurantId eq restaurantId }.toList()
            locations.forEach { resultRow ->
                Address.deleteWhere { Address.id eq resultRow[Location.addressId] }
            }
            Restaurant.deleteWhere { Restaurant.id eq restaurantId } > 0
        }
    }
}

fun Application.configureRestaurantRouter() {

    val restaurantService = RestaurantService()

    routing {

        route("/api/v1") {

            post("/restaurants/create") {
                val restaurantRequest = call.receive(RestaurantRequest::class)
                restaurantService.create(restaurantRequest)
                call.respond(HttpStatusCode.Created)
            }

            get("/restaurants") {
                call.respond(HttpStatusCode.OK, restaurantService.findAll())
            }

            get("/restaurants/by-id") {
                val restaurantId = UUID.fromString(
                    call.parameters["restaurantId"] ?: throw IllegalArgumentException("Restaurant  id is null")
                )
                call.respond(HttpStatusCode.OK, restaurantService.findById(restaurantId))
            }

            put("/restaurant/update") {
                val restaurantRequest = call.receive(RestaurantRequest::class)
                if (restaurantService.update(restaurantRequest)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            delete("/restaurants/delete") {
                val restaurantId = UUID.fromString(
                    call.parameters["restaurantId"] ?: throw IllegalArgumentException("Restaurant  id is null")
                )
                if (restaurantService.delete(restaurantId)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}