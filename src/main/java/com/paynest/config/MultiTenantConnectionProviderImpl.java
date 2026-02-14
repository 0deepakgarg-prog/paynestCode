package com.paynest.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@RequiredArgsConstructor
@Slf4j
public class MultiTenantConnectionProviderImpl implements MultiTenantConnectionProvider {

    private final DataSource dataSource;

    public Connection getConnection(String tenantIdentifier)
            throws SQLException {

        log.info("tenantIdentifier : " + tenantIdentifier);
        Connection connection = dataSource.getConnection();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO tenant_" + tenantIdentifier);
        }
        return connection;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        log.info("Calling getAnyConnection");
        return dataSource.getConnection();
    }

    public void releaseConnection(String tenantIdentifier,
                                  Connection connection)
            throws SQLException {

        connection.createStatement()
                .execute("SET search_path TO public");

        connection.close();
    }

    @Override
    public void releaseAnyConnection(Connection connection)
            throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(Object o) throws SQLException {
        log.info("tenantIdentifier in object : " + o.toString());
        Connection connection = dataSource.getConnection();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO tenant_" + o.toString());
        }
        return connection;
    }

    @Override
    public void releaseConnection(Object o, Connection connection) throws SQLException {
        connection.createStatement()
                .execute("SET search_path TO public");

        connection.close();
    }

    @PostConstruct
    public void init() {
        log.info("MultiTenantConnectionProvider loaded - MultiTenantConnectionProviderImpl");
    }
    @Override public boolean supportsAggressiveRelease() { return false; }
    @Override public boolean isUnwrappableAs(Class unwrapType) { return false; }
    @Override public <T> T unwrap(Class<T> unwrapType) { return null; }
}

