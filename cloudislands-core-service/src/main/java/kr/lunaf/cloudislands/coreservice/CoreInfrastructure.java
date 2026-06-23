package kr.lunaf.cloudislands.coreservice;

import javax.sql.DataSource;
import kr.lunaf.cloudislands.coreservice.cache.RedisCacheAdmin;
import kr.lunaf.cloudislands.coreservice.db.MeteredDataSource;
import kr.lunaf.cloudislands.coreservice.event.RedisStreamEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRouteRegistrar;
import kr.lunaf.cloudislands.coreservice.redis.RedisStreamWriterAdapter;
import kr.lunaf.cloudislands.coreservice.security.CoreApiAuthGuard;

public record CoreInfrastructure(
    CoreApiAuthGuard authGuard,
    CoreHttpRouteRegistrar routeRegistrar,
    CoreHttpRouteRegistrar adminRouteRegistrar,
    MeteredDataSource meteredDataSource,
    DataSource dataSource,
    boolean coreJdbcActive,
    RedisStreamWriterAdapter redisEventWriter,
    RedisStreamEventPublisher redisEventPublisher,
    RedisCacheAdmin redisCacheAdmin,
    RedisActivationLock activationLock,
    RedisPlayerCreationLock playerCreationLock
) {}
