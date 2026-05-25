package com.forfun.code_lineage.pathfinder;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExternalCallResolverTest {

    @Test
    void shouldExtractUrlPath() {
        assertThat(ExternalCallResolver.extractUrlPath(
                "restTemplate.postForObject(\"http://user-service/api/login\", ...)"))
                .isEqualTo("/api/login");

        assertThat(ExternalCallResolver.extractUrlPath(
                "webClient.get().uri(\"http://order-service/api/orders/123\")"))
                .isEqualTo("/api/orders/123");
    }

    @Test
    void shouldReturnNullForNonHttp() {
        assertThat(ExternalCallResolver.extractUrlPath("userDao.findById(id)"))
                .isNull();
    }

    @Test
    void shouldExtractDubboInterface() {
        assertThat(ExternalCallResolver.extractDubboInterface(
                "dubboService.someMethod(param1)"))
                .isEqualTo("someMethod");
    }

    @Test
    void shouldReturnNullForNonDubbo() {
        assertThat(ExternalCallResolver.extractDubboInterface(
                "restTemplate.postForObject(...)"))
                .isNull();
    }
}
