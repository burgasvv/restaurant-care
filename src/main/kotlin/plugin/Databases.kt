package org.burgas.plugin

import io.ktor.server.application.*
import org.burgas.service.Authority
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime

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
    val identityId = uuid("identity_id").references(
        Identity.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE
    ).nullable()
    val fileId = uuid("file_id").references(
        File.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE
    ).nullable()
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(identityId, fileId))
}

fun Application.configureDatabases() {
    val database = Database.connect(
        driver = environment.config.property("ktor.postgres.driver").getString(),
        url = environment.config.property("ktor.postgres.url").getString(),
        user = environment.config.property("ktor.postgres.user").getString(),
        password = environment.config.property("ktor.postgres.password").getString()
    )

    transaction(db = database) {
        SchemaUtils.create(File, Identity, IdentityFile)
    }
}
