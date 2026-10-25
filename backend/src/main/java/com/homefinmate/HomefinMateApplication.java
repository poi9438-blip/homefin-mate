package com.homefinmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

// HomeFin Mate 백엔드 시작점
@SpringBootApplication
public class HomefinMateApplication {
    public static void main(String[] args) {
        // 서버 시각 기준을 UTC로 고정 (DB 기록·보관 기간·로그인 시도 제한이 같은 기준을 쓰도록)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(HomefinMateApplication.class, args);
    }
}
