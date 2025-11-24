package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.Serializable
import org.burgas.plugin.*
import org.burgas.plugin.Identity
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
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
data class EmployeeDirectorResponse(
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
    val employeeAddress: AddressResponse? = null,
    val servants: List<EmployeeShortResponse>? = null
)

@Serializable
data class EmployeeServantResponse(
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
    val employeeAddress: AddressResponse? = null,
    val director: EmployeeShortResponse? = null
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

fun ResultRow.toEmployeeDirectorResponse(servants: List<EmployeeShortResponse>): EmployeeDirectorResponse {
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

    return EmployeeDirectorResponse(
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
        employeeAddress = employeeAddress,
        servants = servants
    )
}

fun ResultRow.toEmployeeServantResponse(director: EmployeeShortResponse?): EmployeeServantResponse {
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

    return EmployeeServantResponse(
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
        employeeAddress = employeeAddress,
        director = director
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

fun UpdateStatement.toEmployee(
    employeeRequest: EmployeeRequest,
    employee: ResultRow,
    employeeAddress: AddressResponse?
) {
    this[Employee.id] = employee[Employee.id]
    this[Employee.identityId] = employeeRequest.identityId ?: employee[Employee.identityId]
    this[Employee.position] = employeeRequest.position ?: employee[Employee.position]
    this[Employee.locationRestaurantId] =
        employeeRequest.locationRestaurantId ?: employee[Employee.locationRestaurantId]
    this[Employee.locationAddressId] = employeeRequest.locationAddressId ?: employee[Employee.locationAddressId]
    this[Employee.firstname] = employeeRequest.firstname ?: employee[Employee.firstname]
    this[Employee.lastname] = employeeRequest.lastname ?: employee[Employee.lastname]
    this[Employee.patronymic] = employeeRequest.patronymic ?: employee[Employee.patronymic]
    this[Employee.age] = employeeRequest.age ?: employee[Employee.age]
    this[Employee.birthday] = employee[Employee.birthday]
    this[Employee.employeeAddressId] =
        if (employeeAddress != null) employeeAddress.id as UUID else employee[Employee.employeeAddressId]
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

    suspend fun findByRestaurant(restaurantId: UUID) = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Employee.selectAll().where { Employee.locationRestaurantId eq restaurantId }
                .map { resultRow -> resultRow.toEmployeeShortResponse() }
                .toList()
        }
    }

    suspend fun findById(employeeId: UUID) = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            val locationAddress = Address.alias("locationAddress")
            val employeeAddress = Address.alias("employeeAddress")

            val employee = Employee
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
                .singleOrNull() ?: throw IllegalArgumentException("Employee not found")

            if (employee[Employee.position] == Position.DIRECTOR) {
                val servants = Employee
                    .leftJoin(DirectorServant, { Employee.id }, { DirectorServant.servantId })
                    .select(Employee.fields)
                    .where { DirectorServant.directorId eq employee[Employee.id] }
                    .map { resultRow -> resultRow.toEmployeeShortResponse() }
                    .toList()
                return@transaction employee.toEmployeeDirectorResponse(servants)

            } else {
                val director = Employee
                    .leftJoin(DirectorServant, { Employee.id }, { DirectorServant.directorId })
                    .select(Employee.fields)
                    .where { DirectorServant.servantId eq employee[Employee.id] }
                    .map { resultRow -> resultRow.toEmployeeShortResponse() }
                    .singleOrNull()
                return@transaction employee.toEmployeeServantResponse(director)
            }
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
            Employee.update({ Employee.id eq employee[Employee.id] }) { updateStatement ->
                updateStatement.toEmployee(employeeRequest, employee, employeeAddress)
            } > 0
        }
    }

    suspend fun delete(employeeId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Employee.deleteWhere { Employee.id eq employeeId } > 0
        }
    }

    suspend fun addServant(directorId: UUID, servantId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val director = Employee.selectAll().where { Employee.id eq directorId }
                .singleOrNull() ?: throw IllegalArgumentException("Director not found")
            val servant = Employee.selectAll().where { Employee.id eq servantId }
                .singleOrNull() ?: throw IllegalArgumentException("Servant not found")

            if (director[Employee.position] == Position.DIRECTOR && servant[Employee.position] != Position.DIRECTOR) {
                DirectorServant.insert { insertStatement ->
                    insertStatement[DirectorServant.directorId] = director[Employee.id]
                    insertStatement[DirectorServant.servantId] = servant[Employee.id]
                }
            } else {
                throw IllegalArgumentException("Wrong positions")
            }
        }
    }

    suspend fun removeServant(directorId: UUID, servantId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val director = Employee.selectAll().where { Employee.id eq directorId }
                .singleOrNull() ?: throw IllegalArgumentException("Director not found")
            val servant = Employee.selectAll().where { Employee.id eq servantId }
                .singleOrNull() ?: throw IllegalArgumentException("Servant not found")
            DirectorServant.deleteWhere {
                (DirectorServant.directorId eq director[Employee.id]) and (DirectorServant.servantId eq servant[Employee.id])
            } > 0
        }
    }
}

