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
        when(repository.findOwnerIdById(listId)).thenReturn(Optional.of(owner));
        when(repository.getBookIdsPage(listId, 0, 101)).thenReturn(contents.stream().map(Book::getBookId).toList());
    }

    @Test
    void healthyStrategyUsesOneBatchWithoutIndividualLookups() {
        service.enrichmentStrategy = "hexagonal";
        var ids = contents.stream().map(Book::getBookId).toList();
        when(books.getBooksByIds(ids)).thenReturn(contents);
        assertEquals(contents, service.getBooksInReadingList(listId, 0, 100, principal).content());
        verify(books).getBooksByIds(ids);
        verify(books, never()).getBookById(any());
    }

    @Test
    void brokenStrategyMakesOneCallPerBookWithoutBatching() {
        service.enrichmentStrategy = "broken";
        contents.forEach(book -> when(books.getBookById(book.getBookId())).thenReturn(Optional.of(book)));
        assertEquals(contents, service.getBooksInReadingList(listId, 0, 100, principal).content());
        verify(books, times(contents.size())).getBookById(any());
        verify(books, never()).getBooksByIds(any());
    }    @Test
    void lookaheadIsNotFetchedAndDeletedBooksDoNotHideFollowingPages() {
        service.enrichmentStrategy = "hexagonal";
        var ids = java.util.stream.IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).toList();
        when(repository.getBookIdsPage(listId, 0, 101)).thenReturn(ids);
        when(books.getBooksByIds(ids.subList(0, 100))).thenReturn(List.of());
        var page = service.getBooksInReadingList(listId, 0, 100, principal);
        assertTrue(page.hasMore());
        assertTrue(page.content().isEmpty());
        verify(books).getBooksByIds(ids.subList(0, 100));
        verify(repository, never()).findById(any());
    }

}
