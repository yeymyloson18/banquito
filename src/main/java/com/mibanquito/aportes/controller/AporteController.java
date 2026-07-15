package com.mibanquito.aportes.controller;

import com.mibanquito.aportes.dto.AporteInicialRequest;
import com.mibanquito.aportes.dto.AporteMensualRequest;
import com.mibanquito.aportes.dto.DeudaAcumuladaDTO;
import com.mibanquito.aportes.service.AporteMensualYaPagadoException;
import com.mibanquito.aportes.service.AporteService;
import com.mibanquito.aportes.service.PagoParcialNoPermitidoException;
import com.mibanquito.aportes.service.PeriodoCerradoException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

/**
 * Rutas de Aportes (US1, US2, US3). Todas restringidas al rol ADMINISTRADOR
 * (FR-014, Principio IX) - ver contracts/aportes-endpoints.md.
 */
@Controller
@RequestMapping("/socios/{socioId}/periodos/{periodoId}")
public class AporteController {

    private final AporteService aporteService;

    public AporteController(AporteService aporteService) {
        this.aporteService = aporteService;
    }

    // ---- Aporte inicial (US1, FR-001, FR-011) --------------------------

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @GetMapping("/aporte-inicial")
    public String mostrarFormularioAporteInicial(@PathVariable Long socioId, @PathVariable Long periodoId,
                                                  Model model) {
        model.addAttribute("socioId", socioId);
        model.addAttribute("periodoId", periodoId);
        if (!model.containsAttribute("aporteInicialRequest")) {
            model.addAttribute("aporteInicialRequest", new AporteInicialRequest(null, null));
        }
        return "aportes/aporte-inicial-form";
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/aporte-inicial")
    public String registrarAporteInicial(@PathVariable Long socioId, @PathVariable Long periodoId,
                                          @Valid @ModelAttribute("aporteInicialRequest") AporteInicialRequest request,
                                          BindingResult bindingResult, Model model,
                                          RedirectAttributes redirectAttributes) {
        model.addAttribute("socioId", socioId);
        model.addAttribute("periodoId", periodoId);
        if (bindingResult.hasErrors()) {
            return "aportes/aporte-inicial-form";
        }
        try {
            aporteService.registrarAporteInicial(socioId, periodoId, request.monto(), request.fecha());
        } catch (IllegalArgumentException | PeriodoCerradoException e) {
            model.addAttribute("error", e.getMessage());
            return "aportes/aporte-inicial-form";
        }
        redirectAttributes.addFlashAttribute("mensaje", "Aporte inicial registrado correctamente");
        return "redirect:/socios/{socioId}/periodos/{periodoId}/deuda";
    }

    // ---- Aporte mensual (US2, US3, FR-002, FR-009, FR-010, FR-012) ------

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @GetMapping("/aporte-mensual")
    public String mostrarFormularioAporteMensual(@PathVariable Long socioId, @PathVariable Long periodoId,
                                                  Model model) {
        YearMonth mesActual = YearMonth.now();
        model.addAttribute("socioId", socioId);
        model.addAttribute("periodoId", periodoId);
        model.addAttribute("deudaAcumulada", aporteService.calcularDeudaAcumulada(socioId, periodoId, mesActual));
        if (!model.containsAttribute("aporteMensualRequest")) {
            model.addAttribute("aporteMensualRequest", new AporteMensualRequest(mesActual, null, null));
        }
        return "aportes/aporte-mensual-form";
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/aporte-mensual")
    public String registrarAporteMensual(@PathVariable Long socioId, @PathVariable Long periodoId,
                                          @Valid @ModelAttribute("aporteMensualRequest") AporteMensualRequest request,
                                          BindingResult bindingResult, Model model,
                                          RedirectAttributes redirectAttributes) {
        model.addAttribute("socioId", socioId);
        model.addAttribute("periodoId", periodoId);
        if (bindingResult.hasErrors()) {
            return "aportes/aporte-mensual-form";
        }
        try {
            aporteService.registrarAporteMensual(socioId, periodoId, request.mesHasta(),
                    request.montoPagado(), request.fecha());
        } catch (PagoParcialNoPermitidoException | AporteMensualYaPagadoException | PeriodoCerradoException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("deudaAcumulada",
                    aporteService.calcularDeudaAcumulada(socioId, periodoId, request.mesHasta()));
            return "aportes/aporte-mensual-form";
        }
        redirectAttributes.addFlashAttribute("mensaje", "Aporte mensual registrado correctamente");
        return "redirect:/socios/{socioId}/periodos/{periodoId}/deuda";
    }

    // ---- Consulta de deuda/historial (US3, FR-004, SC-004) --------------

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @GetMapping("/deuda")
    public String consultarDeuda(@PathVariable Long socioId, @PathVariable Long periodoId, Model model) {
        YearMonth mesActual = YearMonth.now();
        var total = aporteService.calcularDeudaAcumulada(socioId, periodoId, mesActual);
        var desglose = aporteService.obtenerDesgloseDeuda(socioId, periodoId, mesActual);
        model.addAttribute("socioId", socioId);
        model.addAttribute("periodoId", periodoId);
        model.addAttribute("deuda", new DeudaAcumuladaDTO(desglose, total));
        return "aportes/deuda-socio";
    }
}
