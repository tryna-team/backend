package com.tryna.global.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

// 쓰기와 읽기 DB로 물리적 DataSource를 각각 생성하고,
// RoutingDataSource로 감싸 트랜잭션의 readOnly 여부에 따라 자동으로
// 쓰기 연산은 WRITE로, 읽기 연산은 READ로 라우팅되도록 구성한다.
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    @Bean
    @Qualifier("write")
    @ConfigurationProperties("spring.datasource.write")
    public DataSourceProperties writeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Qualifier("write")
    @ConfigurationProperties("spring.datasource.write.hikari")
    public DataSource writeDataSource(@Qualifier("write") DataSourceProperties writeDataSourceProperties) {
        return writeDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Qualifier("read")
    @ConfigurationProperties("spring.datasource.read")
    public DataSourceProperties readDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Qualifier("read")
    @ConfigurationProperties("spring.datasource.read.hikari")
    public DataSource readDataSource(@Qualifier("read") DataSourceProperties readDataSourceProperties) {
        return readDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Qualifier("routing")
    public DataSource routingDataSource(@Qualifier("write") DataSource writeDataSource,
                                         @Qualifier("read") DataSource readDataSource) {
        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> dataSources = new HashMap<>();
        dataSources.put(DataSourceType.WRITE, writeDataSource);
        dataSources.put(DataSourceType.READ, readDataSource);

        routingDataSource.setTargetDataSources(dataSources);
        // 라우팅 키를 판단할 수 없는 예외 상황(트랜잭션 없음 등)에는 주 DB로 안전하게 보낸다.
        routingDataSource.setDefaultTargetDataSource(writeDataSource);

        return routingDataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("routing") DataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
