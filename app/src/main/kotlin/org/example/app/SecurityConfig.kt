package org.example.app

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain

@Profile("!local")
@Configuration
open class SecurityConfig {

    @Bean
    open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/", permitAll)
                authorize("/hello", permitAll)
                authorize("/users/**", permitAll)
                authorize("/public/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                defaultSuccessUrl("/home", true)
            }
        }
        return http.build()
    }
}