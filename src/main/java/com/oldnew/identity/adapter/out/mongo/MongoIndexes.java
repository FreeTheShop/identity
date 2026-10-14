package com.oldnew.identity.adapter.out.mongo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Creates indexes explicitly at startup (auto-index-creation is off), so every index is reviewed in code.
 * Creating an index that already exists with the same definition is a no-op.
 */
@Component
class MongoIndexes implements ApplicationRunner {

    private final MongoTemplate mongo;

    MongoIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongo.indexOps(UserDocument.class)
                .createIndex(new Index().on("email", Sort.Direction.ASC).unique().named("ux_users_email"));
    }
}
