package com.rateLimitExample.eventConsumer.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rateLimitExample.eventConsumer.model.ReplayJobStatus;
import com.rateLimitExample.eventConsumer.service.ReplayService;

@RestController
@RequestMapping("/replays")
public class ReplayController {

	private final ReplayService replayService;

	public ReplayController(ReplayService replayService) {
		this.replayService = replayService;
	}

	@PostMapping("/from-beginning")
	public ReplayJobStatus replayFromBeginning(@RequestParam(required = false) Integer maxRecords) {
		return replayService.replayFromBeginning(maxRecords);
	}

	@GetMapping
	public List<ReplayJobStatus> listReplayJobs() {
		return replayService.listJobs();
	}
}
