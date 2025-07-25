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

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLFloat
import graphql.Scalars.GraphQLString
import graphql.scalars.ExtendedScalars.GraphQLLong
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates.coordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.GraphQLList.list
import graphql.schema.GraphQLNonNull.nonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeVisitor
import graphql.schema.GraphQLTypeVisitorStub
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

@Configuration(proxyBeanMethods = false)
class GraphQLSchemaModbusSchema(
    val schemaDevice: SchemaDevice,
) : GraphQLTypeVisitorStub() {
    // References on how this works (thanks to Brad Baker https://github.com/bbakerman):
    // https://github.com/spring-projects/spring-graphql/issues/452#issuecomment-1256798212
    // https://www.graphql-java.com/documentation/schema/#changing-schema
    @Bean
    fun addSchemaDeviceGraphQLSchema(): GraphQLTypeVisitor {
        return this
    }

    fun Block.gqlId()   = CodeGeneration.convertToCodeCompliantName(this.id, false)
    fun Block.gqlType() = CodeGeneration.convertToCodeCompliantName(this.id,  true)
    fun Field.gqlId()   = CodeGeneration.convertToCodeCompliantName(this.id, false)

    override fun visitGraphQLObjectType(
        objectType: GraphQLObjectType,
        context: TraverserContext<GraphQLSchemaElement?>
    ): TraversalControl {
        val codeRegistry =
            context.getVarFromParents(GraphQLCodeRegistry.Builder::class.java)

        // ------------------------------------------

        if (objectType.name == "DeviceData") {
            LogManager.getLogger().info("Adding the `blocks` to the GraphQL Query.")

            val allNewBlocks = mutableListOf<GraphQLFieldDefinition>()

            schemaDevice.blocks.forEach { block ->

                // New type
                val blockTypeBuilder = GraphQLObjectType
                    .newObject()
                    .name(block.gqlType())
                    .description(block.description)

                val allGqlFields = mutableListOf<Pair<GraphQLFieldDefinition, Field>>()

                block.fields.forEach { field ->
                    // Only include non-system fields
                    if (!field.isSystem) {
                        val gqlFieldBuilder = newFieldDefinition()
                            .name(field.gqlId())
                            .description(field.description + (if (field.unit.isBlank() || field.description.endsWith("(${field.unit})")) "" else " (${field.unit})"))

                        when (field.returnType) {
                            DOUBLE     -> gqlFieldBuilder.type(GraphQLFloat)
                            LONG       -> gqlFieldBuilder.type(GraphQLLong)
                            STRING     -> gqlFieldBuilder.type(GraphQLString)
                            STRINGLIST -> gqlFieldBuilder.type(list(nonNull(GraphQLString)))
                            BOOLEAN    -> gqlFieldBuilder.type(GraphQLBoolean)
                            UNKNOWN    -> throw IllegalArgumentException("The \"Unknown\" return type cannot be used")
                        }

                        val gqlField = gqlFieldBuilder.build()
                        allGqlFields.add(gqlField to field)

                        blockTypeBuilder.field(gqlField)
                    }
                }

                // Only include Blocks that have any fields
                if (!allGqlFields.isEmpty()) {
                    // New "field" for the block to be put in the DeviceData
                    val blockType = newFieldDefinition()
                        .name(block.gqlId())
                        .description(block.description)
                        .type(blockTypeBuilder.build())
                        .build()

                    allNewBlocks.add(blockType)

                    // Now add the datafetcher for the block
                    codeRegistry.dataFetcher(
                        coordinates(objectType.name, blockType.name),
                        DataFetcher {
                            block
                        }
                    )

                    // Now add all the datafetchers for the fields
                    allGqlFields.forEach { (gqlField, field) ->
                        codeRegistry.dataFetcher(
                            coordinates(block.gqlType(), gqlField.name),
                            when (field.returnType) {
                                DOUBLE     -> DataFetcher { field.doubleValue     }
                                LONG       -> DataFetcher { field.longValue       }
                                STRING     -> DataFetcher { field.stringValue     }
                                STRINGLIST -> DataFetcher { field.stringListValue }
                                BOOLEAN    -> DataFetcher { field.booleanValue    }
                                UNKNOWN    -> throw IllegalArgumentException("The \"Unknown\" return type cannot be used")
                            }
                        )
                    }
                }
            }

            // Only submit changes if we have any
            if (allNewBlocks.isNotEmpty()) {
                // Adding an extra field with a type and a data fetcher
                val updatedDeviceData =
                    objectType.transform { objectType -> allNewBlocks.forEach { objectType.field(it) } }
                return changeNode(context, updatedDeviceData)
            }
        }

        // ------------------------------------------

        return TraversalControl.CONTINUE
    }
}
