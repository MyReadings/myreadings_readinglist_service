package org.modular.playground.readinglist.web.controllers;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.modular.playground.readinglist.infrastructure.persistence.postgres.ReadingListEntity;
import static io.restassured.RestAssured.given;

// Explicit disposable database only: test schema is recreated.
@QuarkusTest
@TestProfile(ApiValidationTest.DatabaseProfile.class)
@TestSecurity(user = "demo", roles = {"user", "admin"})
@EnabledIfEnvironmentVariable(named = "DEMO_TEST_DB_URL", matches = ".+")
public class ApiValidationTest {
    public static class DatabaseProfile implements QuarkusTestProfile {
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                Map.entry("quarkus.devservices.enabled", "false"),
                Map.entry("quarkus.oidc.enabled", "false"),
                Map.entry("quarkus.otel.sdk.disabled", "true"),
                Map.entry("quarkus.http.test-port", "0"),
                Map.entry("mp.messaging.incoming.user-profile-created.enabled", "false"),
                Map.entry("quarkus.datasource.\"readinglist-db\".username", "postgres"),
                Map.entry("quarkus.datasource.\"readinglist-db\".password", "demo-test-only"),
                Map.entry("quarkus.datasource.\"readinglist-db\".jdbc.url", System.getenv().getOrDefault("DEMO_TEST_DB_URL", "jdbc:postgresql://localhost:1/demo_test")),
                Map.entry("quarkus.hibernate-orm.\"readinglist-db\".database.generation", "drop-and-create"));
        }
    }
    @Test
    void nullCreateBodyIsRejected() {
        given().contentType("application/json").body("null").post("/api/v1/readinglists").then().statusCode(400).contentType("application/json");
    }    @Test
    void invalidCollectionPagesAreRejected() {
        for (String path : new String[] {"/api/v1/readinglists","/api/v1/readinglists/00000000-0000-0000-0000-000000000001/books"}) {
            for (String query : new String[] {"page=-1", "page=2147483647", "size=0", "size=101"}) {
                given().get(path + "?" + query).then().statusCode(400);
            }
        }
    }
    @Inject @PersistenceUnit("readinglist-db") EntityManager entityManager;

    @Test
    @TestSecurity(user = "demo", roles = {"user", "admin"})
    @JwtSecurity(claims = @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000011"))
    void listPagesReturnMetadataAndExposeMoreRecords() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < 3; i++) {
                var list = new ReadingListEntity();
                list.setUserId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000011"));
                list.setName("Page " + i);
                list.setCreationDate(java.time.LocalDateTime.now());
                entityManager.persist(list);
            }
        });
        given().get("/api/v1/readinglists?page=0&size=2").then().statusCode(200)
                .header("X-Has-More", "true").body("size()", org.hamcrest.Matchers.is(2))
                .body("[0].containsKey('books')", org.hamcrest.Matchers.is(false));
        given().get("/api/v1/readinglists?page=1&size=2").then().statusCode(200)
                .header("X-Has-More", "false").body("size()", org.hamcrest.Matchers.is(1));
    }

}
