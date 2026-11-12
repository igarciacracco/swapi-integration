package com.example.swapi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/** The HTTP client for SWAPI. Both timeouts are explicit because the JDK default waits forever. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SwapiProperties.class)
public class RestClientConfig {

    @Bean
    RestClient swapiRestClient(SwapiProperties properties) {
        // RestClient.builder() rather than the auto-configured builder, which Boot 4 moved to a
        // separate module.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", properties.userAgent())
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
