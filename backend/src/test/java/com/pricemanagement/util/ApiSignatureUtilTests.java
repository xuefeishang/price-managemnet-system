package com.pricemanagement.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSignatureUtilTests {

    @Test
    void shouldBuildCanonicalQueryWithSortedKeys() {
        assertThat(ApiSignatureUtil.canonicalQuery("size=20&page=0"))
                .isEqualTo("page=0&size=20");
    }

    @Test
    void shouldGenerateDocumentedSignatureVector() {
        String canonical = String.join("\n",
                "GET",
                "/api/external/v1/products",
                "page=0&size=20",
                "1779990000",
                "nonce_test_001",
                ApiSignatureUtil.EMPTY_BODY_SHA256);

        String signature = ApiSignatureUtil.hmacSha256Hex("sec_test_1234567890", canonical);

        assertThat(signature).isEqualTo("7221cc7d6fc7d2f6cde1c20d7cdf62aa9669e0c965fe9e91902efac24d4e37cf");
    }
}
