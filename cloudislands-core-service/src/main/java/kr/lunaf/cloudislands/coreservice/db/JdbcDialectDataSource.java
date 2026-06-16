package kr.lunaf.cloudislands.coreservice.db;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

public final class JdbcDialectDataSource implements DataSource {
    private final DataSource delegate;

    public JdbcDialectDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(delegate.getConnection(username, password));
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

    private Connection wrapConnection(Connection connection) throws SQLException {
        if (!mysqlLike(connection)) {
            return connection;
        }
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
            if ("setObject".equals(method.getName()) && args != null && args.length >= 2 && args[1] instanceof UUID uuid) {
                return setUuid(statement, args, uuid);
            }
            Object result = invoke(statement, method, args);
            if (result instanceof ResultSet resultSet) {
                return wrapResultSet(resultSet);
            }
            return result;
        };
        return Proxy.newProxyInstance(statementInterface.getClassLoader(), new Class<?>[] { statementInterface }, handler);
    }

    private Object setUuid(Statement statement, Object[] args, UUID uuid) throws SQLException {
        if (!(statement instanceof PreparedStatement preparedStatement) || !(args[0] instanceof Integer index)) {
            return null;
        }
        if (args.length == 2) {
            preparedStatement.setString(index, uuid.toString());
            return null;
        }
        if (args.length == 3 && args[2] instanceof Integer sqlType) {
            preparedStatement.setObject(index, uuid.toString(), sqlType);
            return null;
        }
        if (args.length == 4 && args[2] instanceof Integer sqlType && args[3] instanceof Integer scaleOrLength) {
            preparedStatement.setObject(index, uuid.toString(), sqlType, scaleOrLength);
            return null;
        }
        preparedStatement.setString(index, uuid.toString());
        return null;
    }

    private ResultSet wrapResultSet(ResultSet resultSet) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getObject".equals(method.getName()) && args != null && args.length == 2 && args[1] == UUID.class) {
                Object raw = args[0] instanceof Integer index ? resultSet.getObject(index) : resultSet.getObject(String.valueOf(args[0]));
                return toUuid(raw);
            }
            Object result = invoke(resultSet, method, args);
            if ("getObject".equals(method.getName())) {
                Object uuid = toUuid(result);
                return uuid == null ? result : uuid;
            }
            return result;
        };
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class }, handler);
    }

    private Object toUuid(Object value) {
        if (value instanceof UUID) {
            return value;
        }
        if (value instanceof String text && uuidText(text)) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return null;
    }

    private boolean uuidText(String value) {
        return value.length() == 36
            && value.charAt(8) == '-'
            && value.charAt(13) == '-'
            && value.charAt(18) == '-'
            && value.charAt(23) == '-';
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        return productName.contains("mysql") || productName.contains("mariadb");
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
