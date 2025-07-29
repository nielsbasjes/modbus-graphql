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
package nl.basjes.sunspec.graphql

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import nl.basjes.modbus.device.exception.ModbusException
import nl.basjes.modbus.device.j2mod.ModbusDeviceJ2Mod
import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.sunspec.device.SunspecDevice
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication(scanBasePackages = ["nl.basjes.sunspec.graphql", "nl.basjes.modbus.graphql"])
class SunSpecGraphQLApplication {
    // The hostname to connect to
    @Value($$"${sunspec.host}")
    private var modbusHost:String = ""

    // The modbus port to connect to
    @Value($$"${sunspec.port:502}")
    private var modbusPort:Int = 502

    // The modbus unit id
    @Value($$"${sunspec.unit:1}")
    val modbusUnit:Int = 1


    @Bean
    fun schemaDevice(): SchemaDevice {
        val logger = LoggerFactory.getLogger("SunSpec")

        logger.info("Connecting to SunSpec device on:")
        logger.info("- Modbus Hostname: {}", modbusHost)
        logger.info("- Modbus TCP Port: {}", modbusPort)
        logger.info("- Modbus UnitID  : {}", modbusUnit)

        logger.info("Modbus: Connecting...")
        val modbusMaster = ModbusTCPMaster(modbusHost, modbusPort)
        modbusMaster.connect()
        val modbusDevice = ModbusDeviceJ2Mod(modbusMaster, modbusUnit)
        logger.info("Modbus: Connected")

        logger.info("SunSpec: Finding list of supported Models")
        val schemaDevice = SunspecDevice.generate(modbusDevice) ?: throw ModbusException("Unable to read the SunSpec device schema")
        schemaDevice.connect(modbusDevice)
        logger.info("SunSpec: Completed")
        return schemaDevice
    }

}

fun main(args: Array<String>) {
    runApplication<SunSpecGraphQLApplication>(*args)
}
