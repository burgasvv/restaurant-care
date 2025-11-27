package org.burgas.plugin

import com.ucasoft.ktor.simpleCache.SimpleCache
import com.ucasoft.ktor.simpleRedisCache.redisCache
import io.ktor.server.application.*

fun Application.configureRedis() {

    install(SimpleCache) {
        redisCache {
            host = "localhost"
            port = 6379
        }
    }
}