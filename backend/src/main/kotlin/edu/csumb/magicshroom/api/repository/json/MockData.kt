package edu.csumb.magicshroom.api.repository.json

import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/** Reads `src/main/resources/mock-data/[file]`, a JSON array of [T]. */
internal inline fun <reified T> JsonMapper.readMockData(file: String): List<T> =
	ClassPathResource("mock-data/$file").inputStream.use { readValue<List<T>>(it) }
