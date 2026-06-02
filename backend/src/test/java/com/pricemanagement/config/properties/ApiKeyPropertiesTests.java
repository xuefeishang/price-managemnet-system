package com.pricemanagement.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyPropertiesTests {

    @Test
    void shouldRequireBase64EncodedThirtyTwoByteKeyWhenEnabled() {
        ApiKeyProperties properties = new ApiKeyProperties(new MockEnvironment());
        properties.setEnabled(true);
        properties.setEncryptionKey("not-base64!");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void shouldRequireDecodedThirtyTwoByteKeyWhenEnabled() {
        ApiKeyProperties properties = new ApiKeyProperties(new MockEnvironment());
        properties.setEnabled(true);
        properties.setEncryptionKey("aW52YWxpZA==");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32字节");
    }

    @Test
    void shouldPassValidationWithValidDevelopmentKey() {
        ApiKeyProperties properties = new ApiKeyProperties(new MockEnvironment());
        properties.setEnabled(true);
        properties.setEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectKnownDevelopmentKeyInProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ApiKeyProperties properties = new ApiKeyProperties(environment);
        properties.setEnabled(true);
        properties.setEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境");
    }
}
