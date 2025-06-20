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

    @QueryMapping("deviceData")
    fun queryData(
        @Argument("maxAgeMs") maxAgeMs: Int = 0,
        env: DataFetchingEnvironment
    ): DeviceData {
        val selectedFields = env.selectionSet.fields
            .filter { it.type is GraphQLScalarType }
            .filter { it.parentField.type is GraphQLObjectType }
            .filter { (it.parentField.type as GraphQLObjectType).name != "DeviceData" }
            .map { Pair( (it.parentField.type as GraphQLObjectType).name, it.name) }

        println("Query with GQL fields ${selectedFields.joinToString(",")}")

        val modbusFields = selectedFields.mapNotNull { (block, field) -> fields[block]?.get(field) }

        println("Query with Modbus fields ${modbusFields.joinToString(", ") { "${it.block.id} - ${it.id}" }}")

        modbusFields.forEach { it.need() }
        schemaDevice.update(maxAgeMs.toLong())
        modbusFields.forEach { it.unNeed() }

        return DeviceData(schemaDevice)
    }

    @SubscriptionMapping("deviceData")
    fun streamData(
        @Argument("intervalMs") intervalMs: Int,
        @Argument("maxAgeMs")   maxAgeMs: Int = 500,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): Flux<DeviceData> {
        val subscriberId = UUID.randomUUID().toString()

        if (intervalMs < 10 || intervalMs > 10000) {
            throw IllegalArgumentException("IntervalMs must be between 10 ms and 10000 ms (10 seconds)")
        }

        val selectedFields = dataFetchingEnvironment.selectionSet.fields
            .filter { it.type is GraphQLScalarType }
            .filter { it.parentField.type is GraphQLObjectType }
            .filter { (it.parentField.type as GraphQLObjectType).name != "DeviceData" }
            .map { Pair( (it.parentField.type as GraphQLObjectType).name, it.name) }

        println("Query with GQL fields ${selectedFields.joinToString(",")}")

        val modbusFields = selectedFields.mapNotNull { (block, field) -> fields[block]?.get(field) }

        println("Subscription with Modbus fields ${modbusFields.joinToString(", ") { "${it.block.id} - ${it.id}" }}")
        println("Subscription started: $subscriberId with fields $selectedFields returned every $intervalMs ms")

        modbusFields.forEach { it.need() }


        return Flux
            .interval(
                Duration.ofMillis(timeToNextMultiple(intervalMs.toLong() - 20) ),
                Duration.ofMillis(intervalMs.toLong()))
            .map {
                sleep(Duration.ofMillis(timeToNextMultiple(intervalMs.toLong())))
                schemaDevice.update(maxAgeMs.toLong())
                DeviceData(schemaDevice)
            }
            .doFinally {
                println("Subscription ended: $subscriberId with fields $selectedFields")
                modbusFields.forEach { it.unNeed() }
            }
    }
}

fun timeToNextMultiple(intervalMs: Long): Long {
    val now = System.currentTimeMillis()
    val roundedNext = ((now / intervalMs) + 1) * intervalMs
    return roundedNext - now
}
