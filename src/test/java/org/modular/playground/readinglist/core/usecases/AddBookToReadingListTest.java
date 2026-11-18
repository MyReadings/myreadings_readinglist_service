package org.modular.playground.readinglist.core.usecases;

import jakarta.json.Json;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modular.playground.catalog.core.domain.BookImpl;
import org.modular.playground.catalog.core.usecases.BookService;
import org.modular.playground.readinglist.core.usecases.repositories.ReadingListRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddBookToReadingListTest {
    @Mock ReadingListRepository repository;
    @Mock BookService books;
    @Mock JsonWebToken principal;
    @InjectMocks ReadingListServiceImpl service;

    final UUID caller = UUID.randomUUID();
    final UUID listId = UUID.randomUUID();
    final UUID bookId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(principal.getSubject()).thenReturn(caller.toString());
    }

    @Test
    void ownerCanAddBookWithoutLoadingListContents() {
        when(repository.findOwnerIdById(listId)).thenReturn(Optional.of(caller));
        when(books.getBookById(bookId)).thenReturn(Optional.of(BookImpl.builder().bookId(bookId).build()));

        service.addBookToReadingList(listId, bookId, principal);

        verify(repository).addBookToReadingList(listId, bookId);
        verify(repository, never()).findById(any());
    }

    @Test
    void missingListIsNotFoundBeforeCallingCatalog() {
        when(repository.findOwnerIdById(listId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.addBookToReadingList(listId, bookId, principal));
        verifyNoInteractions(books);
        verify(repository, never()).addBookToReadingList(any(), any());
    }

    @Test
    void anotherUsersListIsForbiddenBeforeCallingCatalog() {
        when(repository.findOwnerIdById(listId)).thenReturn(Optional.of(UUID.randomUUID()));
        assertThrows(ForbiddenException.class, () -> service.addBookToReadingList(listId, bookId, principal));
        verifyNoInteractions(books);
        verify(repository, never()).addBookToReadingList(any(), any());
    }

    @Test
    void adminCanAddToAnotherUsersList() {
        when(repository.findOwnerIdById(listId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(principal.getClaim("realm_access")).thenReturn(Json.createObjectBuilder()
                .add("roles", Json.createArrayBuilder().add("admin")).build());
        when(books.getBookById(bookId)).thenReturn(Optional.of(BookImpl.builder().bookId(bookId).build()));

        service.addBookToReadingList(listId, bookId, principal);

        verify(repository).addBookToReadingList(listId, bookId);
    }

    @Test
    void missingBookIsNotInserted() {
        when(repository.findOwnerIdById(listId)).thenReturn(Optional.of(caller));
        when(books.getBookById(bookId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.addBookToReadingList(listId, bookId, principal));
        verify(repository, never()).addBookToReadingList(any(), any());
    }

    @Test
    void catalogFailureIsPropagatedWithoutWriting() {
        when(repository.findOwnerIdById(listId)).thenReturn(Optional.of(caller));
        var failure = new IllegalStateException("Catalog unavailable");
        when(books.getBookById(bookId)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.addBookToReadingList(listId, bookId, principal)));
        verify(repository, never()).addBookToReadingList(any(), any());
    }
}
