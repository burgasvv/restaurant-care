package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.Serializable
import org.burgas.plugin.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.time.format.DateTimeFormatter
import java.util.*

@Suppress("unused")
enum class Position {
    DIRECTOR, MANAGER, SERVANT
}

@Serializable
data class EmployeeRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    @Serializable(with = UUIDSerialization::class)
    val identityId: UUID? = null,
    val position: Position? = null,
    @Serializable(with = UUIDSerialization::class)
    val locationRestaurantId: UUID? = null,
    @Serializable(with = UUIDSerialization::class)
    val locationAddressId: UUID? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val age: Int? = null,
    val birthday: LocalDate? = null,
    val employeeAddress: AddressRequest? = null
)

@Serializable
data class EmployeeShortResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val position: Position? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val age: Int? = null,
    val birthday: String? = null,
    val employeeAddress: AddressResponse? = null
)

@Serializable
data class EmployeeFullResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val identity: IdentityShortResponse? = null,
    val position: Position? = null,
    val restaurant: RestaurantShortResponse? = null,
    val location: LocationShortResponse? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val age: Int? = null,
    val birthday: String? = null,
    val employeeAddress: AddressResponse? = null
)

fun ResultRow.toEmployeeShortResponse(): EmployeeShortResponse {
    val employeeAddress = Address.selectAll().where { Address.id eq this[Employee.employeeAddressId] }
        .map { resultRow -> resultRow.toAddressResponse() }
        .singleOrNull()

    return EmployeeShortResponse(
        id = this[Employee.id],
        position = this[Employee.position],
        firstname = this[Employee.firstname],
        lastname = this[Employee.lastname],
        patronymic = this[Employee.patronymic],
        age = this[Employee.age],
        birthday = this[Employee.birthday].format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
        employeeAddress = employeeAddress
    )
}

fun ResultRow.toEmployeeFullResponse(): EmployeeFullResponse {
    val identity = Identity.selectAll().where { Identity.id eq this[Employee.identityId] }
        .map { resultRow -> resultRow.toIdentityShortResponse() }
        .singleOrNull()

    val restaurant =
        Restaurant.selectAll().where { Restaurant.id eq (this[Employee.locationRestaurantId] ?: UUID.randomUUID()) }
            .map { resultRow -> resultRow.toRestaurantShortResponse() }
            .singleOrNull()

    val location = Location
        .leftJoin(Address, { Location.addressId }, { Address.id })
        .selectAll()
        .where { Address.id eq (this[Employee.locationAddressId] ?: UUID.randomUUID()) }
        .map { resultRow -> resultRow.toLocationShortResponse() }
        .singleOrNull()

    val employeeAddress = Address.selectAll().where { Address.id eq this[Employee.employeeAddressId] }
        .map { resultRow -> resultRow.toAddressResponse() }
        .singleOrNull()

    return EmployeeFullResponse(
        id = this[Employee.id],
        identity = identity,
        position = this[Employee.position],
        restaurant = restaurant,
        location = location,
        firstname = this[Employee.firstname],
        lastname = this[Employee.lastname],
        patronymic = this[Employee.patronymic],
        age = this[Employee.age],
        birthday = this[Employee.birthday].format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
        employeeAddress = employeeAddress
    )
}

fun InsertStatement<Number>.toEmployee(employeeRequest: EmployeeRequest, employeeAddressId: UUID) {
    this[Employee.identityId] = employeeRequest.identityId ?: throw IllegalArgumentException("Identity id is null")
    this[Employee.position] = employeeRequest.position ?: throw IllegalArgumentException("Position not found")
    this[Employee.locationRestaurantId] = employeeRequest.locationRestaurantId
    this[Employee.locationAddressId] = employeeRequest.locationAddressId
    this[Employee.firstname] = employeeRequest.firstname ?: throw IllegalArgumentException("Firstname is null")
    this[Employee.lastname] = employeeRequest.lastname ?: throw IllegalArgumentException("Lastname is null")
    this[Employee.patronymic] = employeeRequest.patronymic ?: throw IllegalArgumentException("Patronymic is null")
    this[Employee.age] = employeeRequest.age ?: throw IllegalArgumentException("Age is null")
    this[Employee.birthday] =
        (employeeRequest.birthday ?: throw IllegalArgumentException("Birthday is null")).toJavaLocalDate()
    this[Employee.employeeAddressId] = employeeAddressId
}

