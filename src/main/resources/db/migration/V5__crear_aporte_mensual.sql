-- "mes" se almacena como DATE (siempre dia 1 del mes) y se mapea en JPA a
-- java.time.YearMonth mediante un AttributeConverter (ver
-- com.mibanquito.aportes.entity.YearMonthDateConverter).
CREATE TABLE aporte_mensual (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    socio_id       BIGINT NOT NULL,
    periodo_id     BIGINT NOT NULL,
    mes            DATE NOT NULL,
    monto_esperado DECIMAL(10,2) NOT NULL,
    fecha_pago     DATE NULL,
    estado         VARCHAR(10) NOT NULL,
    CONSTRAINT fk_aporte_mensual_socio
        FOREIGN KEY (socio_id) REFERENCES socio(id),
    CONSTRAINT fk_aporte_mensual_periodo
        FOREIGN KEY (periodo_id) REFERENCES periodo_anual(id),
    CONSTRAINT chk_aporte_mensual_estado
        CHECK (estado IN ('PENDIENTE', 'PAGADO')),
    CONSTRAINT uq_aporte_mensual_socio_periodo_mes
        UNIQUE (socio_id, periodo_id, mes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
