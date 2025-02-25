package org.springframework.grpc.sample;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.experimental.boot.server.exec.CommonsExecWebServerFactoryBean;
import org.springframework.experimental.boot.server.exec.MavenClasspathEntry;
import org.springframework.experimental.boot.test.context.DynamicProperty;
import org.springframework.experimental.boot.test.context.EnableDynamicProperty;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "spring.grpc.client.default-channel.address=static://0.0.0.0:${local.grpc.port}")
@DirtiesContext
public class GrpcClientApplicationTests {

	public static void main(String[] args) {
		SpringApplication.from(GrpcClientApplication::main).with(ExtraConfiguration.class).run(args);
	}

	@Test
	void contextLoads() {
	}

	@TestConfiguration(proxyBeanMethods = false)
	@EnableDynamicProperty
	static class ExtraConfiguration {

		@Bean
		@DynamicProperty(name = "local.grpc.port", value = "port")
		static CommonsExecWebServerFactoryBean grpcServer() {
			return CommonsExecWebServerFactoryBean.builder()
				.classpath(classpath -> classpath
					.entries(new MavenClasspathEntry("org.springframework.grpc:grpc-server-sample:0.4.0-SNAPSHOT"))
					.files("target/test-classes"));
		}

	}

}
