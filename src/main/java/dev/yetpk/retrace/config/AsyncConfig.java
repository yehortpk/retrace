package dev.yetpk.retrace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async}, which the application uses for exactly one thing: stamping an API key's
 * {@code last_used_at} outside the request that authenticated with it.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
