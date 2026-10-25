package com.homefinmate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 예약 작업(@Scheduled) 켜기 — 보관 기간이 지난 기록 정리(RetentionService)
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
