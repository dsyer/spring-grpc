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
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
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
			registerBean(entry.getKey(), entry.getValue().type(), entry.getValue().supplier());
		}
	}

	Set<Class<?>> getTypes() {
		return this.beans.values().stream().map(DeferredBeanDefinition::type).collect(Collectors.toSet());
	}

	@SuppressWarnings("unchecked")
	private <T> void registerBean(String key, Class<?> type, Supplier<?> supplier) {
		Supplier<T> real = (Supplier<T>) supplier;
		Class<T> stub = (Class<T>) type;
		this.context.registerBean(key, stub, real, bd -> {
			bd.setLazyInit(true);
			bd.setAttribute(BeanRegistrationAotProcessor.IGNORE_REGISTRATION_ATTRIBUTE, true);
		});
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

	private <T extends AbstractStub<?>> void preRegisterBean(String beanName, Class<T> type,
			Supplier<T> clientFactory) {
		this.beans.put(beanName, new DeferredBeanDefinition<>(type, clientFactory));
	}

	private <T extends AbstractStub<?>> void preRegisterType(String beanName, Supplier<ManagedChannel> channel,
			Class<? extends StubFactory<?>> factoryType, Class<T> type) {
		StubFactory<? extends AbstractStub<?>> factory = null;
		if (factoryType != null) {
			factory = this.factoriesByClass.get(factoryType);
			if (!factory.supports(type)) {
				factory = null;
			}
		}
		else {
			AnnotationAwareOrderComparator.sort(this.factories);
			for (StubFactory<? extends AbstractStub<?>> value : this.factories) {
				if (value.supports(type)) {
					factory = value;
					break;
				}
			}
		}
		if (factory != null) {
			StubFactory<? extends AbstractStub<?>> value = factory;
			this.beans.put(beanName, new DeferredBeanDefinition<>(type, () -> type.cast(value.create(channel, type))));
			return;
		}
		// Ignore unsupported types
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

		private Class<? extends StubFactory<?>> factory;

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
			preRegisterBean(beanName, type, () -> factory.apply(this.channel.get()));
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
				preRegisterType(beanName, this.channel, this.factory, stub);
			}
			return GrpcClientRegistry.this;
		}

		public <T extends StubFactory<?>> GrpcClientRegistry packageClasses(Class<?>... basePackageClasses) {
			String[] basePackages = new String[basePackageClasses.length];
			for (int i = 0; i < basePackageClasses.length; i++) {
				basePackages[i] = ClassUtils.getPackageName(basePackageClasses[i]);
			}
			return packages(basePackages);
		}

		public <T extends StubFactory<?>> GrpcClientRegistry packages(String... basePackages) {
			for (String basePackage : basePackages) {
				for (Class<?> stub : this.scanner.scan(basePackage, AbstractStub.class)) {
					register(stub);
				}
			}
			return GrpcClientRegistry.this;
		}

		public <T extends StubFactory<?>> GrpcClientGroup scan(Class<T> factory) {
			GrpcClientGroup group = new GrpcClientGroup(this.channel);
			group.prefix = this.prefix;
			group.factory = factory;
			return group;
		}

		public GrpcClientGroup prefix(String prefix) {
			GrpcClientGroup group = new GrpcClientGroup(this.channel);
			group.prefix = prefix;
			return group;
		}

	}

}
