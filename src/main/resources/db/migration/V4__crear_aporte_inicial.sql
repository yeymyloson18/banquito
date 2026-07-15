CREATE TABLE aporte_inicial (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    socio_id   BIGINT NOT NULL,
    periodo_id BIGINT NOT NULL,
    monto      DECIMAL(10,2) NOT NULL,
    fecha      DATE NOT NULL,
    CONSTRAINT fk_aporte_inicial_socio
        FOREIGN KEY (socio_id) REFERENCES socio(id),
    CONSTRAINT fk_aporte_inicial_periodo
        FOREIGN KEY (periodo_id) REFERENCES periodo_anual(id),
    CONSTRAINT chk_aporte_inicial_monto
        CHECK (monto > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_aporte_inicial_socio_periodo
    ON aporte_inicial (socio_id, periodo_id);
