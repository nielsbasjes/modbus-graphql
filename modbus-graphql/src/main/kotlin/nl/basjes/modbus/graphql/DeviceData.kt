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

import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.modbus.schema.fetcher.ModbusQuery
import org.springframework.context.annotation.Description
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

@Description("The data from the modbus device.")
class DeviceData(
    val schemaDevice: SchemaDevice,
    val modbusQueries: List<ModbusQuery>,
    val totalUpdateDurationMs: Int,
) {
    val description = schemaDevice.description
    val requestTimestamp: ZonedDateTime = Instant.now().atZone(UTC)
    val dataTimestamp: ZonedDateTime?
        get() {
            val fields = modbusQueries.map { it.fields }.flatten().sorted().distinct()
            if (fields.isEmpty()) {
                return null
            }
            val maxTimestamp = fields.mapNotNull { it.valueEpochMs }.toTypedArray<Long>().maxOrNull() ?: return null
            return Instant.ofEpochMilli(maxTimestamp).atZone(UTC)
        }

}
