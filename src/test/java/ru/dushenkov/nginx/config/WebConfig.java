package ru.dushenkov.nginx.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@TestConfiguration
public class WebConfig {



    @Bean
    public WebClient defaultHttpWebClient() {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
                .responseTimeout(Duration.ofMillis(500));

        return WebClient
                .builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))

                .build();
    }

}
