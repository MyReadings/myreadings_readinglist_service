package org.modular.playground.readinglist.infrastructure.persistence.postgres;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// Run only against an explicitly supplied disposable database: schema is recreated.
@QuarkusTest
@TestProfile(JpaAddBookIntegrationTest.DatabaseProfile.class)
@EnabledIfEnvironmentVariable(named = "F12_TEST_DB_URL", matches = ".+")
public class JpaAddBookIntegrationTest {
    public static class DatabaseProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("quarkus.devservices.enabled", "false"),
                    Map.entry("quarkus.oidc.enabled", "false"),
                    Map.entry("quarkus.otel.sdk.disabled", "true"),
                    Map.entry("quarkus.http.test-port", "0"),
                    Map.entry("mp.messaging.incoming.user-profile-created.enabled", "false"),
                    Map.entry("quarkus.datasource.\"readinglist-db\".username", "postgres"),
                    Map.entry("quarkus.datasource.\"readinglist-db\".password", System.getenv().getOrDefault("DEMO_TEST_DB_PASSWORD", "f12-test-only")),
                    Map.entry("quarkus.datasource.\"readinglist-db\".jdbc.url",
                            System.getenv().getOrDefault("F12_TEST_DB_URL", "jdbc:postgresql://localhost:1/f12_test")),
                    Map.entry("quarkus.hibernate-orm.\"readinglist-db\".database.generation", "drop-and-create"));
        }
    }

    @Inject JpaReadingListRepository repository;
    @Inject @PersistenceUnit("readinglist-db") EntityManager entityManager;

    UUID listId;
    UUID ownerId;

    @BeforeEach
    void createList() {
        ownerId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("DELETE FROM reading_list_items").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM reading_lists").executeUpdate();
            var list = new ReadingListEntity();
            list.setUserId(ownerId);
            list.setName("Test list");
            list.setCreationDate(LocalDateTime.now());
            entityManager.persist(list);
            listId = list.getId();
        });
    }

    @Test
    void ownerProjectionDistinguishesExistingAndMissingLists() {
        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(ownerId, repository.findOwnerIdById(listId).orElseThrow());
            assertTrue(repository.findOwnerIdById(UUID.randomUUID()).isEmpty());
        });
    }

    @Test
    void repeatedAddsPreserveOneMembershipAndAllowOtherBooks() {
        var book = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> repository.addBookToReadingList(listId, book));
        QuarkusTransaction.requiringNew().run(() -> repository.addBookToReadingList(listId, book));
        QuarkusTransaction.requiringNew().run(() -> repository.addBookToReadingList(listId, UUID.randomUUID()));
        assertEquals(2L, itemCount());
    }

    @Test
    void authorizationLookupAndInsertUseTwoStatementsWithoutFetchingItems() {
        QuarkusTransaction.requiringNew().run(() -> {
            var statistics = entityManager.getEntityManagerFactory()
                    .unwrap(org.hibernate.SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            try {
                repository.findOwnerIdById(listId).orElseThrow();
                repository.addBookToReadingList(listId, UUID.randomUUID());
                assertEquals(2L, statistics.getPrepareStatementCount());
                assertEquals(0L, statistics.getCollectionFetchCount());
            } finally {
                statistics.setStatisticsEnabled(false);
            }
        });
    }

    @Test
    void movingBetweenAlreadyLoadedListsPreservesMembership() {
        var book = UUID.randomUUID();
        var targetId = QuarkusTransaction.requiringNew().call(() -> {
            repository.addBookToReadingList(listId, book);
            var target = new ReadingListEntity();
            target.setUserId(ownerId);
            target.setName("Target list");
            target.setCreationDate(LocalDateTime.now());
            entityManager.persist(target);
            return target.getId();
        });
        QuarkusTransaction.requiringNew().run(() -> {
            repository.findById(listId).orElseThrow();
            repository.findById(targetId).orElseThrow();
            repository.removeBookFromReadingList(listId, book);
            repository.addBookToReadingList(targetId, book);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(repository.getBookIdsInReadingList(listId).isEmpty());
            assertEquals(java.util.List.of(book), repository.getBookIdsInReadingList(targetId));
        });
    }

    @Test
    void concurrentDuplicateAddsBothSucceedWithOneMembership() throws Exception {
        var book = UUID.randomUUID();
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> {
                start.await();
                QuarkusTransaction.requiringNew().run(() -> repository.addBookToReadingList(listId, book));
                return null;
            });
            var second = workers.submit(() -> {
                start.await();
                QuarkusTransaction.requiringNew().run(() -> repository.addBookToReadingList(listId, book));
                return null;
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
        assertEquals(1L, itemCount());
    }

    @Test
    void insertFailureRollsBackEarlierInsertInSameTransaction() {
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            repository.addBookToReadingList(listId, UUID.randomUUID());
            repository.addBookToReadingList(UUID.randomUUID(), UUID.randomUUID());
        }));
        assertEquals(0L, itemCount());
    }

    @Test
    void listAndBookPagesAreBoundedWithoutHydratingCollections() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < 5; i++) {
                repository.addBookToReadingList(listId, UUID.randomUUID());
                var list = new ReadingListEntity();
                list.setUserId(ownerId);
                list.setName("List " + i);
                list.setCreationDate(LocalDateTime.now());
                entityManager.persist(list);
            }
        });
        QuarkusTransaction.requiringNew().run(() -> {
            var stats = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
            stats.setStatisticsEnabled(true);
            stats.clear();
            try {
                var page = repository.findPageByUserId(ownerId, 0, 2);
                assertEquals(2, page.size());
                assertTrue(page.stream().allMatch(list -> list.getBooks().isEmpty()));
                assertTrue(repository.findSummaryById(listId).orElseThrow().getBooks().isEmpty());
                var next = repository.findPageByUserId(ownerId, 2, 2);
                assertTrue(next.stream().noneMatch(list -> page.stream().anyMatch(p -> p.getReadingListId().equals(list.getReadingListId()))));
                var first = repository.getBookIdsPage(listId, 0, 3);
                var last = repository.getBookIdsPage(listId, 3, 3);
                assertEquals(3, first.size());
                assertEquals(2, last.size());
                assertTrue(last.stream().noneMatch(first::contains));
                assertEquals(0L, stats.getCollectionFetchCount());
            } finally { stats.setStatisticsEnabled(false); }
        });
    }

    private long itemCount() {
        return QuarkusTransaction.requiringNew().call(() -> entityManager
                .createQuery("SELECT COUNT(i) FROM ReadingListItemEntity i", Long.class).getSingleResult());
    }
}
