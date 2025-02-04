package org.springframework.grpc.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.grpc.client.EnableGrpcClients;
import org.springframework.grpc.client.GrpcClient;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.grpc.sample.proto.SimpleGrpc;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext
// @AutoConfigureInProcessTransport
public class GrpcServerApplicationTests {

	private static Log log = LogFactory.getLog(GrpcServerApplicationTests.class);

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run();
	}

	@Autowired
	private SimpleGrpc.SimpleBlockingStub stub;

	@Test
	void contextLoads() {
	}

	@Test
	void serverResponds() {
		log.info("Testing");
		HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Alien").build());
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@TestConfiguration
	@EnableGrpcClients(@GrpcClient(name = "0.0.0.0:${local.grpc.port:9090}", basePackageTypes =  SimpleGrpc.class))
	static class ExtraConfiguration {
	}

}
