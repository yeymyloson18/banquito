-- Tabla stub minima: el modulo "Apertura de periodo anual" (fuera de alcance
-- de 001-aportes) es el dueno real de esta tabla y podra ampliarla con ALTER
-- TABLE sin romper las FK que Aportes ya crea contra periodo_anual.id.
CREATE TABLE periodo_anual (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    estado VARCHAR(10) NOT NULL,
    CONSTRAINT chk_periodo_anual_estado CHECK (estado IN ('ABIERTO', 'CERRADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
