package nl.basjes.modbus.graphql

import graphql.schema.DataFetchingEnvironment
import nl.basjes.modbus.device.api.ModbusDevice
import nl.basjes.modbus.schema.SchemaDevice
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

    @QueryMapping("deviceData")
    fun queryData(
        @Argument("maxAgeMs") maxAgeMs: Int,
        env: DataFetchingEnvironment
    ): Data {
        val selectedFields = env.selectionSet.fields.map { it.name }

        println("Query with fields $selectedFields")

        return Data(
                    id = "query",
                    value = "Value query",
                    secret = "TopSecret query"
                )
    }

    @SubscriptionMapping("deviceData")
    fun streamData(
        @Argument("intervalMs") intervalMs: Int,
        @Argument("maxAgeMs")   maxAgeMs: Int = 500,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): Flux<Data> {
        val selectedFields = dataFetchingEnvironment.selectionSet.fields.map { it.name }
        val subscriberId = UUID.randomUUID().toString()

        if (intervalMs < 10 || intervalMs > 10000) {
            throw IllegalArgumentException("IntervalMs must be between 10 ms and 10000 ms (10 seconds)")
        }

        println("Subscription started: $subscriberId with fields $selectedFields returned every $intervalMs ms")

        return Flux
            .interval(
                Duration.ofMillis(timeToNextMultiple(intervalMs.toLong() - 20) ),
                Duration.ofMillis(intervalMs.toLong()))
            .map {
                sleep(Duration.ofMillis(timeToNextMultiple(intervalMs.toLong())))
                Data(
                    id = it.toString(),
                    value = "Value $it",
                    secret = "TopSecret $it"
                )
            }
            .doFinally {
                println("Subscription ended: $subscriberId with fields $selectedFields")
            }
    }
}

fun timeToNextMultiple(intervalMs: Long): Long {
    val now = System.currentTimeMillis()
    val roundedNext = ((now / intervalMs) + 1) * intervalMs
    return roundedNext - now
}

data class Data(val id: String, val value: String, val secret: String)
