package com.example.knowyourcolleagues.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    @Test
    void shouldRegisterOptimisticLockBeforePagination() {
        MybatisPlusInterceptor interceptor =
                new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasSize(2)
                .element(0)
                .isInstanceOf(OptimisticLockerInnerInterceptor.class);
        assertThat(interceptor.getInterceptors())
                .element(1)
                .isInstanceOf(PaginationInnerInterceptor.class);
    }
}
