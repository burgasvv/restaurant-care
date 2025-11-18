package org.burgas.service

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.burgas.File
import org.burgas.Identity
import org.burgas.IdentityFile
import org.burgas.UUIDSerialization
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*

@Suppress("unused")
enum class Authority {
    ADMIN, USER, DIRECTOR, EMPLOYEE
}

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val isActive: Boolean? = null
)

@Serializable
data class IdentityShortResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val username: String? = null,
    val email: String? = null
)

@Serializable
data class IdentityFullResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val files: List<FileResponse>? = null
)

fun ResultRow.toIdentityShortResponse(): IdentityShortResponse {
    return IdentityShortResponse(
        id = this[Identity.id],
        username = this[Identity.username],
        email = this[Identity.email]
    )
}

fun ResultRow.toIdentityFullResponse(files: List<FileResponse>): IdentityFullResponse {
    return IdentityFullResponse(
        id = this[Identity.id],
        username = this[Identity.username],
        email = this[Identity.email],
        files = files
    )
}

fun InsertStatement<Number>.toIdentity(identityRequest: IdentityRequest) {
    this[Identity.authority] = identityRequest.authority ?: throw IllegalArgumentException("Authority is null")
    this[Identity.username] = identityRequest.username ?: throw IllegalArgumentException("Username is null")
    this[Identity.password] = identityRequest.password ?: throw IllegalArgumentException("Password is null")
    this[Identity.email] = identityRequest.email ?: throw IllegalArgumentException("Email is null")
    this[Identity.isActive] = identityRequest.isActive ?: throw IllegalArgumentException("IsActive is null")
    this[Identity.createdAt] = LocalDateTime.now()
    this[Identity.updatedAt] = LocalDateTime.now()
}

fun UpdateStatement.toIdentity(identityRequest: IdentityRequest, identity: ResultRow) {
    this[Identity.id] = identity[Identity.id]
    this[Identity.authority] = identityRequest.authority ?: identity[Identity.authority]
    this[Identity.username] = identityRequest.username ?: identity[Identity.username]
    this[Identity.password] = identity[Identity.password]
    this[Identity.email] = identityRequest.email ?: identity[Identity.email]
    this[Identity.isActive] = identityRequest.isActive ?: identity[Identity.isActive]
    this[Identity.createdAt] = identity[Identity.createdAt]
    this[Identity.updatedAt] = LocalDateTime.now()
}

class IdentityService {

    val fileService = FileService()

    suspend fun create(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Identity.insert { insertStatement ->
                insertStatement.toIdentity(identityRequest)
            }
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Identity.select(Identity.id, Identity.username, Identity.email)
                .map { resultRow ->
                    resultRow.toIdentityShortResponse()
                }
        }
    }

    suspend fun findById(identityId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val files = (File leftJoin IdentityFile)
                .select(File.id, File.name, File.contentType)
                .where { IdentityFile.identityId eq identityId }
                .map { resultRow -> resultRow.toFileResponse() }
                .toList()
            Identity.select(Identity.fields).where { Identity.id eq identityId }
                .map { resultRow -> resultRow.toIdentityFullResponse(files) }
                .singleOrNull() ?: throw IllegalArgumentException("Identity not found")
        }
    }

    suspend fun update(identityRequest: IdentityRequest): Boolean = withContext(Dispatchers.Default) {
        val identityId = identityRequest.id ?: throw IllegalArgumentException("Identity request id is null")
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identity = Identity.select(Identity.fields).where { Identity.id eq identityId }
                .singleOrNull() ?: throw IllegalArgumentException("Identity is null")
            Identity.update({ Identity.id eq identity[Identity.id] }) { updateStatement ->
                updateStatement.toIdentity(identityRequest, identity)
            } > 0
        }
    }

    suspend fun delete(identityId: UUID): Boolean = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Identity.deleteWhere { Identity.id eq identityId } > 0
        }
    }

    suspend fun addFiles(identityId: UUID, multiPartData: MultiPartData) = withContext(Dispatchers.Default) {
        val fileIds = fileService.upload(multiPartData)
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identity = Identity.select(Identity.id).where { Identity.id eq identityId }
                .singleOrNull() ?: throw IllegalArgumentException("Identity not found")
            fileIds.forEach { fileId ->
                IdentityFile.insert { insertStatement ->
                    insertStatement[IdentityFile.identityId] = identity[Identity.id]
                    insertStatement[IdentityFile.fileId] = fileId
                }
            }
        }
    }
}

fun Application.configureIdentityRouter() {

    val identityService = IdentityService()

    routing {

        route("/api/v1") {

            post("/identities/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.create(identityRequest)
                call.respond(HttpStatusCode.Created)
            }

            get("/identities") {
                call.respond(HttpStatusCode.OK, identityService.findAll())
            }

            get("/identities/by-id") {
                val identityId = UUID.fromString(
                    call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                )
                call.respond(HttpStatusCode.OK, identityService.findById(identityId))
            }

            put("/identities/update") {
                val identityRequest = call.receive(IdentityRequest::class)
                if (identityService.update(identityRequest)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            delete("/identities/delete") {
                val identityId = UUID.fromString(
                    call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                )
                if (identityService.delete(identityId)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            post("/identities/add-files") {
                val identityId = UUID.fromString(
                    call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                )
                val multiPartData = call.receiveMultipart()
                identityService.addFiles(identityId, multiPartData)
                call.respond(HttpStatusCode.Created)
            }
        }
    }
}