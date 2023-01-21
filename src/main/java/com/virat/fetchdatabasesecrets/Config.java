package com.virat.fetchdatabasesecrets;

import static org.springframework.vault.core.lease.domain.RequestedSecret.Mode.RENEW;
import static org.springframework.vault.core.lease.domain.RequestedSecret.Mode.ROTATE;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PostConstruct;

@Configuration
public class Config {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SecretLeaseContainer leaseContainer;

    @Autowired
    private HikariDataSource hikariDataSource;

    @Value("${spring.cloud.vault.database.role}")
    private String databaseRole;

    @PostConstruct
    private void postConstruct(){
        String path = "database/creds/" + databaseRole;
        leaseContainer.addLeaseListener(event -> {
            if (!path.equals(event.getSource().getPath())) {
                return;
            }

            if (event instanceof SecretLeaseExpiredEvent && RENEW == event.getSource().getMode()) {
                logger.info("Replace RENEW for expired credential with ROTATE");
                leaseContainer.requestRotatingSecret(path);
            }

            if (event instanceof SecretLeaseCreatedEvent && ROTATE == event.getSource().getMode()) {
                Map<String, Object> secrets = ((SecretLeaseCreatedEvent) event).getSecrets();
                String username = (String) secrets.get("username");
                String password = (String) secrets.get("password");
                logger.info("Current user = {}, {}", username);
                hikariDataSource.getHikariConfigMXBean().setUsername(username);
                hikariDataSource.getHikariConfigMXBean().setPassword(password);
                logger.info("Soft evicting db connections...");
                hikariDataSource.getHikariPoolMXBean().softEvictConnections();
            }
        });
    }
    
}
