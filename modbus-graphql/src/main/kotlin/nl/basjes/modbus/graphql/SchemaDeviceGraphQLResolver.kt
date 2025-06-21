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

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import nl.basjes.modbus.schema.Block
import nl.basjes.modbus.schema.Field
import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.modbus.schema.utils.CodeGeneration
import org.apache.commons.lang3.ThreadUtils.sleep
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Controller
class SchemaDeviceGraphQLResolver(
    val schemaDevice: SchemaDevice,
) {

    // Maps the GraphQL block type(!!), field name to the actual Field
    val fields: MutableMap<String, MutableMap<String, Field>> = mutableMapOf()

    fun Block.gqlId() = CodeGeneration.convertToCodeCompliantName(this.id, false)
    fun Block.gqlType() = CodeGeneration.convertToCodeCompliantName(this.id, true)
    fun Field.gqlId() = CodeGeneration.convertToCodeCompliantName(this.id, false)

    init {
        schemaDevice.blocks.forEach { block ->
            val blockMap = mutableMapOf<String, Field>()
            fields[block.gqlType()] = blockMap
            block.fields.forEach { field ->
                blockMap[field.gqlId()] = field
            }
        }
    }

    fun List<Field>.toStr() = this.joinToString(", ") { "${it.block.id} - ${it.id}" }


    private fun modbusFields(dataFetchingEnvironment: DataFetchingEnvironment): List<Field> {
        val selectedFields = dataFetchingEnvironment.selectionSet.fields
            .filter { it.type is GraphQLScalarType }
            .filter { it.parentField.type is GraphQLObjectType }
            .filter { (it.parentField.type as GraphQLObjectType).name != "DeviceData" }
            .map { Pair( (it.parentField.type as GraphQLObjectType).name, it.name) }

        println("Query with GQL fields ${selectedFields.joinToString(",")}")
        val modbusFields = selectedFields.mapNotNull { (block, field) -> fields[block]?.get(field) }
        println("Query with Modbus fields ${modbusFields.toStr()}")
        return modbusFields
    }

    @QueryMapping("deviceData")
    fun queryData(
        @Argument("maxAgeMs") maxAgeMs: Int = 0,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): DeviceData {
        val modbusFields = modbusFields(dataFetchingEnvironment)

        modbusFields.forEach { it.need() }
        val start = Instant.now().toEpochMilli()
        schemaDevice.update(maxAgeMs.toLong())
        val stop = Instant.now().toEpochMilli()
        println("TIMER: Query: DURATION ${stop-start} ")
        modbusFields.forEach { it.unNeed() }

        return DeviceData(schemaDevice)
    }

    @SubscriptionMapping("deviceData")
    fun streamData(
        @Argument("intervalMs") intervalMs: Int = 1000,
        @Argument("maxAgeMs")   maxAgeMs: Int = 0,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): Flux<DeviceData> {
        val subscriberId = UUID.randomUUID().toString()

        if (intervalMs < 500 || intervalMs > 60000) {
            throw IllegalArgumentException("IntervalMs must be between 500 ms (0.5 seconds) and 60000 ms (60 seconds)")
        }

        val modbusFields = modbusFields(dataFetchingEnvironment)

        println("START: Subscription ${subscriberId}: Modbus fields ${modbusFields.toStr()}")

        modbusFields.forEach { it.need() }

        return Flux
            .interval(
//                Duration.ofMillis(timeToNextMultiple(intervalMs.toLong() - 20) ),
                Duration.ofMillis(intervalMs.toLong()))
            .map {
                println("TIMER: Subscription ${subscriberId}: ${Instant.now()} ")
//                sleep(Duration.ofMillis(timeToNextMultiple(intervalMs.toLong())))
                val start = Instant.now().toEpochMilli()
                schemaDevice.update(maxAgeMs.toLong())
                val stop = Instant.now().toEpochMilli()
                println("TIMER: Subscription ${subscriberId}: DURATION ${stop-start} ")
                DeviceData(schemaDevice)
            }
            .doFinally {
                println("STOP: Subscription ${subscriberId}: Modbus fields ${modbusFields.toStr()}")
                modbusFields.forEach { it.unNeed() }
            }
    }
}

fun timeToNextMultiple(intervalMs: Long): Long {
    val now = System.currentTimeMillis()
    val roundedNext = ((now / intervalMs) + 1) * intervalMs
    return roundedNext - now
}

