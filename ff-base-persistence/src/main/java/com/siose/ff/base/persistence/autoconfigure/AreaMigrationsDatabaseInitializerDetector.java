package com.siose.ff.base.persistence.autoconfigure;

import com.siose.ff.base.persistence.AreaMigrations;
import java.util.Set;
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;

/**
 * Tells Spring Boot that {@link AreaMigrations} initializes the database, so that the beans that
 * use it (JPA's EntityManagerFactory, JdbcTemplate) are created after the migrations ran.
 * Registered in {@code META-INF/spring.factories}.
 */
public class AreaMigrationsDatabaseInitializerDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

    @Override
    protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
        return Set.of(AreaMigrations.class);
    }
}
