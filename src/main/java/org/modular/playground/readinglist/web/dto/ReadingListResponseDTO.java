package org.modular.playground.readinglist.web.dto;

import java.util.List;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@RegisterForReflection
public class ReadingListResponseDTO {
    private UUID readingListId;
    private String name;
    private String description;
    private List<UUID> books;
}
