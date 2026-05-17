package io.github.ydhekim.crimson_sky.server.database;

import com.badlogic.gdx.utils.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ReflectionMappers;
import org.jdbi.v3.core.mapper.reflect.SnakeCaseColumnNameMatcher;
import org.jdbi.v3.jackson2.Jackson2Config;
import org.jdbi.v3.jackson2.Jackson2Plugin;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.util.List;

public class DatabaseManager {
    private static final Logger log = new Logger("DatabaseManager", Logger.DEBUG);
    private static DatabaseManager instance;
    private HikariDataSource dataSource;
    private Flyway flyway;
    private Jdbi jdbi;

    private DatabaseManager() {
        init();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void init() {
        log.info("Initializing Database connection pool...");
        HikariConfig config = new HikariConfig();
        // Replace with your actual DB credentials or use environment variables
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/crimsonsky");
        config.setUsername("postgres");
        config.setPassword("postgres");

        // Recommended settings for HikariCP
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(10000);

        try {
            this.dataSource = new HikariDataSource(config);
            log.info("Database connection pool initialized successfully.");

            log.info("Starting Flyway database migrations...");
            this.flyway = Flyway.configure()
                .dataSource(this.dataSource)
                .locations("classpath:db/migration")
                .load();
            flyway.migrate();
            log.info("Flyway database migrations completed successfully.");

            this.jdbi = Jdbi.create(dataSource)
                .installPlugin(new SqlObjectPlugin())
                .installPlugin(new Jackson2Plugin())
                .installPlugin(new PostgresPlugin());

            ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule())
                .registerModule(new ParameterNamesModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            this.jdbi.getConfig(Jackson2Config.class).setMapper(objectMapper);
            this.jdbi.getConfig(ReflectionMappers.class).setColumnNameMatchers(List.of(new SnakeCaseColumnNameMatcher()));
            log.info("JDBI initialized successfully with Postgres and Jackson plugins.");
        } catch (Exception e) {
            log.error("Failed to initialize database or execute migrations.", e);
            throw e;
        }
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            log.info("Closing database connection pool...");
            dataSource.close();
            log.info("Database connection pool closed.");
        }
    }
}
