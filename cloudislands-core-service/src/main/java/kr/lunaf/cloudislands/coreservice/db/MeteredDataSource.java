package kr.lunaf.cloudislands.coreservice.db;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import javax.sql.DataSource;

public final class MeteredDataSource implements DataSource {
    private final DataSource delegate;
    private volatile double lastQuerySeconds;
    private final AtomicLong connectionFailures = new AtomicLong();
    private final AtomicLong queryFailures = new AtomicLong();

    public MeteredDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    public double lastQuerySeconds() {
        return lastQuerySeconds;
    }

    public long queryFailures() {
        return queryFailures.get();
    }

    public long connectionFailures() {
        return connectionFailures.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            return wrapConnection(delegate.getConnection());
        } catch (SQLException exception) {
            connectionFailures.incrementAndGet();
            throw exception;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        try {
            return wrapConnection(delegate.getConnection(username, password));
        } catch (SQLException exception) {
            connectionFailures.incrementAndGet();
            throw exception;
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    private Connection wrapConnection(Connection connection) {
        InvocationHandler handler = (proxy, method, args) -> {
            Object result = invoke(connection, method, args);
            if (result instanceof CallableStatement callableStatement) {
                return wrapStatement(callableStatement, CallableStatement.class);
            }
            if (result instanceof PreparedStatement preparedStatement) {
                return wrapStatement(preparedStatement, PreparedStatement.class);
            }
            if (result instanceof Statement statement) {
                return wrapStatement(statement, Statement.class);
            }
            return result;
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, handler);
    }

    private Object wrapStatement(Statement statement, Class<?> statementInterface) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (!isExecutionMethod(method)) {
                return invoke(statement, method, args);
            }
            long started = System.nanoTime();
            try {
                return invoke(statement, method, args);
            } catch (Throwable exception) {
                queryFailures.incrementAndGet();
                throw exception;
            } finally {
                lastQuerySeconds = (System.nanoTime() - started) / 1_000_000_000.0D;
            }
        };
        return Proxy.newProxyInstance(statementInterface.getClassLoader(), new Class<?>[] { statementInterface }, handler);
    }

    private static boolean isExecutionMethod(Method method) {
        String name = method.getName();
        return "execute".equals(name)
            || "executeQuery".equals(name)
            || "executeUpdate".equals(name)
            || "executeLargeUpdate".equals(name)
            || "executeBatch".equals(name)
            || "executeLargeBatch".equals(name);
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
