package org.springframework.grpc.server;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.grpc.ForwardingServerBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.servlet.jakarta.ServletServerBuilder;

public class InternalServerBuilder extends ForwardingServerBuilder<InternalServerBuilder> {

	private final ServletServerBuilder delegate;

	public InternalServerBuilder(ServletServerBuilder delegate) {
		this.delegate = delegate;
	}

	@Override
	public Server build() {
		return new NoopServer();
	}

	@Override
	protected ServerBuilder<?> delegate() {
		return this.delegate;
	}

}

class NoopServer extends Server {

	private boolean shutdown = false;

	@Override
	public Server start() throws IOException {
		return this;
	}

	@Override
	public Server shutdown() {
		this.shutdown = true;
		return this;
	}

	@Override
	public Server shutdownNow() {
		return shutdown();
	}

	@Override
	public boolean isShutdown() {
		return this.shutdown;
	}

	@Override
	public boolean isTerminated() {
		return this.shutdown;
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		shutdown();
		return isShutdown();
	}

	@Override
	public void awaitTermination() throws InterruptedException {
		shutdown();
	}

}