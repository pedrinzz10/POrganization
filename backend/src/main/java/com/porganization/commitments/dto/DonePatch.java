package com.porganization.commitments.dto;

import jakarta.validation.constraints.NotNull;

public record DonePatch(@NotNull Boolean done) {
}
