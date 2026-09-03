package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

	public record SystemInfo(
			String osName,
			String osVersion,
			String osArch,
			int processors,
			long memoryMb
	) {}

	@GetMapping("/system")
	public SystemInfo system() {
		return new SystemInfo(
				System.getProperty("os.name"),
				System.getProperty("os.version"),
				System.getProperty("os.arch"),
				Runtime.getRuntime().availableProcessors(),
				Runtime.getRuntime().maxMemory() / (1024 * 1024)
		);
	}

}
