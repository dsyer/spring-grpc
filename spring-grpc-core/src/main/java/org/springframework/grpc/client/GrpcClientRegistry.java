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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.grpc.internal.ClasspathScanner;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractStub;

public class GrpcClientRegistry {

	private List<StubFactory<?>> factories = new ArrayList<>();

	private final GenericApplicationContext context;

	public GrpcClientRegistry(GenericApplicationContext context) {
		this.context = context;
		this.factories.add(new BlockingStubFactory());
		this.factories.add(new FutureStubFactory());
		this.factories.add(new ReactorStubFactory());
		this.factories.add(new SimpleStubFactory());
		SpringFactoriesLoader.loadFactories(StubFactory.class, getClass().getClassLoader())
			.forEach(this.factories::add);
		AnnotationAwareOrderComparator.sort(this.factories);
	}

	public GrpcClientGroup channel(String name) {
		return channel(name, ChannelBuilderOptions.defaults());
	}

	public GrpcClientGroup channel(String name, ChannelBuilderOptions options) {
		return new GrpcClientGroup(() -> channels().createChannel(name, options));
	}

	public GrpcClientGroup channel(Supplier<ManagedChannel> channel) {
		return new GrpcClientGroup(channel);
	}

	private <T extends AbstractStub<?>> void registerBean(String beanName, Class<T> type, Supplier<T> clientFactory) {
		this.context.registerBean(beanName, type, clientFactory, bd -> bd.setLazyInit(true));
	}

	private <T extends AbstractStub<?>> void registerType(String beanName, Supplier<ManagedChannel> channel,
			Class<T> type) {
		registerBean(beanName, type, () -> type.cast(create(channel, type)));
	}

	private AbstractStub<?> create(Supplier<ManagedChannel> channel, Class<? extends AbstractStub<?>> type) {
		for (StubFactory<? extends AbstractStub<?>> factory : this.factories) {
			if (factory.supports(type)) {
				return factory.create(channel, type);
			}
		}
		throw new IllegalArgumentException("Unsupported stub type: " + type);
	}

	private GrpcChannelFactory channels() {
		return this.context.getBean(GrpcChannelFactory.class);
	}

	public class GrpcClientGroup {

		private final Supplier<ManagedChannel> channel;

		private String prefix = "";

		public GrpcClientGroup(Supplier<ManagedChannel> channel) {
			this.channel = channel;
		}

		public <T extends AbstractStub<?>> GrpcClientRegistry register(Class<T> type, Function<Channel, T> factory) {
			String beanName = type.getSimpleName();
			if (StringUtils.hasText(this.prefix)) {
				beanName = this.prefix + beanName;
			}
			else {
				beanName = StringUtils.uncapitalize(beanName);
			}
			registerBean(beanName, type, () -> factory.apply(this.channel.get()));
			return GrpcClientRegistry.this;
		}

		public <T extends AbstractStub<?>> GrpcClientRegistry register(Class<?>... types) {
			for (Class<?> type : types) {
				String beanName = type.getSimpleName();
				if (StringUtils.hasText(this.prefix)) {
					beanName = this.prefix + beanName;
				}
				else {
					beanName = StringUtils.uncapitalize(beanName);
				}
				@SuppressWarnings("unchecked")
				Class<T> stub = (Class<T>) type;
				registerType(beanName, this.channel, stub);
			}
			return GrpcClientRegistry.this;
		}

		public <T extends AbstractStub<?>> GrpcClientRegistry scan(Class<T> type, Class<?>... basePackageClasses) {
			String[] basePackages = new String[basePackageClasses.length];
			for (int i = 0; i < basePackageClasses.length; i++) {
				basePackages[i] = ClassUtils.getPackageName(basePackageClasses[i]);
			}
			return scan(type, basePackages);
		}

		public <T extends AbstractStub<?>> GrpcClientRegistry scan(Class<T> type, String... basePackages) {
			ClasspathScanner scanner = new ClasspathScanner();
			for (String basePackage : basePackages) {
				for (Class<?> stub : scanner.scan(basePackage, type)) {
					register(stub);
				}
			}
			return GrpcClientRegistry.this;
		}

		public GrpcClientGroup prefix(String prefix) {
			GrpcClientGroup group = new GrpcClientGroup(this.channel);
			group.prefix = prefix;
			return group;
		}

	}

}
