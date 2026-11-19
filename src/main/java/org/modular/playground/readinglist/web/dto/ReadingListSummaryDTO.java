package org.modular.playground.readinglist.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.modular.playground.readinglist.core.domain.ReadingList;
import java.util.UUID;

// Book contents are retrieved through the separately paginated /{id}/books endpoint.
@RegisterForReflection
public record ReadingListSummaryDTO(UUID readingListId, String name, String description) {
    public static ReadingListSummaryDTO from(ReadingList list) {
        return new ReadingListSummaryDTO(list.getReadingListId(), list.getName(), list.getDescription());
    }
}
