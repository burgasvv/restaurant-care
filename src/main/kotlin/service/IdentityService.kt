package org.burgas.service

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.burgas.plugin.File
import org.burgas.plugin.Identity
import org.burgas.plugin.IdentityFile
import org.burgas.plugin.UUIDSerialization
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*

@Suppress("unused")
enum class Authority {
    ADMIN, USER
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
    this[Identity.password] = BCrypt.hashpw(
        identityRequest.password ?: throw IllegalArgumentException("Password is null"), BCrypt.gensalt()
    )
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

    suspend fun findByPage(page: Int, size: Int) = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            if (size <= 0) {
                throw IllegalArgumentException("Size is equals or below zero")
            }
            val offset: Int = if (page > 1) {
                (page - 1) * size
            } else if (page <= 0) {
                throw IllegalArgumentException("Page is less or equals zero")
            }  else {
                0
            }
            Identity.select(Identity.id, Identity.username, Identity.email)
                .drop(offset)
                .take(size)
                .map { resultRow -> resultRow.toIdentityShortResponse() }
                .toList()
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(readOnly = true) {
            Identity.select(Identity.id, Identity.username, Identity.email)
                .map { resultRow -> resultRow.toIdentityShortResponse() }
                .toHashSet()
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

    suspend fun changePassword(identityId: UUID, newPassword: String) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identity = Identity.select(Identity.fields).where { Identity.id eq identityId }
                .singleOrNull() ?: throw IllegalArgumentException("Identity not null")
            if (BCrypt.checkpw(newPassword, identity[Identity.password])) {
                throw IllegalArgumentException("Passwords match")
            }
            Identity.update({ Identity.id eq identity[Identity.id] }) { updateStatement ->
                updateStatement[Identity.password] = BCrypt.hashpw(newPassword, BCrypt.gensalt())
            }
        }
    }

    suspend fun activate(identityId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identity = Identity.select(Identity.fields).where { Identity.id eq identityId }
                .singleOrNull() ?: throw IllegalArgumentException("Identity not found")
            if (identity[Identity.isActive]) {
                throw IllegalArgumentException("Identity is already active")
            }
            Identity.update({ Identity.id eq identityId }) { updateStatement ->
                updateStatement[Identity.isActive] = true
            }
        }
    }

    suspend fun deactivate(identityId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identity = Identity.select(Identity.fields).where { Identity.id eq identityId }
                .singleOrNull() ?: throw IllegalArgumentException("Identity not found")
            if (!identity[Identity.isActive]) {
                throw IllegalArgumentException("Identity is already deactivated")
            }
            Identity.update({ Identity.id eq identityId }) { updateStatement ->
                updateStatement[Identity.isActive] = false
            }
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

    suspend fun removeFiles(identityId: UUID, fileIds: List<UUID>) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            IdentityFile.deleteWhere { (IdentityFile.identityId eq identityId) and (IdentityFile.fileId inList fileIds) }
            File.deleteWhere { File.id inList fileIds }
        }
    }
}

fun Application.configureIdentityRouter() {

    val identityService = IdentityService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/identities/by-id", false) ||
                call.request.path().equals("/api/v1/identities/delete", false) ||
                call.request.path().equals("/api/v1/identities/change-password", false) ||
                call.request.path().equals("/api/v1/identities/add-files", false) ||
                call.request.path().equals("/api/v1/identities/remove-files", false)
            ) {
                newSuspendedTransaction {
                    val principal = call.principal<UserPasswordCredential>()
                        ?: throw IllegalArgumentException("Identity not authenticated")
                    val identity = Identity.select(Identity.id).where { Identity.email eq principal.name }
                        .singleOrNull() ?: throw IllegalArgumentException("Identity credential not found")
                    val identityId = UUID.fromString(
                        call.parameters["identityId"]
                            ?: throw IllegalArgumentException("Identity id parameter not found")
                    )
                    if (identity[Identity.id] == identityId) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/identities/update", false)) {
                newSuspendedTransaction {
                    val principal = call.principal<UserPasswordCredential>()
                        ?: throw IllegalArgumentException("Identity not authenticated")
                    val identity = Identity.select(Identity.id).where { Identity.email eq principal.name }
                        .singleOrNull() ?: throw IllegalArgumentException("Identity credential not found")
                    val identityRequest = call.receive(IdentityRequest::class)
                    val identityId = identityRequest.id ?: throw IllegalArgumentException("identity request id is null")
                    if (identity[Identity.id] == identityId) {
                        call.attributes[AttributeKey<IdentityRequest>("identityRequest")] = identityRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/identities") {

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.create(identityRequest)
                call.respond(HttpStatusCode.Created)
            }

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, identityService.findAll())
                }

                get("/by-page") {
                    val page = (call.parameters["page"] ?: throw IllegalArgumentException("Page parameter is null")).toInt()
                    val size = (call.parameters["size"] ?: throw IllegalArgumentException("Size parameter is null")).toInt()
                    call.respond(HttpStatusCode.OK, identityService.findByPage(page, size))
                }

                put("/activate") {
                    val identityId = UUID.fromString(
                        call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                    )
                    identityService.activate(identityId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/deactivate") {
                    val identityId = UUID.fromString(
                        call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                    )
                    identityService.deactivate(identityId)
                    call.respond(HttpStatusCode.OK)
                }
            }

            authenticate("basic-auth-all") {

                get("/by-id") {
                    val identityId = UUID.fromString(
                        call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                    )
                    call.respond(HttpStatusCode.OK, identityService.findById(identityId))
                }

                put("/update") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    if (identityService.update(identityRequest)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/delete") {
                    val identityId = UUID.fromString(
                        call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                    )
                    if (identityService.delete(identityId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                put("/change-password") {
                    val identityId = UUID.fromString(
                        call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                    )
                    val newPassword =
                        call.parameters["newPassword"] ?: throw IllegalArgumentException("New password is null")
                    identityService.changePassword(identityId, newPassword)
                    call.respond(HttpStatusCode.OK)
                }

                post("/add-files") {
                    val identityId = UUID.fromString(
                        call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                    )
                    val multiPartData = call.receiveMultipart()
                    identityService.addFiles(identityId, multiPartData)
                    call.respond(HttpStatusCode.Created)
                }

                delete("/remove-files") {
                    val identityId = UUID.fromString(
                        call.parameters["identityId"] ?: throw IllegalArgumentException("Identity id is null")
                    )
                    val fileIds = call.parameters.getAll("fileId")?.map { UUID.fromString(it) }
                        ?: throw IllegalArgumentException("File id is null")
                    identityService.removeFiles(identityId, fileIds)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}