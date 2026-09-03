package com.example.demo;

import java.lang.management.ManagementFactory;

import com.sun.management.OperatingSystemMXBean;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

	public record SystemInfo(
			String osName,
			String osVersion,
			String osArch,
			int processors,
			long ramTotalMb,
			long ramFreeMb,
			double cpuLoadPercent
	) {}

	@GetMapping("/system")
	public SystemInfo system() {
		OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
		long mb = 1024 * 1024;

		return new SystemInfo(
				os.getName(),
				os.getVersion(),
				os.getArch(),
				os.getAvailableProcessors(),
				os.getTotalMemorySize() / mb,
				os.getFreeMemorySize() / mb,
				cpuLoadPercent(os)
		);
	}

	private double cpuLoadPercent(OperatingSystemMXBean os) {
		double load = os.getCpuLoad();

		// Windows не завжди дає завантаження системи (повертає -1),
		// тоді рахуємо частку процесорного часу, спожиту застосунком
		if (load < 0) {
			double cpuTimeMs = os.getProcessCpuTime() / 1_000_000.0;
			double uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
			load = cpuTimeMs / uptimeMs / os.getAvailableProcessors();
		}

		return Math.round(load * 1000) / 10.0;
	}

}
