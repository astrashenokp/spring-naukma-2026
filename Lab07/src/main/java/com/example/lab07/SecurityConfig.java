package com.example.lab07;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		return new InMemoryUserDetailsManager(
				User.withUsername("user").password(passwordEncoder.encode("userpass123")).roles("USER").build(),
				User.withUsername("admin").password(passwordEncoder.encode("adminpass123")).roles("ADMIN").build());
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login").permitAll()
						.requestMatchers(HttpMethod.POST, "/teas").hasRole("ADMIN")
						.anyRequest().authenticated())
				.formLogin(form -> form
						.loginPage("/login")
						.defaultSuccessUrl("/teas"))
				.build();
	}

}
