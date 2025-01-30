/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.grpc.client;

import java.util.function.Supplier;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.StringUtils;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import io.grpc.stub.AbstractStub;

public class GrpcClientRegistry {
	private final GenericApplicationContext context;

	public GrpcClientRegistry(GenericApplicationContext context) {
		this.context = context;
	}

	public <T extends AbstractStub<?>> void register(Class<T> type, Supplier<T> clientFactory) {
		String beanName = StringUtils.uncapitalize(type.getSimpleName());
		register(beanName, type, clientFactory);
	}

	public <T extends AbstractStub<?>> void register(String beanName, Class<T> type, Supplier<T> clientFactory) {
		context.registerBean(beanName, type, clientFactory, bd -> bd.setLazyInit(true));
	}

	public <T  extends AbstractStub<?>> void register(Supplier<ManagedChannel> channel, Class<?>... types) {
		for (Class<?> type : types) {
			@SuppressWarnings("unchecked")
			Class<T> stub = (Class<T>) type;
			String beanName = StringUtils.uncapitalize(type.getSimpleName());
			registerType(beanName, channel, stub);
		}
	}

	public <T  extends AbstractStub<?>> void registerWithPrefix(String prefix, Supplier<ManagedChannel> channel, Class<?>... types) {
		for (Class<?> type : types) {
			@SuppressWarnings("unchecked")
			Class<T> stub = (Class<T>) type;
			String beanName = prefix + type.getSimpleName();
			registerType(beanName, channel, stub);
		}
	}

	private <T  extends AbstractStub<?>> void registerType(String beanName, Supplier<ManagedChannel> channel, Class<T> type) {
		register(beanName, type, () -> type.cast(create(channel, type)));
	}

	private AbstractStub<?> create(Supplier<ManagedChannel> channel, Class<? extends AbstractStub<?>> type) {
		Class<?> factory = type.getEnclosingClass();
		if (AbstractBlockingStub.class.isAssignableFrom(type)) {
			return (AbstractStub<?>) createStub(channel, factory, AbstractBlockingStub.class, "newBlockingStub");
		} else if (AbstractStub.class.isAssignableFrom(type)) {
			return (AbstractStub<?>) createStub(channel, factory, AbstractStub.class, "newStub");
		} else if (AbstractFutureStub.class.isAssignableFrom(type)) {
			return (AbstractStub<?>) createStub(channel, factory, AbstractFutureStub.class, "newFutureStub");
		} else {
			throw new IllegalArgumentException("Unsupported stub type: " + type);
		}
	}

	private Object createStub(Supplier<ManagedChannel> channel, Class<?> factory, Class<?> type,
			String method) {
		try {
			return factory.getMethod(method,Channel.class).invoke(null, channel.get());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create stub", e);
		}
	}

	public GrpcChannelFactory channels() {
		return context.getBean(GrpcChannelFactory.class);
	}

}
