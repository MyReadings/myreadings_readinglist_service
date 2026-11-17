package org.modular.playground.readinglist.core.usecases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modular.playground.readinglist.core.domain.ReadingList;
import org.modular.playground.readinglist.core.domain.ReadingListImpl;
import org.modular.playground.readinglist.core.usecases.repositories.ReadingListRepository;
import org.modular.playground.user.core.domain.UserImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultReadingListsTest {
    @Mock
    ReadingListRepository repository;

    @InjectMocks
    ReadingListServiceImpl service;

    @Test
    void createsBothDefaultsForTheEventUser() {
        var user = UserImpl.builder().keycloakUserId(UUID.randomUUID()).build();

        service.ensureDefaultReadingLists(user);

        var captor = ArgumentCaptor.forClass(ReadingList.class);
        verify(repository, times(2)).create(captor.capture());
        var lists = captor.getAllValues();
        assertEquals("To Read", lists.get(0).getName());
        assertEquals("Books I plan to read.", lists.get(0).getDescription());
        assertEquals("Read", lists.get(1).getName());
        assertEquals("Books I have already completed.", lists.get(1).getDescription());
        for (var list : lists) {
            assertSame(user, list.getUser());
            assertNotNull(list.getCreationDate());
            assertTrue(list.getBooks().isEmpty());
        }
    }

    @Test
    void propagatesSecondInsertFailureToTheTransactionBoundary() {
        var user = UserImpl.builder().keycloakUserId(UUID.randomUUID()).build();
        var failure = new IllegalStateException("Second insert failed");
        when(repository.create(any(ReadingList.class))).thenReturn(null).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.ensureDefaultReadingLists(user)));
    }

    @Test
    void skipsCreationWhenBothDefaultsExist() {
        var user = UserImpl.builder().keycloakUserId(UUID.randomUUID()).build();
        when(repository.findByUserId(user.getKeycloakUserId())).thenReturn(List.of(
                ReadingListImpl.builder().name("To Read").user(user).build(),
                ReadingListImpl.builder().name("Read").user(user).build()));

        service.ensureDefaultReadingLists(user);

        verify(repository, never()).create(any(ReadingList.class));
    }

    @Test
    void createsReadWhenOnlyToReadExists() {
        assertCreatesOnlyMissingDefault("To Read", "Read");
    }

    @Test
    void createsToReadWhenOnlyReadExists() {
        assertCreatesOnlyMissingDefault("Read", "To Read");
    }

    private void assertCreatesOnlyMissingDefault(String existingName, String missingName) {
        var user = UserImpl.builder().keycloakUserId(UUID.randomUUID()).build();
        when(repository.findByUserId(user.getKeycloakUserId())).thenReturn(List.of(
                ReadingListImpl.builder().name(existingName).user(user).build()));

        service.ensureDefaultReadingLists(user);

        var captor = ArgumentCaptor.forClass(ReadingList.class);
        verify(repository).create(captor.capture());
        assertEquals(missingName, captor.getValue().getName());
        assertSame(user, captor.getValue().getUser());
    }

    @Test
    void repeatedDeliveryDoesNotCreateMoreLists() {
        var user = UserImpl.builder().keycloakUserId(UUID.randomUUID()).build();
        List<ReadingList> stored = new ArrayList<>();
        when(repository.findByUserId(user.getKeycloakUserId()))
                .thenAnswer(invocation -> new ArrayList<>(stored));
        when(repository.create(any(ReadingList.class))).thenAnswer(invocation -> {
            ReadingList list = invocation.getArgument(0);
            stored.add(list);
            return list;
        });

        service.ensureDefaultReadingLists(user);
        service.ensureDefaultReadingLists(user);

        assertEquals(List.of("To Read", "Read"), stored.stream().map(ReadingList::getName).toList());
        verify(repository, times(2)).create(any(ReadingList.class));
    }

    @Test
    void customListDoesNotPreventDefaultCreation() {
        var user = UserImpl.builder().keycloakUserId(UUID.randomUUID()).build();
        when(repository.findByUserId(user.getKeycloakUserId())).thenReturn(List.of(
                ReadingListImpl.builder().name("Favourites").user(user).build()));

        service.ensureDefaultReadingLists(user);

        var captor = ArgumentCaptor.forClass(ReadingList.class);
        verify(repository, times(2)).create(captor.capture());
        assertEquals(List.of("To Read", "Read"),
                captor.getAllValues().stream().map(ReadingList::getName).toList());
    }
}
