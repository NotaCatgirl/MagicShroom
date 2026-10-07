package edu.csumb.magicshroom.api.config

import edu.csumb.magicshroom.api.auth.StubCurrentUserResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Spring MVC setup: lets controllers take a [edu.csumb.magicshroom.api.auth.CurrentUser] parameter. */
@Configuration
class WebConfig(private val currentUserResolver: StubCurrentUserResolver) : WebMvcConfigurer {
	override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
		resolvers.add(currentUserResolver)
	}
}
