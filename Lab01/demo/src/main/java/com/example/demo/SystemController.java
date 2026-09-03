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
			double cpuLoad
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
				os.getCpuLoad()
		);
	}

}
