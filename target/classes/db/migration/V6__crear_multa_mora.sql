CREATE TABLE multa_mora (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    aporte_mensual_id  BIGINT NOT NULL,
    monto              DECIMAL(10,2) NOT NULL,
    mes_generada       DATE NOT NULL,
    CONSTRAINT fk_multa_mora_aporte_mensual
        FOREIGN KEY (aporte_mensual_id) REFERENCES aporte_mensual(id),
    CONSTRAINT uq_multa_mora_aporte_mensual
        UNIQUE (aporte_mensual_id),
    CONSTRAINT chk_multa_mora_monto
        CHECK (monto > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
