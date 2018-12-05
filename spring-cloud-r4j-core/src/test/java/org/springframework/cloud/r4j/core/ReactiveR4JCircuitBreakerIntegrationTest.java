/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.r4j.core;

import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import reactor.core.publisher.Mono;

import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.Assert.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Ryan Baxter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = R4JCircuitBreakerIntegrationTest.Application.class)
@DirtiesContext
public class ReactiveR4JCircuitBreakerIntegrationTest {
	@Configuration
	@EnableAutoConfiguration
	@RestController
	protected static class Application {
		@RequestMapping("/slow")
		public Mono<String> slow() throws InterruptedException {
			return Mono.just("slow").delayElement(Duration.ofSeconds(3));
		}

		@GetMapping("/normal")
		public Mono<String> normal() {
			return Mono.just("normal");
		}

		@Bean
		public R4JConfigFactory configFactory() {
			return new R4JConfigFactory.DefaultR4JConfigFactory(){
				@Override
				public TimeLimiterConfig getTimeLimiterConfig(String id) {
					return TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build();
				}
			};
		}

		@Service
		public static class DemoControllerService {
			private int port = 0;
			private ReactiveCircuitBreakerFactory cbFactory;


			public DemoControllerService(ReactiveCircuitBreakerFactory cbFactory) {
				this.cbFactory = cbFactory;
			}

			public Mono<String> slow() {
				return cbFactory.create("slow").run(WebClient.builder().baseUrl("http://localhost:" + port).build()
						.get().uri("/slow").retrieve().bodyToMono(String.class), t -> {
					t.printStackTrace();
					return Mono.just("fallback");
				});
			}

			public Mono<String> normal() {
				return cbFactory.create("normal").run(WebClient.builder().baseUrl("http://localhost:" + port).build()
						.get().uri("/normal").retrieve().bodyToMono(String.class), t -> {
					t.printStackTrace();
					return Mono.just("fallback");
				});
			}

			public void setPort(int port) {
				this.port = port;
			}
		}
	}

	@Autowired
	R4JCircuitBreakerIntegrationTest.Application.DemoControllerService service;

	@Test
	public void testSlow() {
		assertEquals("fallback", service.slow());
	}

	@Test
	public void testNormal() {
		assertEquals("normal", service.normal());
	}
}
