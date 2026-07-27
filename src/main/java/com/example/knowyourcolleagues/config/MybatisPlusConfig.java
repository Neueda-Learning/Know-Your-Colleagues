package com.example.knowyourcolleagues.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 */
@Configuration
public class MybatisPlusConfig {

    private static final long MAX_PAGE_SIZE = 100L;

    /**
     * 配置 MySQL 物理分页插件。
     *
     * <p>分页插件会为查询生成 COUNT 和 LIMIT SQL，避免将全部数据加载到内存。</p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        PaginationInnerInterceptor paginationInterceptor =
                new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setOverflow(false);
        paginationInterceptor.setMaxLimit(MAX_PAGE_SIZE);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
