package com.omnichannel.analytics.web;

import com.omnichannel.analytics.AnalyticsService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Visão geral operacional do período (padrão: 30 dias, máx. 365). */
@RestController
@RequestMapping("/api/analytics")
class AnalyticsController {

	private final AnalyticsService analyticsService;

	AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping("/overview")
	AnalyticsService.Overview overview(@RequestParam(defaultValue = "30") int days) {
		return analyticsService.overview(Math.clamp(days, 1, 365));
	}
}
