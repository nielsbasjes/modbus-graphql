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
import graphql.scalars.ExtendedScalars
import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import nl.basjes.modbus.schema.Block
import nl.basjes.modbus.schema.Field
import nl.basjes.modbus.schema.ReturnType.BOOLEAN
import nl.basjes.modbus.schema.ReturnType.DOUBLE
import nl.basjes.modbus.schema.ReturnType.LONG
import nl.basjes.modbus.schema.ReturnType.STRING
import nl.basjes.modbus.schema.ReturnType.STRINGLIST
import nl.basjes.modbus.schema.ReturnType.UNKNOWN
import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.modbus.schema.utils.CodeGeneration
import org.apache.logging.log4j.LogManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Consumer

@Configuration(proxyBeanMethods = false)
class GraphQLSchemaInitializerSchemaDevice(
    val schemaDevice: SchemaDevice,
) : GraphQLTypeVisitorStub() {
    // References on how this works (thanks to Brad Baker https://github.com/bbakerman):
    // https://github.com/spring-projects/spring-graphql/issues/452#issuecomment-1256798212
    // https://www.graphql-java.com/documentation/schema/#changing-schema
    @Bean
    fun addSchemaDeviceGraphQLSchema(): GraphQLTypeVisitor {
        return this
    }

    val blockIds = schemaDevice.blocks.map { CodeGeneration.convertToCodeCompliantName(it.id, false) }

    val blocks: MutableMap<String, Block> = mutableMapOf()
    val fields: MutableMap<String, MutableMap<String, Field>> = mutableMapOf()

    fun Block.gqlId() = CodeGeneration.convertToCodeCompliantName(this.id, false)
    fun Block.gqlType() = CodeGeneration.convertToCodeCompliantName(this.id, true)
    fun Field.gqlId() = CodeGeneration.convertToCodeCompliantName(this.id, false)

    init {
        schemaDevice.blocks.forEach { block ->
            blocks[block.gqlId()] = block
            val blockMap = fields.computeIfAbsent(block.gqlId()) { mutableMapOf() }
            block.fields.forEach { field ->
                blockMap[field.gqlId()] = field
            }
        }
    }

    override fun visitGraphQLObjectType(
        objectType: GraphQLObjectType,
        context: TraverserContext<GraphQLSchemaElement?>
    ): TraversalControl? {
        val codeRegistry =
            context.getVarFromParents(GraphQLCodeRegistry.Builder::class.java)

        if (objectType.name == "DeviceData") {
            LogManager.getLogger(GraphQLSchemaInitializerSchemaDevice::class.java)
                .info("Adding the `blocks` to the GraphQL Query.")

            blocks.forEach { (id, block) ->
                // New type
                val blockTypeBuilder = GraphQLObjectType
                    .newObject()
                    .name(block.gqlType())
                    .description(block.description)

                block.fields.forEach { field ->
                    val gqlType = when(field.returnType) {
                        DOUBLE      ->  Scalars.GraphQLFloat
                        LONG        ->  Scalars.GraphQLInt // FIXME: ExtendedScalars.GraphQLLong
                        STRING      ->  Scalars.GraphQLString
                        STRINGLIST  ->  Scalars.GraphQLString // TODO( "List<String>" )
                        BOOLEAN     ->  Scalars.GraphQLBoolean
                        UNKNOWN     ->  throw IllegalArgumentException("The \"Unknown\" return type cannot be used")
                    }

                    val gqlField = GraphQLFieldDefinition
                        .newFieldDefinition()
                        .name(field.gqlId())
                        .description(field.description)
                        .type(gqlType)
                        .build()

                    blockTypeBuilder.field(gqlField)
                }

                // FIXME: NOTE: All data fetchers are the default getters of the Version instance.

                // New "field" for the block to be put in the DeviceData
                val blockType = GraphQLFieldDefinition.newFieldDefinition()
                    .name(id)
                    .description(block.description)
                    .type(blockTypeBuilder.build())
                    .build()

                // Adding an extra field with a type and a data fetcher
                val updatedDeviceData =
                    objectType.transform { it.field(blockType) }

                codeRegistry.dataFetcher(
                    FieldCoordinates.coordinates(objectType.name, blockType.name),
                    DataFetcher { env: DataFetchingEnvironment -> block }
                )
                return changeNode(context, updatedDeviceData)
            }

        }

        return TraversalControl.CONTINUE
    }
}
