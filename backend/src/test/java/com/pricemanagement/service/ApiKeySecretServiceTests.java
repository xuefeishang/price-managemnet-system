package com.pricemanagement.service;

import com.pricemanagement.config.properties.ApiKeyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeySecretServiceTests {

    @Test
    void shouldEncryptAndDecryptSecretWithoutPlainTextStorage() {
        ApiKeyProperties properties = new ApiKeyProperties(new MockEnvironment());
        properties.setEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        ApiKeySecretService service = new ApiKeySecretService(properties);

        String secret = "sec_test_1234567890";
        String cipherText = service.encrypt(secret);

        assertThat(cipherText).isNotEqualTo(secret);
        assertThat(service.decrypt(cipherText)).isEqualTo(secret);
        assertThat(service.fingerprint(secret)).hasSize(64);
    }
}