fun UpdateStatement.toEmployee(employeeRequest: EmployeeRequest, employee: ResultRow, employeeAddress: AddressResponse?) {
    this[Employee.id] = employee[Employee.id]
    this[Employee.identityId] = employeeRequest.identityId ?: employee[Employee.identityId]
    this[Employee.position] = employeeRequest.position ?: employee[Employee.position]
    this[Employee.locationRestaurantId] = employeeRequest.locationRestaurantId ?: employee[Employee.locationRestaurantId]
    this[Employee.locationAddressId] = employeeRequest.locationAddressId ?: employee[Employee.locationAddressId]
    this[Employee.firstname] = employeeRequest.firstname ?: employee[Employee.firstname]
    this[Employee.lastname] = employeeRequest.lastname ?: employee[Employee.lastname]
    this[Employee.patronymic] = employeeRequest.patronymic ?: employee[Employee.patronymic]
    this[Employee.age] = employeeRequest.age ?: employee[Employee.age]
    this[Employee.birthday] = employee[Employee.birthday]
    this[Employee.employeeAddressId] = if (employeeAddress != null) employeeAddress.id as UUID else employee[Employee.employeeAddressId]
}

class EmployeeService {

    val addressService = AddressService()

    suspend fun create(employeeRequest: EmployeeRequest) = withContext(Dispatchers.Default) {
        val addressRequest = employeeRequest.employeeAddress
            ?: throw IllegalArgumentException("Employee address request is null")
        val employeeAddressId = addressService.create(addressRequest)
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Employee.insert { insertStatement ->
                insertStatement.toEmployee(employeeRequest, employeeAddressId)
            }
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Employee.selectAll()
                .map { resultRow -> resultRow.toEmployeeShortResponse() }
                .toList()
        }
    }

    suspend fun findById(employeeId: UUID) = withContext(Dispatchers.Default) {
        val locationAddress = Address.alias("locationAddress")
        val employeeAddress = Address.alias("employeeAddress")
        transaction(readOnly = true) {
            Employee
                .leftJoin(Identity, { Employee.identityId }, { Identity.id })
                .leftJoin(Restaurant, { Employee.locationRestaurantId }, { Restaurant.id })
                .leftJoin(
                    locationAddress,
                    { Employee.locationAddressId },
                    { locationAddress[Address.id] }
                )
                .leftJoin(
                    employeeAddress,
                    { Employee.employeeAddressId },
                    { employeeAddress[Address.id] }
                )
                .selectAll()
                .where { Employee.id eq employeeId }
                .map { resultRow -> resultRow.toEmployeeFullResponse() }
                .singleOrNull() ?: throw IllegalArgumentException("Employee not found")
        }
    }

    suspend fun update(employeeRequest: EmployeeRequest) = withContext(Dispatchers.Default) {
        val employeeId = employeeRequest.id ?: throw IllegalArgumentException("Employee id is null")
        val addressRequest = employeeRequest.employeeAddress
        var employeeAddress: AddressResponse? = null
        if (addressRequest != null) {
            addressRequest.id ?: throw IllegalArgumentException("Address id is null")
            employeeAddress = addressService.update(addressRequest)
        }
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val employee = Employee.selectAll().where { Employee.id eq employeeId }.singleOrNull()
                ?: throw IllegalArgumentException("Employee not found")
            Employee.update({ Employee.id eq employee[Employee.id] }) {
                    updateStatement -> updateStatement.toEmployee(employeeRequest, employee, employeeAddress)
            } > 0
        }
    }

    suspend fun delete(employeeId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Employee.deleteWhere { Employee.id eq employeeId } > 0
        }
    }
}

fun Application.configureEmployeeRouter() {

    val employeeService = EmployeeService()

    routing {

        route("/api/v1") {

            post("/employees/create") {
                val employeeRequest = call.receive(EmployeeRequest::class)
                employeeService.create(employeeRequest)
                call.respond(HttpStatusCode.Created)
            }

            get("/employees") {
                call.respond(HttpStatusCode.OK, employeeService.findAll())
            }

            get("/employees/by-id") {
                val employeeId = UUID.fromString(
                    call.parameters["employeeId"] ?: throw IllegalArgumentException("Employee id is null")
                )
                call.respond(HttpStatusCode.OK, employeeService.findById(employeeId))
            }

            put("/employees/update") {
                val employeeRequest = call.receive(EmployeeRequest::class)
                if (employeeService.update(employeeRequest)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            delete("/employees/delete") {
                val employeeId = UUID.fromString(
                    call.parameters["employeeId"] ?: throw IllegalArgumentException("Employee id is null")
                )
                if (employeeService.delete(employeeId)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}