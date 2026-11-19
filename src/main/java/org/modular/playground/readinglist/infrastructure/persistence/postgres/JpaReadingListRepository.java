package org.modular.playground.readinglist.infrastructure.persistence.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.persistence.TypedQuery;
import org.modular.playground.readinglist.core.domain.ReadingList;
import org.modular.playground.readinglist.core.usecases.repositories.ReadingListRepository;
import org.modular.playground.readinglist.infrastructure.persistence.postgres.mapper.ReadingListMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

@ApplicationScoped
@IfBuildProperty(name = "app.repository.type", stringValue = "jpa", enableIfMissing = false)
public class JpaReadingListRepository implements ReadingListRepository {

    private static final Logger LOGGER = Logger.getLogger(JpaReadingListRepository.class);

    @Inject
    @PersistenceUnit("readinglist-db")
    EntityManager entityManager;

    @Inject
    ReadingListMapper mapper;

    @Override
    public ReadingList create(ReadingList list) {
        LOGGER.debugf("JPA: Creating reading list entity with ID: %s", list.getReadingListId());
        ReadingListEntity newEntity = mapper.toEntity(list);
        entityManager.persist(newEntity);
        return mapper.toDomain(newEntity);
    }

    @Override
    public ReadingList update(ReadingList list) {
        LOGGER.debugf("JPA: Merging reading list entity with ID: %s", list.getReadingListId());
        ReadingListEntity managedEntity = entityManager.find(ReadingListEntity.class, list.getReadingListId());

        if (managedEntity == null) {
            throw new IllegalArgumentException(
                    "ReadingList with ID " + list.getReadingListId() + " not found for update.");
        }
        mapper.updateEntityFromDomain(list, managedEntity);
        return mapper.toDomain(managedEntity);
    }

    @Override
    public Optional<ReadingList> findById(UUID readingListId) {
        LOGGER.debugf("JPA: Finding reading list entity by ID: %s", readingListId);
        return Optional.ofNullable(entityManager.find(ReadingListEntity.class, readingListId)).map(mapper::toDomain);
    }

    @Override
    public Optional<UUID> findOwnerIdById(UUID readingListId) {
        return entityManager.createQuery(
                "SELECT rl.userId FROM ReadingListEntity rl WHERE rl.id = :listId", UUID.class)
                .setParameter("listId", readingListId)
                .getResultList().stream().findFirst();
    }

    @Override
    public List<ReadingList> findByUserId(UUID userId) {
        LOGGER.debugf("JPA: Finding reading list entities for user ID: %s", userId);
        TypedQuery<ReadingListEntity> query = entityManager
                .createQuery("SELECT rl FROM ReadingListEntity rl WHERE rl.userId = :userId", ReadingListEntity.class);
        query.setParameter("userId", userId);
        return query.getResultList().stream().map(mapper::toSummaryDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<ReadingList> findSummaryById(UUID id) {
        return Optional.ofNullable(entityManager.find(ReadingListEntity.class, id)).map(mapper::toSummaryDomain);
    }

    @Override
    public List<ReadingList> findPageByUserId(UUID userId, int offset, int limit) {
        // Never paginate a collection fetch join: Hibernate can paginate it in memory.
        return entityManager.createQuery("SELECT rl FROM ReadingListEntity rl WHERE rl.userId = :userId ORDER BY rl.creationDate, rl.id", ReadingListEntity.class)
                .setParameter("userId", userId).setFirstResult(offset).setMaxResults(limit)
                .getResultList().stream().<ReadingList>map(mapper::toSummaryDomain).toList();
    }

    @Override
    public List<UUID> getBookIdsPage(UUID listId, int offset, int limit) {
        return entityManager.createQuery("SELECT i.id.bookId FROM ReadingListItemEntity i WHERE i.id.readingListId = :listId ORDER BY i.id.bookId", UUID.class)
                .setParameter("listId", listId).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    @Override
    public void deleteById(UUID readingListId) {
        LOGGER.debugf("JPA: Deleting reading list entity with ID: %s", readingListId);
        ReadingListEntity entity = entityManager.find(ReadingListEntity.class, readingListId);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    public void addBookToReadingList(UUID readingListId, UUID bookId) {
        LOGGER.debugf("JPA: Adding book %s to list %s", bookId, readingListId);
        entityManager.createNativeQuery("""
                INSERT INTO reading_list_items (reading_list_id, book_id)
                VALUES (:listId, :bookId)
                ON CONFLICT (reading_list_id, book_id) DO NOTHING
                """)
                .setParameter("listId", readingListId)
                .setParameter("bookId", bookId)
                .executeUpdate();
    }

    @Override
    public void removeBookFromReadingList(UUID readingListId, UUID bookId) {
        LOGGER.debugf("JPA: Removing book %s from list %s", bookId, readingListId);
        ReadingListEntity listEntity = entityManager.find(ReadingListEntity.class, readingListId);
        if (listEntity == null) {
            throw new IllegalArgumentException("ReadingList with ID " + readingListId + " not found.");
        }

        ReadingListItemIdEntity id = new ReadingListItemIdEntity(readingListId, bookId);
        ReadingListItemEntity item = entityManager.find(ReadingListItemEntity.class, id);

        if (item == null) {
            throw new IllegalArgumentException(
                    "Book with ID " + bookId + " not found in reading list " + readingListId + ".");
        }

        // Keep the managed collection in sync; orphanRemoval deletes the row.
        listEntity.getItems().removeIf(existing -> id.equals(existing.getId()));
    }

    @Override
    public List<UUID> getBookIdsInReadingList(UUID readingListId) {
        LOGGER.debugf("JPA: Getting book IDs for list %s", readingListId);
        TypedQuery<UUID> query = entityManager.createQuery(
                "SELECT i.id.bookId FROM ReadingListItemEntity i WHERE i.id.readingListId = :listId", UUID.class);
        query.setParameter("listId", readingListId);
        return query.getResultList();
    }

    @Override
    public Optional<ReadingList> findReadingListContainingBookForUser(UUID userId, UUID bookId) {
        LOGGER.debugf("JPA: Finding if user %s has book %s in a list", userId, bookId);
        TypedQuery<ReadingListEntity> query = entityManager.createQuery(
                "SELECT rl FROM ReadingListEntity rl JOIN rl.items i WHERE rl.userId = :userId AND i.id.bookId = :bookId ORDER BY rl.id",
                ReadingListEntity.class);
        query.setParameter("userId", userId);
        query.setParameter("bookId", bookId);
        return query.setMaxResults(1).getResultStream().findFirst().map(mapper::toSummaryDomain);
    }
}
