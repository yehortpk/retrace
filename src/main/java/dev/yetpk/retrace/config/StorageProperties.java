package dev.yetpk.retrace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "retrace.storage")
public record StorageProperties(String root) {
}
