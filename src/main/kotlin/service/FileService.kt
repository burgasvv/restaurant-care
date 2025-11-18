package org.burgas.service

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.burgas.File
import org.burgas.UUIDSerialization
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.time.Instant
import java.util.*

@Serializable
data class FileResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null
)

fun ResultRow.toFileResponse(): FileResponse {
    return FileResponse(
        id = this[File.id],
        name = this[File.name],
        contentType = this[File.contentType]
    )
}

fun InsertStatement<Number>.toFile(partData: PartData.FileItem) {
    this[File.name] = partData.originalFileName ?: throw IllegalArgumentException("Original file name is null")
    this[File.contentType] = partData.contentType.toString()
    this[File.data] = partData.provider.invoke().toInputStream().readAllBytes()
    this[File.createdAt] = Instant.now()
    this[File.updatedAt] = Instant.now()
}

class FileService {

    suspend fun findById(fileId: UUID) = withContext(context = Dispatchers.Default) {
        transaction(readOnly = true) {
            File.select(File.fields).where { File.id eq fileId }.singleOrNull() ?: throw IllegalArgumentException("File not found")
        }
    }

    suspend fun upload(multiPartData: MultiPartData): MutableList<UUID> = withContext(context = Dispatchers.Default) {
        val fileIds: MutableList<UUID> = mutableListOf()
        multiPartData.forEachPart { partData ->

            when(partData) {
                is PartData.FileItem -> {
                    transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                        val fileId = File.insert { insertStatement ->
                            insertStatement.toFile(partData)
                        }[File.id]
                        fileIds.add(fileId)
                    }
                }
                else -> throw IllegalArgumentException("File part data is not file item")
            }

            partData.dispose.invoke()
        }
        return@withContext fileIds
    }

    suspend fun remove(fileIds: List<UUID>) = withContext(context = Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            File.deleteWhere { File.id inList fileIds }
        }
    }
}

fun Application.configureFileRouter() {

    val fileService = FileService()

    routing {

        route("/api/v1") {

            get("/files/by-id") {
                val fileId = UUID.fromString(call.parameters["fileId"] ?: throw IllegalArgumentException("File id not found"))
                val file = fileService.findById(fileId)
                call.respondBytes(
                    file[File.data],
                    ContentType.parse("${file[File.contentType]}/${file[File.name].split(".")[1]}"),
                    HttpStatusCode.OK
                )
            }

            post("/files/upload") {
                fileService.upload(call.receiveMultipart())
                call.respond(HttpStatusCode.OK)
            }

            delete("/files/remove") {
                val fileIds = call.parameters.getAll("fileId")
                    ?.map { UUID.fromString(it) } ?: throw IllegalArgumentException("File ids not found")
                fileService.remove(fileIds)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}