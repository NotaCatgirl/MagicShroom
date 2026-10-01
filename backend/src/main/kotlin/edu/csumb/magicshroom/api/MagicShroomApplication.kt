package edu.csumb.magicshroom.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MagicShroomApplication

fun main(args: Array<String>) {
	runApplication<MagicShroomApplication>(*args)
}
