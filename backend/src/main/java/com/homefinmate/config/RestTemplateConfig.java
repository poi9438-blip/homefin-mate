package com.homefinmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

// 외부 서버 호출용 RestTemplate (연결 5초, 응답 10초 타임아웃)
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 연결 타임아웃 5초
        factory.setConnectTimeout(5_000);
        // 응답 타임아웃 10초
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }
}
