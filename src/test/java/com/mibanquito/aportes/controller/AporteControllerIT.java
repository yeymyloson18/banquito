package com.mibanquito.aportes.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-014 / SC-005: verifica que las rutas de Aportes rechazan el rol SOCIO
 * (403) y responden correctamente para ADMINISTRADOR. No repite la logica de
 * negocio (ya cubierta por AporteServiceTest, 12 casos).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AporteControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long socioId;
    private Long periodoId;

    @BeforeEach
    void crearFixtures() {
        jdbcTemplate.update("INSERT INTO periodo_anual (estado) VALUES ('ABIERTO')");
        periodoId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update("INSERT INTO socio (id) VALUES (NULL)");
        socioId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update("INSERT INTO parametro_periodo (periodo_id, monto_aporte_mensual, monto_multa, "
                + "vigente_desde) VALUES (?, 50.00, 5.00, '2020-01-01')", periodoId);

        jdbcTemplate.update("INSERT INTO aporte_inicial (socio_id, periodo_id, monto, fecha) VALUES (?, ?, 100.00, ?)",
                socioId, periodoId, LocalDate.now().withDayOfMonth(1));
    }

    // ---- GET /aporte-inicial --------------------------------------------

    @Test
    @WithMockUser(roles = "SOCIO")
    void getAporteInicial_conRolSocio_rechaza403() throws Exception {
        mockMvc.perform(get("/socios/{socioId}/periodos/{periodoId}/aporte-inicial", socioId, periodoId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void getAporteInicial_conRolAdministrador_ok() throws Exception {
        mockMvc.perform(get("/socios/{socioId}/periodos/{periodoId}/aporte-inicial", socioId, periodoId))
                .andExpect(status().isOk());
    }

    // ---- GET /aporte-mensual ---------------------------------------------

    @Test
    @WithMockUser(roles = "SOCIO")
    void getAporteMensual_conRolSocio_rechaza403() throws Exception {
        mockMvc.perform(get("/socios/{socioId}/periodos/{periodoId}/aporte-mensual", socioId, periodoId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void getAporteMensual_conRolAdministrador_ok() throws Exception {
        mockMvc.perform(get("/socios/{socioId}/periodos/{periodoId}/aporte-mensual", socioId, periodoId))
                .andExpect(status().isOk());
    }

    // ---- GET /deuda -------------------------------------------------------

    @Test
    @WithMockUser(roles = "SOCIO")
    void getDeuda_conRolSocio_rechaza403() throws Exception {
        mockMvc.perform(get("/socios/{socioId}/periodos/{periodoId}/deuda", socioId, periodoId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void getDeuda_conRolAdministrador_ok() throws Exception {
        mockMvc.perform(get("/socios/{socioId}/periodos/{periodoId}/deuda", socioId, periodoId))
                .andExpect(status().isOk());
    }
}
