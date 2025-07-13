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

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import nl.basjes.modbus.schema.Block
import nl.basjes.modbus.schema.Field
import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.modbus.schema.fetcher.ModbusQuery
import nl.basjes.modbus.schema.utils.CodeGeneration
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.lang.Thread.sleep
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID
import kotlin.time.DurationUnit

@Controller
class SchemaDeviceGraphQLResolver(
    val schemaDevice: SchemaDevice,
) {

    val logger = LoggerFactory.getLogger("Modbus GraphQL")

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

    fun List<Field>.toStr() = this.joinToString(", ") { "(${it.block.id} | ${it.id})" }

    private fun requestedFields(dataFetchingEnvironment: DataFetchingEnvironment): List<Field> {
        val selectedFields = dataFetchingEnvironment.selectionSet.fields
            .filter { it.type is GraphQLScalarType }
            .filter { it.parentField.type is GraphQLObjectType }
            .filter { (it.parentField.type as GraphQLObjectType).name != "DeviceData" }
            .map { Pair( (it.parentField.type as GraphQLObjectType).name, it.name) }

        logger.trace("Query with GQL fields {}", selectedFields.joinToString(","))
        val modbusFields = selectedFields.mapNotNull { (block, field) -> fields[block]?.get(field) }
        logger.trace("Query with Modbus fields {}", modbusFields.toStr())
        return modbusFields
    }

    fun maxAge(maxAgeMs: Int): Long {
        if (maxAgeMs < 0 || maxAgeMs > 60000) {
            throw IllegalArgumentException("maxAgeMs must be a between 0 ms and 60000 ms (60 seconds)")
        }
        return maxAgeMs.toLong()
    }

    fun interval(intervalMs: Int): Long {
        if (intervalMs < 500 || intervalMs > 60000) {
            throw IllegalArgumentException("IntervalMs must be between 500 ms (0.5 seconds) and 60000 ms (60 seconds)")
        }
        return intervalMs.toLong()
    }

    // ------------------------------------------

    @QueryMapping("deviceData")
    fun queryDeviceData(
        @Argument("maxAgeMs") maxAgeMs: Int,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): DeviceData {
        // Check and cleanup input parameters
        val usedMaxAgeMs = maxAge(maxAgeMs)

        val requestedFields = requestedFields(dataFetchingEnvironment)

        requestedFields.forEach { it.need() }

        val start = Instant.now()
        logger.trace("Query: START@    {}: Modbus fields {}", start, requestedFields.toStr())
        val modbusQueries = schemaDevice.update(usedMaxAgeMs)
        val stop = Instant.now().toEpochMilli()
        val duration =  (stop - start.toEpochMilli()).toInt()
        logger.trace("Query: COMPLETE@ {} DURATION {}ms to do {} Modbus Requests",  Instant.now(), duration, modbusQueries.size)

        requestedFields.forEach { it.unNeed() }

        return DeviceData(schemaDevice, requestedFields, modbusQueries, start.atZone(UTC), duration)
    }

    // ------------------------------------------

    @SubscriptionMapping("deviceData")
    fun streamDeviceData(
        @Argument("intervalMs") intervalMs: Int,
        @Argument("maxAgeMs")   maxAgeMs: Int,
        @Argument("queryAtRoundInterval") queryAtRoundInterval: Boolean,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): Flux<DeviceData> {
        // Check and cleanup input parameters
        val usedIntervalMs = interval(intervalMs)
        val usedMaxAgeMs = maxAge(maxAgeMs)

        val subscriberId = UUID.randomUUID().toString()

        val requestedFields = requestedFields(dataFetchingEnvironment)

        logger.trace("START: Subscription ${subscriberId}: Modbus fields ${requestedFields.toStr()}")

        return Flux
            .interval(
                Duration.ofMillis(usedIntervalMs),
            )
            .publishOn(Schedulers.boundedElastic())
            .map {
                if (queryAtRoundInterval) {
                    sleep(timeToNextMultiple(intervalMs.toLong()))
                }
                requestedFields.forEach { it.need() }
                val start = Instant.now()
                logger.trace("TIMER: Subscription {}: START@    {}", subscriberId, start)
                val modbusQueries = schemaDevice.update(usedMaxAgeMs)
                val stop = Instant.now().toEpochMilli()
                val duration =  (stop - start.toEpochMilli()).toInt()
                logger.trace("TIMER: Subscription {}: COMPLETE@ {} DURATION {}ms to do {} Modbus Requests", subscriberId, Instant.now(), duration, modbusQueries.size)
                requestedFields.forEach { it.unNeed() }
                DeviceData(schemaDevice, requestedFields, modbusQueries, start.atZone(UTC), duration)
            }
            .doFinally {
                logger.trace("STOP: Subscription ${subscriberId}: Modbus fields ${requestedFields.toStr()}")
            }
    }

    fun timeToNextMultiple(intervalMs: Long): Long {
        val now = System.currentTimeMillis()
        val roundedNext = ((now / intervalMs) + 1) * intervalMs
        return roundedNext - now
    }

    // ------------------------------------------

    @GraphQlExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        errorBuilder: GraphqlErrorBuilder<*>,
        ex: IllegalArgumentException,
    ): GraphQLError =
        errorBuilder
            .message(ex.message)
            .errorType(ErrorType.BAD_REQUEST)
            .build()

    @SchemaMapping("duration")
    fun getModbusQueryDuration(modbusQuery: ModbusQuery): Int {
        val duration = modbusQuery.duration
        if (duration == null) {
            return 0
        }
        return duration.toLong(DurationUnit.MILLISECONDS).toInt()
    }

    @SchemaMapping("status")
    fun getModbusQueryStatus(modbusQuery: ModbusQuery): String = modbusQuery.status.name

    @SchemaMapping("fields")
    fun getModbusQueryFields(modbusQuery: ModbusQuery): List<String> = modbusQuery.fields.map { "${it.block.id}|${it.id}" }

}
