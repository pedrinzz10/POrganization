package fixtures.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

// Mapeia user_settings com uma coluna que não existe, para o SchemaValidationTest.
@Entity
@Table(name = "user_settings")
public class DivergentUserSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "coluna_inexistente")
    private String colunaInexistente;
}
