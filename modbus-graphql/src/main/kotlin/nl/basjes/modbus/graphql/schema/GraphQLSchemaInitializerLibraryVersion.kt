/*
 * Yet Another UserAgent Analyzer
 * Copyright (C) 2013-2025 Niels Basjes
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
package nl.basjes.modbus.graphql.schema

import graphql.Scalars
import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import nl.basjes.modbus.version.Version
import org.apache.logging.log4j.LogManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Consumer

@Configuration(proxyBeanMethods = false)
class GraphQLSchemaInitializerLibraryVersion : GraphQLTypeVisitorStub() {
    // References on how this works (thanks to Brad Baker https://github.com/bbakerman):
    // https://github.com/spring-projects/spring-graphql/issues/452#issuecomment-1256798212
    // https://www.graphql-java.com/documentation/schema/#changing-schema
    @Bean
    fun addYauaaVersionToGraphQLSchema(): GraphQLTypeVisitor {
        return this
    }

    private fun newField(name: String?, description: String?): GraphQLFieldDefinition {
        return GraphQLFieldDefinition.newFieldDefinition().name(name).description(description)
            .type(Scalars.GraphQLString).build()
    }

    override fun visitGraphQLObjectType(
        objectType: GraphQLObjectType,
        context: TraverserContext<GraphQLSchemaElement?>
    ): TraversalControl? {
        val codeRegistry =
            context.getVarFromParents<GraphQLCodeRegistry.Builder>(GraphQLCodeRegistry.Builder::class.java)

        if (objectType.name == "Query") {
            LogManager.getLogger(GraphQLSchemaInitializerLibraryVersion::class.java)
                .info("Adding the `version` to the GraphQL Query.")

            // New type
            val version = GraphQLObjectType
                .newObject()
                .name("Version")
                .description("The version information of the underlying Yauaa runtime engine.")
                .field(newField("gitCommitId", "The git commit id of the Yauaa engine that is used"))
                .field(newField("gitCommitIdDescribeShort", "The git describe short of the Yauaa engine that is used"))
                .field(newField("buildTimeStamp", "Timestamp when the engine was built."))
                .field(newField("projectVersion", "Version of the Modbus Schema Toolkit"))
                .field(newField("copyright", "Copyright notice of the Yauaa engine that is used"))
                .field(newField("license", "The software license Yauaa engine that is used"))
                .field(newField("url", "Project url"))
                .field(newField("targetJREVersion", "Yauaa was build using for this target JRE version"))
                .build()

            // NOTE: All data fetchers are the default getters of the Version instance.

            // New "field" to be put in Query
            val getVersion = GraphQLFieldDefinition.newFieldDefinition()
                .name("version")
                .description("Returns the version information of the underlying Modbus Schema Toolkit.")
                .type(version)
                .build()

            // Adding an extra field with a type and a data fetcher
            val updatedQuery =
                objectType.transform(Consumer { builder: GraphQLObjectType.Builder? -> builder!!.field(getVersion) })

            val coordinates = FieldCoordinates.coordinates(objectType.name, getVersion.name)
            codeRegistry.dataFetcher(
                coordinates,
                (DataFetcher { env: DataFetchingEnvironment? -> Version.INSTANCE }) as DataFetcher<*>
            )
            return changeNode(context, updatedQuery)
        }

        return TraversalControl.CONTINUE
    }
}
