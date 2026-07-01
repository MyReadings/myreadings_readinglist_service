package org.modular.playground.readinglist.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@io.quarkus.runtime.annotations.RegisterForReflection
public class MoveBookRequestDTO {
    @NotNull
    private UUID sourceListId;
    @NotNull
    private UUID targetListId;
}
