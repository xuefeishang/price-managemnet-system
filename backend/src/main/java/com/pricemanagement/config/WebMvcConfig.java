package com.pricemanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 Java 8 日期时间模块
        mapper.registerModule(new JavaTimeModule());
        // 将 LocalDate 序列化为 ISO-8601 字符串而不是数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Build absolute path to resources/static/logo directory
        String baseDir = System.getProperty("user.dir");
        String logoPath = Paths.get(baseDir, "src", "main", "resources", "static", "logo")
                .toUri().toString();
        registry.addResourceHandler("/api/static/logo/**")
                .addResourceLocations(logoPath);
    }
}
