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

import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

import io.grpc.stub.AbstractStub;

public class GrpcClientConfiguration implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata meta, BeanDefinitionRegistry registry) {
		Set<AnnotationAttributes> attrs = meta.getMergedRepeatableAnnotationAttributes(GrpcClient.class,
				EnableGrpcClients.class, false);
		for (AnnotationAttributes attr : attrs) {
			register(registry, attr, meta.getClassName() + ".");
		}
		String name = GrpcClientRegistryPostProcessor.class.getName();
		if (!registry.containsBeanDefinition(name)) {
			registry.registerBeanDefinition(name, new RootBeanDefinition(GrpcClientRegistryPostProcessor.class));
		}
	}

	private void register(BeanDefinitionRegistry registry, AnnotationAttributes attr, String stem) {
		String value = attr.getString("target");
		if (!StringUtils.hasText(value)) {
			value = "localhost:9090";
		}
		String prefix = attr.getString("prefix");
		Class<?>[] types = attr.getClassArray("types");
		Class<? extends AbstractStub<?>> type = attr.getClass("type");
		Class<?>[] basePackageTypes = attr.getClassArray("basePackageTypes");
		String[] basePackages = attr.getStringArray("basePackages");
		RootBeanDefinition beanDef = new RootBeanDefinition(SimpleGrpcClientRegistryCustomizer.class);
		beanDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		String name = value;
		beanDef.setInstanceSupplier(
				() -> new SimpleGrpcClientRegistryCustomizer(name, prefix, types, type, basePackageTypes,
						basePackages));
		registry.registerBeanDefinition(stem + value, beanDef);
	}

	static class SimpleGrpcClientRegistryCustomizer implements GrpcClientRegistryCustomizer {

		private String value;
		private Class<?>[] types;
		private String prefix;
		private Class<?>[] basePackageTypes;
		private Class<? extends AbstractStub<?>> type;
		private String[] basePackages;

		public SimpleGrpcClientRegistryCustomizer(String value, String prefix, Class<?>[] types,
				Class<? extends AbstractStub<?>> type, Class<?>[] basePackageTypes, String[] basePackages) {
			this.value = value;
			this.prefix = prefix;
			this.types = types;
			this.type = type;
			this.basePackageTypes = basePackageTypes;
			this.basePackages = basePackages;
		}

		@Override
		public void customize(GrpcClientRegistry registry) {
			if (this.types.length > 0) {
				registry.channel(this.value).prefix(prefix).register(this.types);
			}
			if (this.basePackageTypes.length > 0) {
				registry.channel(this.value).prefix(prefix).scan(this.type, this.basePackageTypes);
			}
			if (this.basePackages.length > 0) {
				registry.channel(this.value).prefix(prefix).scan(this.type, this.basePackages);
			}
		}

	}

}
