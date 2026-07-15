-- Tabla stub minima: el modulo "Registro de socios" (fuera de alcance de
-- 001-aportes) es el dueno real de esta tabla y podra ampliarla con ALTER
-- TABLE sin romper las FK que Aportes ya crea contra socio.id.
CREATE TABLE socio (
    id BIGINT AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
