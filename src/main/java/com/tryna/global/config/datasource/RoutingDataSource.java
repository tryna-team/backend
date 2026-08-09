package com.tryna.global.config.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 현재 트랜잭션이 읽기 전용(readOnly = true)인지에 따라
// 실제 커넥션을 가져올 DataSource를 결정한다.
// 트랜잭션이 없거나 읽기 전용이 아니면 안전하게 WRITE로 라우팅한다.
@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        DataSourceType type = readOnly ? DataSourceType.READ : DataSourceType.WRITE;
        log.debug("[DataSource Routing] readOnly={} -> {}", readOnly, type);
        return type;
    }
}
