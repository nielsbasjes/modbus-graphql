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
import nl.basjes.modbus.schema.Block
import nl.basjes.modbus.schema.Field
import nl.basjes.modbus.schema.ReturnType.BOOLEAN
import nl.basjes.modbus.schema.ReturnType.DOUBLE
import nl.basjes.modbus.schema.ReturnType.LONG
import nl.basjes.modbus.schema.ReturnType.STRING
import nl.basjes.modbus.schema.ReturnType.STRINGLIST
import nl.basjes.modbus.schema.ReturnType.UNKNOWN
import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.modbus.schema.get
import nl.basjes.modbus.schema.utils.CodeGeneration
import org.apache.logging.log4j.LogManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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

    fun Block.gqlId() = CodeGeneration.convertToCodeCompliantName(this.id, false)
    fun Block.gqlType() = CodeGeneration.convertToCodeCompliantName(this.id, true)
    fun Field.gqlId() = CodeGeneration.convertToCodeCompliantName(this.id, false)

    override fun visitGraphQLObjectType(
        objectType: GraphQLObjectType,
        context: TraverserContext<GraphQLSchemaElement?>
    ): TraversalControl? {
        val codeRegistry =
            context.getVarFromParents(GraphQLCodeRegistry.Builder::class.java)

        // ======================================================================================

        if (objectType.name == "DeviceData") {
            LogManager.getLogger(GraphQLSchemaInitializerSchemaDevice::class.java)
                .info("Adding the `blocks` to the GraphQL Query.")

            val allNewBlocks = mutableListOf<GraphQLFieldDefinition>()

            schemaDevice.blocks.forEach { block ->

                // New type
                val blockTypeBuilder = GraphQLObjectType
                    .newObject()
                    .name(block.gqlType())
                    .description(block.description)

                val allGqlFields = mutableListOf<Pair<GraphQLFieldDefinition, Field>>()

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

                    allGqlFields.add(gqlField to field)

                    blockTypeBuilder.field(gqlField)
                }

                // New "field" for the block to be put in the DeviceData
                val blockType = GraphQLFieldDefinition.newFieldDefinition()
                    .name(block.gqlId())
                    .description(block.description)
                    .type(blockTypeBuilder.build())
                    .build()

                allNewBlocks.add(blockType)

                // Now add the datafetcher for the block
                codeRegistry.dataFetcher(
                    FieldCoordinates.coordinates(objectType.name, blockType.name),
                    DataFetcher {
                        block
                    }
                )

                // Now add all the datafetchers for the fields
                allGqlFields.forEach { (gqlField, field) ->
                    val fieldCoordinates = FieldCoordinates.coordinates(block.gqlType(), gqlField.name)
                    when(field.returnType) {
                        DOUBLE      ->  codeRegistry.dataFetcher(
                            fieldCoordinates,
                            DataFetcher {
                                field.doubleValue
                            }
                        )
                        LONG        ->  codeRegistry.dataFetcher(
                            fieldCoordinates,
                            DataFetcher {
                                field.longValue
                            }
                        )
                        STRING      ->  codeRegistry.dataFetcher(
                            fieldCoordinates,
                            DataFetcher {
                                field.stringValue
                            }
                        )
                        STRINGLIST  ->  codeRegistry.dataFetcher(
                            fieldCoordinates,
                            DataFetcher {
                                field.stringListValue
                            }
                        )
                        BOOLEAN     ->  TODO("Boolean")
                        UNKNOWN     ->  throw IllegalArgumentException("The \"Unknown\" return type cannot be used")
                    }
                }
            }

            // Adding an extra field with a type and a data fetcher
            val updatedDeviceData =
                objectType.transform { objectType -> allNewBlocks.forEach { objectType.field(it) } }
            return changeNode(context, updatedDeviceData)
        }

        return TraversalControl.CONTINUE
    }
}
