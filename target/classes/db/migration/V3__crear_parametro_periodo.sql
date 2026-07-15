CREATE TABLE parametro_periodo (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    periodo_id             BIGINT NOT NULL,
    monto_aporte_mensual   DECIMAL(10,2) NOT NULL,
    monto_multa            DECIMAL(10,2) NOT NULL,
    vigente_desde          DATE NOT NULL,
    CONSTRAINT fk_parametro_periodo_periodo
        FOREIGN KEY (periodo_id) REFERENCES periodo_anual(id),
    CONSTRAINT chk_parametro_periodo_montos
        CHECK (monto_aporte_mensual > 0 AND monto_multa > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_parametro_periodo_periodo_vigente
    ON parametro_periodo (periodo_id, vigente_desde);
