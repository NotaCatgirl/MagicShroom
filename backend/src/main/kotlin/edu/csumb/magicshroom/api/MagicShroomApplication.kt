package edu.csumb.magicshroom.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.slf4j.LoggerFactory

@SpringBootApplication
class MagicShroomApplication {
    private val logger = LoggerFactory.getLogger(MagicShroomApplication::class.java)

    @Bean
    fun verifyDatabaseConnection(jdbcTemplate: JdbcTemplate) = CommandLineRunner {
        try {
            val result = jdbcTemplate.queryForObject("SELECT 1") { resultSet, _ ->
                resultSet.getInt(1)
            }

            check(result == 1) { "SELECT 1 returned $result" }
            logger.info("Supabase database connection successful: SELECT 1 returned 1")
        } catch (exception: Exception) {
            logger.error("Supabase database connection failed", exception)
            throw exception
        }
    }
}

fun main(args: Array<String>) {
	runApplication<MagicShroomApplication>(*args)
}
