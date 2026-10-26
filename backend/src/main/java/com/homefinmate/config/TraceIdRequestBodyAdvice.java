package com.homefinmate.config;

import com.homefinmate.common.TraceIdCarrier;
import com.homefinmate.common.TraceIds;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

// 요청 본문의 traceId를 헤더 기준 traceId로 덮어씀 (본문에 다른 값을 보내도 무시)
@ControllerAdvice
public class TraceIdRequestBodyAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return TraceIdCarrier.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        ((TraceIdCarrier) body).setTraceId(TraceIds.current());
        return body;
    }
}
