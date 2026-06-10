package com.pricemanagement.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTests {

    @Test
    void masksNamedAndUrlSecrets() {
        String masked = SensitiveDataMasker.mask(
                "NotificationChannelConfigUpdateRequest(secret=plain-secret, endpointUrl=https://example.test?access_token=plain-token)");

        assertThat(masked)
                .doesNotContain("plain-secret")
                .doesNotContain("plain-token")
                .contains("secret=******")
                .contains("access_token=******");
    }
}
