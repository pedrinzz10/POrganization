package com.porganization.studies.dto;

import com.porganization.studies.Tag;
import java.util.UUID;

public record TagResponse(UUID id, String name) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
