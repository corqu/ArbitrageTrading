package corque.gimpalarm.common.config;

import corque.gimpalarm.common.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final corque.gimpalarm.user.repository.UserRepository userRepository;

    @Value("${app.security.require-ssl:false}")
    private boolean requireSsl;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(requireSsl)
                .sameSite("Lax"));
        return repository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository())
                .ignoringRequestMatchers("/ws-stomp/**")
            )
            .headers(headers -> {
                if (requireSsl) {
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .preload(true)
                            .maxAgeInSeconds(31536000));
                }
            })
            .requiresChannel(channel -> {
                if (requireSsl) {
                    channel.anyRequest().requiresSecure();
                }
            })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/**", "/ws-stomp/**").permitAll()
                .requestMatchers("/api/trading/**").authenticated()
                .requestMatchers("/api/user-bots/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userRepository), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }

    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
