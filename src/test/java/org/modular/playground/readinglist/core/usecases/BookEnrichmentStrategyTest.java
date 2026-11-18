package org.modular.playground.readinglist.core.usecases;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modular.playground.catalog.core.domain.Book;
import org.modular.playground.catalog.core.domain.BookImpl;
import org.modular.playground.catalog.core.usecases.BookService;
import org.modular.playground.readinglist.core.domain.ReadingListImpl;
import org.modular.playground.readinglist.core.usecases.repositories.ReadingListRepository;
import org.modular.playground.user.core.domain.UserImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentStrategyTest {
    @Mock ReadingListRepository repository;
    @Mock BookService books;
    @Mock JsonWebToken principal;
    @InjectMocks ReadingListServiceImpl service;
    UUID listId = UUID.randomUUID();
    List<Book> contents = List.of(BookImpl.builder().bookId(UUID.randomUUID()).build(),
        BookImpl.builder().bookId(UUID.randomUUID()).build());

    @BeforeEach
    void ownedList() {
        var owner = UUID.randomUUID();
        when(principal.getSubject()).thenReturn(owner.toString());
        when(repository.findById(listId)).thenReturn(Optional.of(ReadingListImpl.builder()
            .readingListId(listId).user(UserImpl.builder().keycloakUserId(owner).build()).books(contents).build()));
    }

    @Test
    void healthyStrategyUsesOneBatchWithoutIndividualLookups() {
        service.enrichmentStrategy = "hexagonal";
        var ids = contents.stream().map(Book::getBookId).toList();
        when(books.getBooksByIds(ids)).thenReturn(contents);
        assertEquals(contents, service.getBooksInReadingList(listId, principal));
        verify(books).getBooksByIds(ids);
        verify(books, never()).getBookById(any());
    }

    @Test
    void brokenStrategyMakesOneCallPerBookWithoutBatching() {
        service.enrichmentStrategy = "broken";
        contents.forEach(book -> when(books.getBookById(book.getBookId())).thenReturn(Optional.of(book)));
        assertEquals(contents, service.getBooksInReadingList(listId, principal));
        verify(books, times(contents.size())).getBookById(any());
        verify(books, never()).getBooksByIds(any());
    }
}
