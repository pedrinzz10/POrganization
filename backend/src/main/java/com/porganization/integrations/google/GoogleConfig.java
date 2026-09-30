package com.porganization.integrations.google;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleConfig {

    public static final String SYNC_EXECUTOR = "googleSyncExecutor";

    /** Poucas threads e fila limitada: a sincronização é em segundo plano e não pode crescer sem fim. */
    @Bean(SYNC_EXECUTOR)
    ThreadPoolTaskExecutor googleSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("google-sync-");
        executor.initialize();
        return executor;
    }

    /** Cliente HTTP das APIs do Google, com timeouts: o Google fora do ar não pode prender a API. */
    @Bean
    RestClient googleRestClient() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }
}
