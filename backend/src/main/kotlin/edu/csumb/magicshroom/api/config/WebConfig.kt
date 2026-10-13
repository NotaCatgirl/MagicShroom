package edu.csumb.magicshroom.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Spring MVC setup: lets controllers take a [edu.csumb.magicshroom.api.auth.CurrentUser] parameter. */
@Configuration
class WebConfig(private val currentUserResolvers: List<HandlerMethodArgumentResolver>) : WebMvcConfigurer {
	/** Registers the active profile's current-user resolver. */
	override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
		resolvers.addAll(currentUserResolvers)
	}
}
