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
package nl.basjes.modbus.graphql.tcp

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import nl.basjes.modbus.device.j2mod.ModbusDeviceJ2Mod
import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.modbus.schema.toSchemaDevice
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.io.File

@SpringBootApplication(scanBasePackages = ["nl.basjes.modbus.graphql.tcp","nl.basjes.modbus.graphql"])
 class ModbusTCPGraphQLApplication {
    // The hostname to connect to
    @Value($$"${modbus.host}")
    private var modbusHost:String = ""

    // The modbus port to connect to
    @Value($$"${modbus.port:502}")
    private var modbusPort:Int = 502

    // The modbus unit id
    @Value($$"${modbus.unit:1}")
    val modbusUnit:Int = 1

    @Bean
    fun schemaDevice(): SchemaDevice {
        val logger = LoggerFactory.getLogger("Modbus TCP")

        logger.info("Connecting to Modbus TCP device on:")
        logger.info("- Hostname: {}", modbusHost)
        logger.info("- TCP Port: {}", modbusPort)
        logger.info("- UnitID  : {}", modbusUnit)

        logger.info("Modbus: Connecting...")
        val modbusMaster = ModbusTCPMaster(modbusHost, modbusPort)
        modbusMaster.connect()
        val modbusDevice = ModbusDeviceJ2Mod(modbusMaster, modbusUnit)
        logger.info("Modbus: Connected")

        logger.info("Schema: Loading schema")
        val schemaDevice = File("ModbusSchema.yaml").toSchemaDevice()
        schemaDevice.connect(modbusDevice)
        logger.info("Schema: Completed")
        return schemaDevice
    }

}

fun main(args: Array<String>) {
    runApplication<ModbusTCPGraphQLApplication>(*args)
}
