package org.burgas.plugin

import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.burgas.service.Authority
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

fun Application.configureAuthentication() {

    authentication {

        basic(name = "basic-auth-all") {
            validate { credentials -> transaction(readOnly = true) {
                    val identity = Identity.select(Identity.fields).where { (Identity.email eq credentials.name) }
                        .singleOrNull()
                    if (
                        identity != null &&
                        BCrypt.checkpw(credentials.password, identity[Identity.password]) &&
                        identity[Identity.isActive]
                    ) {
                        UserPasswordCredential(credentials.name, credentials.password)

                    } else {
                        null
                    }
                }
            }
        }

        basic(name = "basic-auth-admin") {
            validate { credentials -> transaction(readOnly = true) {
                    val identity = Identity.select(Identity.fields).where { (Identity.email eq credentials.name) }
                        .singleOrNull()
                    if (
                        identity != null &&
                        BCrypt.checkpw(credentials.password, identity[Identity.password]) &&
                        identity[Identity.authority] == Authority.ADMIN &&
                        identity[Identity.isActive]
                    ) {
                        UserPasswordCredential(credentials.name, credentials.password)

                    } else {
                        null
                    }
                }
            }
        }
    }
}