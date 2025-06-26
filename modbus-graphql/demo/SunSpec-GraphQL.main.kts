#!/usr/bin/env kotlin
/*
 *
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
 *
 */

@file:DependsOn("nl.basjes.sunspec:sunspec-device:0.6.0")
@file:DependsOn("nl.basjes.modbus:modbus-api-j2mod:0.10.0")
@file:DependsOn("nl.basjes.modbus.graphql:modbus-graphql:0.0.1-SNAPSHOT")
@file:DependsOn("org.apache.logging.log4j:log4j-to-slf4j:2.25.0")

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import nl.basjes.modbus.device.exception.ModbusException
import nl.basjes.modbus.device.j2mod.ModbusDeviceJ2Mod
import nl.basjes.modbus.graphql.startGraphQLService
import nl.basjes.sunspec.device.SunspecDevice

// The hostname to connect to
val modbusHost        = "sunspec.iot.basjes.nl"
val modbusPort        = 502
val modbusUnit        = 126  // This is the SunSpec specific Modbus Unit ID for SMA devices

println("Make connection with physical device")
val modbusMaster = ModbusTCPMaster(modbusHost, modbusPort)
modbusMaster.connect()
val modbusDevice = ModbusDeviceJ2Mod(modbusMaster, modbusUnit)

println("Create the SunSpec schema based on the device data")
val schemaDevice = SunspecDevice.generate(modbusDevice) ?: throw ModbusException("Unable to read the SunSpec device schema")

println("Link the generated SchemaDevice to the ModbusDevice")
schemaDevice.connect(modbusDevice)

println("Start the GraphQL service...")
startGraphQLService(schemaDevice)
// FIXME: This doesn't work yet. Logging conflicts Kotlin script with Spring GraphQL and other problems
