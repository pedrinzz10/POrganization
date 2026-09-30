package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.porganization.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TagRepositoryTest extends IntegrationTest {

    @Autowired
    private TagRepository repository;

    // E01 T1 (CA1)
    @Test
    void nomeUnicoPorUsuarioSemDiferenciarMaiusculas() {
        UUID usuarioA = UUID.randomUUID();
        UUID usuarioB = UUID.randomUUID();
        repository.saveAndFlush(new Tag(usuarioA, "Faculdade"));

        assertThatThrownBy(() -> repository.saveAndFlush(new Tag(usuarioA, "faculdade")))
                .isInstanceOf(DataIntegrityViolationException.class);

        Tag deB = repository.saveAndFlush(new Tag(usuarioB, "faculdade"));
        assertThat(deB.getId()).isNotNull();
    }

    @Test
    void nomeEmBrancoFalhaPelaConstraint() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Tag(UUID.randomUUID(), "  ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listaSoAsTagsDoUsuarioEmOrdemAlfabetica() {
        UUID dono = UUID.randomUUID();
        repository.saveAndFlush(new Tag(dono, "línguas"));
        repository.saveAndFlush(new Tag(dono, "Faculdade"));
        repository.saveAndFlush(new Tag(UUID.randomUUID(), "de outra pessoa"));

        assertThat(repository.findByUserIdOrderByNameAsc(dono)).extracting(Tag::getName)
                .containsExactly("Faculdade", "línguas");
    }
}
