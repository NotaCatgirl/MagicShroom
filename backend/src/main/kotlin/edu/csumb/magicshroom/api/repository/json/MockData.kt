package edu.csumb.magicshroom.api.repository.json

import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

// TODO(db): everything in repository/json/ is the mock-data stand-in. Implement the four repository
//  interfaces against the real database under their own profile, then change spring.profiles.default.
/** Reads `src/main/resources/mock-data/[file]`, a JSON array of [T]. */
internal inline fun <reified T> JsonMapper.readMockData(file: String): List<T> =
	ClassPathResource("mock-data/$file").inputStream.use { readValue<List<T>>(it) }
