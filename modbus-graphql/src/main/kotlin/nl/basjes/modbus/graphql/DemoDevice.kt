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

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import nl.basjes.modbus.device.api.ModbusDevice
import nl.basjes.modbus.device.j2mod.ModbusDeviceJ2Mod
import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.modbus.thermia.ThermiaGenesis
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
class DemoDevice {

    // FIXME: For testing purpose only

    @Bean
    fun connectModbus() : ModbusDevice {
        val modbusHost      = "localhost" // "thermia.iot.basjes.nl"
        val modbusPort      = 1502 // MODBUS_STANDARD_TCP_PORT
        val modbusUnit      = 1
        print("Modbus: Connecting...")
        val modbusMaster = ModbusTCPMaster(modbusHost, modbusPort)
        modbusMaster.connect()
        return ModbusDeviceJ2Mod(modbusMaster, modbusUnit)
    }

    @Bean
    fun schemaDevice(
        modbusDevice: ModbusDevice,
    ) : SchemaDevice {
        val thermiaGenesis = ThermiaGenesis()
        thermiaGenesis.connect(modbusDevice)
        return thermiaGenesis.schemaDevice
    }

}
