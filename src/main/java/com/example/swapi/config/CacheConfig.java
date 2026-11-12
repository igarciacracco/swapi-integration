package com.example.swapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process Caffeine cache in front of SWAPI. The data is effectively static, so a 1 h TTL is
 * safe. It is bounded (the container has 512 MB) and per instance; sharing it would need Redis.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    CacheManager cacheManager(CacheProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.timeToLive())
                // Used by the cache tests; also exposes hit rates in production.
                .recordStats());
        return manager;
    }
}
