package edu.csumb.magicshroom.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import javax.sql.DataSource
import kotlin.test.assertEquals

@SpringBootTest
class MagicShroomApplicationTests(@Autowired private val dataSource: DataSource) {

	@Test
	fun contextLoads() {
	}

	@Test
	fun `uses H2 during tests`() {
		dataSource.connection.use { connection ->
			assertEquals("H2", connection.metaData.databaseProductName)
		}
	}
}
