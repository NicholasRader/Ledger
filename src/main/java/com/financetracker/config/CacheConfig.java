package com.financetracker.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    // Cache name constants — use these in @Cacheable annotations
    public static final String CACHE_NET_WORTH      = "netWorth";
    public static final String CACHE_BUDGET_SUMMARY = "budgetSummary";
    public static final String CACHE_CASH_FLOW      = "cashFlow";
    public static final String CACHE_CATEGORIES     = "categories";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            CACHE_NET_WORTH,      defaultConfig.entryTtl(Duration.ofMinutes(2)),
            CACHE_BUDGET_SUMMARY, defaultConfig.entryTtl(Duration.ofMinutes(5)),
            CACHE_CASH_FLOW,      defaultConfig.entryTtl(Duration.ofMinutes(10)),
            CACHE_CATEGORIES,     defaultConfig.entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}
