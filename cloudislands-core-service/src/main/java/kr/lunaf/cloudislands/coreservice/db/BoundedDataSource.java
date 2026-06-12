package kr.lunaf.cloudislands.coreservice.db;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.sql.DataSource;

public final class BoundedDataSource implements DataSource {
    private final DataSource delegate;
    private final Semaphore permits;

    public BoundedDataSource(DataSource delegate, int maxConnections) {
        this.delegate = delegate;
        this.permits = new Semaphore(Math.max(1, maxConnections), true);
    }

    @Override
    public Connection getConnection() throws SQLException {
        acquire();
        try {
            return wrap(delegate.getConnection());
        } catch (SQLException exception) {
            permits.release();
            throw exception;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        acquire();
        try {
            return wrap(delegate.getConnection(username, password));
        } catch (SQLException exception) {
            permits.release();
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

    private void acquire() throws SQLException {
        try {
            permits.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("interrupted while waiting for a database connection", exception);
        }
    }

    private Connection wrap(Connection connection) {
        AtomicBoolean closed = new AtomicBoolean();
        InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName())) {
                return close(connection, method, args, closed);
            }
            return invoke(connection, method, args);
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, handler);
    }

    private Object close(Connection connection, Method method, Object[] args, AtomicBoolean closed) throws Throwable {
        try {
            return invoke(connection, method, args);
        } finally {
            if (closed.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
