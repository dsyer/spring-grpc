package org.springframework.grpc.sample;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.EnableGrpcClients;
import org.springframework.grpc.client.GrpcClient;
import org.springframework.grpc.client.GrpcClientRegistryCustomizer;
import org.springframework.grpc.client.security.BasicAuthenticationInterceptor;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.grpc.sample.proto.SimpleGrpc;
import org.springframework.test.annotation.DirtiesContext;

import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.StreamObserver;

@SpringBootTest(properties = { "spring.grpc.server.port=0",
		"spring.grpc.client.channels.stub.address=static://0.0.0.0:${local.grpc.port}" })
@DirtiesContext
public class GrpcServerApplicationTests {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run(args);
	}

	@Autowired
	@Qualifier("unsecuredSimpleBlockingStub")
	private SimpleGrpc.SimpleBlockingStub stub;

	@Autowired
	private ServerReflectionGrpc.ServerReflectionStub reflect;

	@Autowired
	@Qualifier("simpleBlockingStub")
	private SimpleGrpc.SimpleBlockingStub basic;

	@Test
	void contextLoads() {
	}

	@Test
	void unauthenticated() {
		StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
				() -> stub.sayHello(HelloRequest.newBuilder().setName("Alien").build()));
		assertEquals(Code.UNAUTHENTICATED, exception.getStatus().getCode());
	}

	@Test
	void anonymous() throws Exception {
		AtomicReference<ServerReflectionResponse> response = new AtomicReference<>();
		AtomicBoolean error = new AtomicBoolean();
		StreamObserver<ServerReflectionResponse> responses = new StreamObserver<ServerReflectionResponse>() {
			@Override
			public void onNext(ServerReflectionResponse value) {
				response.set(value);
			}

			@Override
			public void onError(Throwable t) {
				error.set(true);
			}

			@Override
			public void onCompleted() {
			}
		};
		StreamObserver<ServerReflectionRequest> request = reflect.serverReflectionInfo(responses);
		request.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
		request.onCompleted();
		Awaitility.await().until(() -> response.get() != null || error.get());
	}

	@Test
	void unauthauthorized() {
		StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
				() -> basic.streamHello(HelloRequest.newBuilder().setName("Alien").build()).next());
		assertEquals(Code.PERMISSION_DENIED, exception.getStatus().getCode());
	}

	@Test
	void authenticated() {
		HelloReply response = basic.sayHello(HelloRequest.newBuilder().setName("Alien").build());
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@Test
	void basic() {
		HelloReply response = basic.sayHello(HelloRequest.newBuilder().setName("Alien").build());
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@TestConfiguration
	@EnableGrpcClients(@GrpcClient(target = "stub", prefix = "unsecured", types = { SimpleGrpc.SimpleBlockingStub.class,
			ServerReflectionGrpc.ServerReflectionStub.class }))
	static class ExtraConfiguration {

		@Bean
		GrpcClientRegistryCustomizer basicStubs() {
			return registry -> registry
					.channel("stub",
							ChannelBuilderOptions.defaults()
									.withInterceptors(List.of(new BasicAuthenticationInterceptor("user", "user"))))
					.scan(AbstractBlockingStub.class, SimpleGrpc.class);
		}

	}

}
