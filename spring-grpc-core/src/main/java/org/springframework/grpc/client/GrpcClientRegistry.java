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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	private Map<Class<?>, StubFactory<?>> factoriesByClass = new HashMap<>();

	private Map<String, DeferredBeanDefinition<?>> beans = new HashMap<>();

	private final GenericApplicationContext context;

	public GrpcClientRegistry(GenericApplicationContext context) {
		this.context = context;
		stubs(new BlockingStubFactory());
		stubs(new FutureStubFactory());
		stubs(new ReactorStubFactory());
		stubs(new SimpleStubFactory());
		SpringFactoriesLoader.loadFactories(StubFactory.class, getClass().getClassLoader()).forEach(this::stubs);
	}

	public void close() {
		for (Map.Entry<String, DeferredBeanDefinition<?>> entry : this.beans.entrySet()) {
			cheekyRegisterBean(entry.getKey(), entry.getValue().type(), entry.getValue().supplier());
		}
		this.beans.clear();
	}

	@SuppressWarnings("unchecked")
	private <T> void cheekyRegisterBean(String key, Class<?> type, Supplier<?> supplier) {
		Supplier<T> real = (Supplier<T>) supplier;
		Class<T> stub = (Class<T>) type;
		this.context.registerBean(key, stub, real, bd -> bd.setLazyInit(true));
	}

	public GrpcClientRegistry stubs(StubFactory<? extends AbstractStub<?>> factory) {
		if (this.factoriesByClass.containsKey(factory.getClass())) {
			this.factories.remove(this.factoriesByClass.get(factory.getClass()));
		}
		this.factories.add(factory);
		this.factoriesByClass.put(factory.getClass(), factory);
		return this;
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
		AnnotationAwareOrderComparator.sort(this.factories);
		for (StubFactory<? extends AbstractStub<?>> factory : this.factories) {
			if (factory.supports(type)) {
				this.beans.put(beanName,
						new DeferredBeanDefinition<>(type, () -> type.cast(factory.create(channel, type))));
				return;
			}
		}
		throw new IllegalArgumentException("Unsupported stub type: " + type);
	}

	private GrpcChannelFactory channels() {
		return this.context.getBean(GrpcChannelFactory.class);
	}

	private static record DeferredBeanDefinition<T extends AbstractStub<?>>(Class<T> type, Supplier<T> supplier) {
	}

	public class GrpcClientGroup {

		private ClasspathScanner scanner = new ClasspathScanner();

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

		public <T extends StubFactory<?>> GrpcClientRegistry scan(Class<T> type, Class<?>... basePackageClasses) {
			String[] basePackages = new String[basePackageClasses.length];
			for (int i = 0; i < basePackageClasses.length; i++) {
				basePackages[i] = ClassUtils.getPackageName(basePackageClasses[i]);
			}
			return scan(type, basePackages);
		}

		public <T extends StubFactory<?>> GrpcClientRegistry scan(Class<T> type, String... basePackages) {
			for (String basePackage : basePackages) {
				for (Class<?> stub : this.scanner.scan(basePackage, AbstractStub.class)) {
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
