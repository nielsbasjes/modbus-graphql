/*
 * Modbus Schema Toolkit
 * Copyright (C) 2019-2025 Niels Basjes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.basjes.modbus.graphql

import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLTypeVisitor
import graphql.schema.SchemaTransformer
import graphql.schema.idl.SchemaGenerator
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

@Configuration(proxyBeanMethods = false)
class GraphQLSchemaCustomizer (
    val context: ApplicationContext,
) {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer {
        return RuntimeWiringConfigurer {
            it
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.DateTime)
        }
    }

    @Bean
    fun graphQlSourceBuilderCustomizer(): GraphQlSourceBuilderCustomizer {
        return GraphQlSourceBuilderCustomizer { it
                .schemaFactory { typeDefinitionRegistry, runtimeWiring ->
                    // First we create the base Schema.
                    var schema = SchemaGenerator()
                        .makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)

                    // The ordering is essentially "Random" which may cause problems if elements are deleted
                    // or if 2 try to add the same thing.
                    for (visitor in context.getBeanProvider(GraphQLTypeVisitor::class.java)) {
                        schema = SchemaTransformer.transformSchema(schema, visitor)
                    }
                    schema
                }
        }
    }
}
