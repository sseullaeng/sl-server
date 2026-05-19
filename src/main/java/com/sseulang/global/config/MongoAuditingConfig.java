package com.sseulang.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** MongoDB Auditing — {@code @CreatedDate} 등 자동 처리. */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
