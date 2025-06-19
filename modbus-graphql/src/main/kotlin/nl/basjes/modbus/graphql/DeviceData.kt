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

import nl.basjes.modbus.schema.Block
import nl.basjes.modbus.schema.Field
import nl.basjes.modbus.schema.SchemaDevice
import nl.basjes.modbus.schema.get
import org.springframework.context.annotation.Description
import sun.net.www.protocol.http.HttpURLConnection.userAgent
import java.util.*

@Description("The data from the modbus device.")
class DeviceData(
    val schemaDevice: SchemaDevice,
) {
//    fun getBlock(blockId: String): Block? {
//        return schemaDevice[blockId]
//    }
//
//    fun getField(fieldId: String): Field? {
//        return userAgent.get(fieldName)
//    }
//
//    val blocks: MutableMap<String, Block>? = null
//
//    val fields: MutableMap<String?, FieldResult?>
//        get() {
//            if (blockMap == null) {
//                blockMap = TreeMap<String?, FieldResult?>()
//
//                for (fieldName in userAgent.getAvailableFieldNamesSorted()) {
//                    fieldsMap.put(fieldName, FieldResult(fieldName, userAgent.getValue(fieldName)))
//                }
//            }
//            return fieldsMap
//        }
}
