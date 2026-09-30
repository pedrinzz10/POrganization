package com.porganization.studies.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Ids das matérias (não arquivadas) na nova ordem de prioridade: a primeira vira 1. */
public record SubjectOrderRequest(@NotNull List<UUID> ids) {
}
