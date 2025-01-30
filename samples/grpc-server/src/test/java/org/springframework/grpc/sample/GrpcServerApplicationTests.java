package org.springframework.grpc.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.EnableGrpcClients;
import org.springframework.grpc.client.GrpcClientRegistryCustomizer;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.grpc.sample.proto.SimpleGrpc;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
// @AutoConfigureInProcessTransport
public class GrpcServerApplicationTests {

	private static Log log = LogFactory.getLog(GrpcServerApplicationTests.class);

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run();
	}

	@Autowired
	private SimpleGrpc.SimpleBlockingStub stub;

	@Test
	@DirtiesContext
	void contextLoads() {
	}

	@Test
	@DirtiesContext
	void serverResponds() {
		log.info("Testing");
		HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Alien").build());
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@TestConfiguration
	@EnableGrpcClients
	static class ExtraConfiguration {

		@Bean
		GrpcClientRegistryCustomizer stubs() {
			return registry -> registry.register(
					() -> registry.channels().createChannel("0.0.0.0:${local.grpc.port:9090}"),
					SimpleGrpc.SimpleBlockingStub.class);
		}

	}

}
