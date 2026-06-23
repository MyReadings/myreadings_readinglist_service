package org.modular.playground.readinglist.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.UUID;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.modular.playground.common.filters.TraceIdFilter;
import org.modular.playground.readinglist.core.domain.ReadingList;
import org.modular.playground.readinglist.core.domain.ReadingListImpl;
import org.modular.playground.readinglist.core.usecases.ReadingListService;
import org.modular.playground.user.core.domain.User;
import org.modular.playground.user.core.domain.UserImpl;

import io.smallrye.common.annotation.Blocking;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class UserProfileCreatedListener {

    private static final Logger LOGGER = Logger.getLogger(UserProfileCreatedListener.class);

    @Inject
    ReadingListService readingListService;

    @Incoming("user-profile-created")
    @Blocking
    public void processUserCreation(JsonObject eventJson) {
        MDC.put(TraceIdFilter.TRACE_ID_KEY, "event-" + UUID.randomUUID().toString());
        LOGGER.infof("Received user-profile-created event: %s", eventJson.encode());

        try {
            User user = UserImpl.builder()
                    .keycloakUserId(UUID.fromString(eventJson.getString("keycloakUserId")))
                    .firstName(eventJson.getString("firstName"))
                    .lastName(eventJson.getString("lastName"))
                    .username(eventJson.getString("username"))
                    .email(eventJson.getString("email"))
                    .build();
            createDefaultReadingListsForUser(user);
            LOGGER.infof("Created default reading lists for user %s", user.getUsername());
        } catch (Exception e) {
            LOGGER.errorf(e, "Failed to process user-profile-created event");
        } finally {
            MDC.remove(TraceIdFilter.TRACE_ID_KEY);
        }
    }

    private void createDefaultReadingListsForUser(User user) {
        ReadingList toReadList = ReadingListImpl.builder()
                .readingListId(UUID.randomUUID())
                .user(user)
                .name("To Read")
                .creationDate(LocalDateTime.now())
                .description("Books I plan to read.")
                .build();

        ReadingList alreadyReadList = ReadingListImpl.builder()
                .readingListId(UUID.randomUUID())
                .user(user)
                .name("Read")
                .creationDate(LocalDateTime.now())
                .description("Books I have already completed.")
                .build();

        readingListService.createReadingListInternal(toReadList);
        readingListService.createReadingListInternal(alreadyReadList);
    }

    public static class UserCreatedEvent {
        public UUID keycloakUserId;
        public String firstName;
        public String lastName;
        public String username;
        public String email;
    }
}
