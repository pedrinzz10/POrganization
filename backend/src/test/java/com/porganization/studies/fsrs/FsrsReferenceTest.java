package com.porganization.studies.fsrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * E05 T1 (CA1): o port em Java bate com a implementação de referência (ts-fsrs 5.4.2) para as
 * mesmas notas e datas. O fixture é gerado por src/test/resources/fsrs/generate-reference.mjs.
 *
 * <p>Única diferença intencional: o ts-fsrs pode passar do intervalo máximo (366, 367 com máximo
 * 365) ao forçar hard < good < easy; aqui o teto é aplicado por último (CA3). Stability e
 * difficulty são iguais em todos os passos.
 */
class FsrsReferenceTest {

    private static final double TOLERANCIA = 1e-4;

    static Stream<Arguments> sequencias() throws IOException {
        try (InputStream in = FsrsReferenceTest.class.getResourceAsStream("/fsrs/reference-ts-fsrs.json")) {
            JsonNode root = JsonMapper.builder().build().readTree(in);
            return root.get("sequences").valueStream()
                    .map(seq -> Arguments.of(Named.of(seq.get("name").asString(), seq.get("steps"))));
        }
    }

    @ParameterizedTest
    @MethodSource("sequencias")
    void bateComATsFsrs(JsonNode passos) {
        Fsrs fsrs = new Fsrs(FsrsParameters.defaults());
        FsrsCard card = FsrsCard.newCard();

        for (JsonNode esperado : passos) {
            LocalDate dia = LocalDate.parse(esperado.get("reviewDate").asString());
            ReviewGrade nota = switch (esperado.get("grade").asString()) {
                case "Hard" -> ReviewGrade.DIFICIL;
                case "Good" -> ReviewGrade.OK;
                case "Easy" -> ReviewGrade.FACIL;
                default -> throw new IllegalArgumentException(esperado.toString());
            };

            card = fsrs.review(card, nota, dia);

            String passo = dia + " " + nota;
            assertThat(card.stability()).as("stability em %s", passo)
                    .isCloseTo(esperado.get("stability").asDouble(), within(TOLERANCIA));
            assertThat(card.difficulty()).as("difficulty em %s", passo)
                    .isCloseTo(esperado.get("difficulty").asDouble(), within(TOLERANCIA));
            assertThat(card.state().name()).as("estado em %s", passo)
                    .isEqualToIgnoringCase(esperado.get("state").asString());
            assertThat(card.reps()).as("reps em %s", passo).isEqualTo(esperado.get("reps").asInt());

            int intervaloReferencia = esperado.get("scheduledDays").asInt();
            int esperadoComTeto = Math.min(intervaloReferencia, FsrsParameters.DEFAULT_MAXIMUM_INTERVAL);
            assertThat(card.scheduledDays()).as("intervalo em %s", passo).isEqualTo(esperadoComTeto);
            assertThat(card.due()).as("vencimento em %s", passo).isEqualTo(dia.plusDays(esperadoComTeto));
            if (intervaloReferencia <= FsrsParameters.DEFAULT_MAXIMUM_INTERVAL) {
                assertThat(card.due().toString()).isEqualTo(esperado.get("due").asString());
            }
        }
    }
}
