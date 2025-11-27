package org.burgas

import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burgas.plugin.*
import org.burgas.service.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.time.LocalDateTime

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configurePostgres()
    configureRouting()
    configureAuthentication()
    configureRedis()

    configureFileRouter()
    configureIdentityRouter()
    configureEmployeeRouter()
    configureRestaurantRouter()
    configureLocationRouter()
    configureReservationRouter()

    launch {
        while (true) {
            delay(10000)

            transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                val reservations = Reservation.selectAll()
                    .where {
                        (Reservation.isFinished eq false) and (Reservation.isStarted eq false) and
                                (Reservation.startTime.less(LocalDateTime.now().minusMinutes(10)))
                    }
                    .toList()
                if (!reservations.isEmpty()) {
                    reservations.forEach { reservation ->
                        Reservation.update({ Reservation.id eq reservation[Reservation.id] }) { updateStatement ->
                            updateStatement[Reservation.isFinished] = true
                        }
                    }
                }
            }
        }
    }
}
