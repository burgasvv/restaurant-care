package org.burgas.plugin

import io.ktor.server.application.*
import org.burgas.service.Authority
import org.burgas.service.Position
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

object File : Table("file") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255)
    val contentType = varchar("content_type", 255)
    val data = binary("data")

    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object Identity : Table("identity") {
    val id = uuid("id").autoGenerate()
    val authority = enumerationByName("authority",15, Authority::class)
    val username = varchar("username", 255).uniqueIndex()
    val password = varchar("password", 255)
    val email = varchar("email", 255).uniqueIndex()
    val isActive = bool("is_active")

    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object IdentityFile : Table("identity_file") {
    val identityId = uuid("identity_id")
        .references(Identity.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
        .nullable()
    val fileId = uuid("file_id")
        .references(File.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
        .nullable()
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(identityId, fileId))
}

object Address : Table("address") {
    val id = uuid("id").autoGenerate()
    val city = varchar("city", 255)
    val street = varchar("street", 255)
    val house = varchar("house", 255)
    val apartment = varchar("apartment", 255).nullable()
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object Restaurant : Table("restaurant") {
    val id  = uuid("id").autoGenerate()
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description").uniqueIndex()
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object Location : Table("location") {
    val restaurantId = uuid("restaurant_id")
        .references(Restaurant.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val addressId = uuid("address_id")
        .references(Address.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val open = time("open").nullable()
    val close = time("close").nullable()
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(restaurantId, addressId))
}

object Employee : Table("employee") {
    val id = uuid("id").autoGenerate()
    val identityId = uuid("identity_id")
        .references(Identity.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
        .uniqueIndex()
    val position = enumerationByName("position", 255, Position::class)
    val locationRestaurantId = uuid("location_restaurant_id").nullable()
    val locationAddressId = uuid("location_address_id").nullable()
    val firstname = varchar("firstname", 255)
    val lastname = varchar("lastname", 255)
    val patronymic = varchar("patronymic", 255)
    val age = integer("age")
    val birthday = date("birthday")
    val employeeAddressId = uuid("employee_address_id")
        .references(Address.id, ReferenceOption.SET_NULL, ReferenceOption.CASCADE)
    init {
        foreignKey(locationRestaurantId, locationAddressId, target = Location.primaryKey)
    }
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object DirectorServant : Table("director_servant") {
    val directorId = uuid("director_id")
        .references(Employee.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val servantId = uuid("servant_id")
        .references(Employee.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(directorId, servantId))
}

fun Application.configureDatabases() {
    val database = Database.connect(
        driver = environment.config.property("ktor.postgres.driver").getString(),
        url = environment.config.property("ktor.postgres.url").getString(),
        user = environment.config.property("ktor.postgres.user").getString(),
        password = environment.config.property("ktor.postgres.password").getString()
    )

    transaction(db = database) {
        SchemaUtils.create(File, Identity, IdentityFile, Address, Restaurant, Location, Employee, DirectorServant)
    }
}
