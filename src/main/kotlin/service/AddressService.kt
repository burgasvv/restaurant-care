package org.burgas.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.burgas.plugin.Address
import org.burgas.plugin.UUIDSerialization
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
import java.util.*

@Serializable
data class AddressRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val city: String? = null,
    val street: String? = null,
    val house: String? = null,
    val apartment: String? = null
)

@Serializable
data class AddressResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val city: String? = null,
    val street: String? = null,
    val house: String? = null,
    val apartment: String? = null
)

fun ResultRow.toAddressResponse(): AddressResponse {
    return AddressResponse(
        id = this[Address.id],
        city = this[Address.city],
        street = this[Address.street],
        house = this[Address.house],
        apartment = this[Address.apartment]
    )
}

fun InsertStatement<Number>.toAddress(addressRequest: AddressRequest) {
    this[Address.city] = addressRequest.city ?: throw IllegalArgumentException("Address city is null")
    this[Address.street] = addressRequest.street ?: throw IllegalArgumentException("Address street is null")
    this[Address.house] = addressRequest.house ?: throw IllegalArgumentException("Address house is null")
    this[Address.apartment] = addressRequest.apartment
}

fun UpdateStatement.toAddress(addressRequest: AddressRequest, address: ResultRow) {
    this[Address.id] = address[Address.id]
    this[Address.city] = addressRequest.city ?: address[Address.city]
    this[Address.street] = addressRequest.street ?: address[Address.street]
    this[Address.house] = addressRequest.house ?: address[Address.house]
    this[Address.apartment] = addressRequest.apartment ?: address[Address.apartment]
}

class AddressService {

    suspend fun create(addressRequest: AddressRequest): UUID = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Address.insert { insertStatement ->
                insertStatement.toAddress(addressRequest)
            }[Address.id]
        }
    }

    suspend fun update(addressRequest: AddressRequest): AddressResponse? = withContext(Dispatchers.Default) {
        val addressId = addressRequest.id ?: throw IllegalArgumentException("Address request id is null")
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val address = Address.select(Address.fields).where { Address.id eq addressId }
                .singleOrNull() ?: throw IllegalArgumentException("Address not found")
            Address.update({ Address.id eq address[Address.id] }) { updateStatement ->
                updateStatement.toAddress(addressRequest, address)
            }
            Address.select(Address.fields).where { Address.id eq address[Address.id] }
                .map { resultRow -> resultRow.toAddressResponse() }
                .singleOrNull()
        }
    }

    suspend fun delete(addressId: UUID) = withContext(Dispatchers.Default) {
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Address.deleteWhere { Address.id eq addressId }
        }
    }
}