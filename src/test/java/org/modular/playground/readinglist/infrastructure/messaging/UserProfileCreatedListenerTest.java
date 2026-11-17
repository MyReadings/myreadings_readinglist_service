package org.modular.playground.readinglist.infrastructure.messaging;

import io.vertx.core.json.JsonObject;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modular.playground.common.filters.TraceIdFilter;
import org.modular.playground.readinglist.core.usecases.ReadingListService;
import org.modular.playground.user.core.domain.User;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileCreatedListenerTest {
    @Mock
    ReadingListService service;

    @InjectMocks
    UserProfileCreatedListener listener;

    @Test
    void delegatesDefaultCreationAsOneServiceOperation() {
        var userId = UUID.randomUUID();
        var event = new JsonObject()
                .put("keycloakUserId", userId.toString())
                .put("username", "demo-user")
                .put("firstName", "Demo")
                .put("lastName", "Reader")
                .put("email", "demo@example.test");

        listener.processUserCreation(event);

        var captor = ArgumentCaptor.forClass(User.class);
        verify(service).ensureDefaultReadingLists(captor.capture());
        verifyNoMoreInteractions(service);
        var user = captor.getValue();
        assertEquals(userId, user.getKeycloakUserId());
        assertEquals("demo-user", user.getUsername());
        assertEquals("Demo", user.getFirstName());
        assertEquals("Reader", user.getLastName());
        assertEquals("demo@example.test", user.getEmail());
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_KEY));
    }

    @Test
    void preservesFailurePropagationAndCleansMdc() {
        var failure = new IllegalStateException("Database unavailable");
        doThrow(failure).when(service).ensureDefaultReadingLists(any(User.class));
        var event = new JsonObject().put("keycloakUserId", UUID.randomUUID().toString());

        var thrown = assertThrows(IllegalStateException.class,
                () -> listener.processUserCreation(event));

        assertSame(failure, thrown.getCause());
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_KEY));
    }

    @Test
    void malformedUserIdDoesNotCreateLists() {
        var event = new JsonObject().put("keycloakUserId", "invalid");

        assertThrows(IllegalStateException.class, () -> listener.processUserCreation(event));

        verifyNoInteractions(service);
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_KEY));
    }
}