fun Application.configureEmployeeRouter() {

    val employeeService = EmployeeService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/employees/create", false) ||
                call.request.path().equals("/api/v1/employees/update", false)
            ) {
                newSuspendedTransaction {
                    val principal = call.principal<UserPasswordCredential>()
                        ?: throw IllegalArgumentException("Not authenticated")
                    val employeeRequest = call.receive(EmployeeRequest::class)

                    val identityId = employeeRequest.identityId
                        ?: throw IllegalArgumentException("Employee identityId is null")

                    val identity = Identity
                        .leftJoin(Employee, { Identity.id }, { Employee.identityId })
                        .selectAll()
                        .where { Identity.email eq principal.name }
                        .singleOrNull() ?: throw IllegalArgumentException("Identity authentication not found")

                    if (
                        (identity[Identity.id] == identityId ||
                                (identity[Employee.position] == Position.DIRECTOR ||
                                        identity[Employee.position] == Position.MANAGER)) ||
                        (identity[Identity.id] == identityId &&
                                (identity[Employee.position] == Position.DIRECTOR ||
                                        identity[Employee.position] == Position.MANAGER))
                    ) {
                        call.attributes[AttributeKey<EmployeeRequest>("employeeRequest")] = employeeRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/employees/delete", false)) {
                newSuspendedTransaction {
                    val principal = call.principal<UserPasswordCredential>()
                        ?: throw IllegalArgumentException("Not authenticated")
                    val employeeId = UUID.fromString(
                        call.parameters["employeeId"] ?: throw IllegalArgumentException("Employee id is null")
                    )
                    val identityEmployee = Identity
                        .leftJoin(Employee, { Identity.id }, { Employee.identityId })
                        .selectAll()
                        .where { Identity.email eq principal.name }
                        .singleOrNull() ?: throw IllegalArgumentException("Identity authentication not found")

                    if (
                        (identityEmployee[Employee.id] == employeeId ||
                                (identityEmployee[Employee.position] == Position.DIRECTOR ||
                                        identityEmployee[Employee.position] == Position.MANAGER)) ||
                        (identityEmployee[Employee.id] == employeeId &&
                                (identityEmployee[Employee.position] == Position.DIRECTOR ||
                                        identityEmployee[Employee.position] == Position.MANAGER))
                    ) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity employee not authorized")
                    }
                }
            } else if (
                call.request.path().equals("/api/v1/employees/add-servant", false) ||
                call.request.path().equals("/api/v1/employees/remove-servant", false)
            ) {
                newSuspendedTransaction {
                    val principal = call.principal<UserPasswordCredential>()
                        ?: throw IllegalArgumentException("Not authentication")
                    val directorId = UUID.fromString(
                        call.parameters["directorId"] ?: throw IllegalArgumentException("DirectorId is null")
                    )
                    val servantId = UUID.fromString(
                        call.parameters["servantId"] ?: throw IllegalArgumentException("ServantId is null")
                    )
                    val identityDirectorEmployee = Identity
                        .leftJoin(Employee, { Identity.id }, { Employee.identityId })
                        .leftJoin(Restaurant, { Employee.locationRestaurantId }, { Restaurant.id })
                        .selectAll()
                        .where { Employee.id eq directorId }
                        .singleOrNull() ?: throw IllegalArgumentException("Identity director not found")

                    val identityServantEmployee = Identity
                        .leftJoin(Employee, { Identity.id }, { Employee.identityId })
                        .leftJoin(Restaurant, { Employee.locationRestaurantId }, { Restaurant.id })
                        .selectAll()
                        .where { Employee.id eq servantId }
                        .singleOrNull() ?: throw IllegalArgumentException("Identity servant not found")

                    if (identityDirectorEmployee[Identity.email] == principal.name) {

                        if (
                            identityDirectorEmployee[Employee.position] == Position.DIRECTOR &&
                            identityDirectorEmployee[Restaurant.id] == identityServantEmployee[Restaurant.id]
                        )
                            proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authenticated")
                    }
                }
            }

            proceed()
        }

        route("/api/v1") {

            authenticate("basic-auth-all") {

                post("/employees/create") {
                    val employeeRequest = call.attributes[AttributeKey<EmployeeRequest>("employeeRequest")]
                    employeeService.create(employeeRequest)
                    call.respond(HttpStatusCode.Created)
                }

                get("/employees") {
                    call.respond(HttpStatusCode.OK, employeeService.findAll())
                }

                get("/employees/by-restaurant") {
                    val restaurantId = UUID.fromString(
                        call.parameters["restaurantId"] ?: throw IllegalArgumentException("Restaurant id is null")
                    )
                    call.respond(HttpStatusCode.OK, employeeService.findByRestaurant(restaurantId))
                }

                get("/employees/by-id") {
                    val employeeId = UUID.fromString(
                        call.parameters["employeeId"] ?: throw IllegalArgumentException("Employee id is null")
                    )
                    call.respond(HttpStatusCode.OK, employeeService.findById(employeeId))
                }

                put("/employees/update") {
                    val employeeRequest = call.attributes[AttributeKey<EmployeeRequest>("employeeRequest")]
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

                put("/employees/add-servant") {
                    val directorId = UUID.fromString(
                        call.parameters["directorId"] ?: throw IllegalArgumentException("Director id is null")
                    )
                    val servantId = UUID.fromString(
                        call.parameters["servantId"] ?: throw IllegalArgumentException("Servant id is null")
                    )
                    employeeService.addServant(directorId, servantId)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/employees/remove-servant") {
                    val directorId = UUID.fromString(
                        call.parameters["directorId"] ?: throw IllegalArgumentException("Director id is null")
                    )
                    val servantId = UUID.fromString(
                        call.parameters["servantId"] ?: throw IllegalArgumentException("Servant id is null")
                    )
                    if (employeeService.removeServant(directorId, servantId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}