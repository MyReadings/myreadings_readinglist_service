package org.modular.playground.readinglist.web.controllers;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;
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
    }
}
