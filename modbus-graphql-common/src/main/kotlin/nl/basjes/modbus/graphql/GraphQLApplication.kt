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
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

@SpringBootApplication
class GraphQLApplication

fun startGraphQLService(schemaDevice: SchemaDevice) {
    runApplication<GraphQLApplication> {
        addInitializers ({
            (it as GenericApplicationContext)
                .registerBean(
                    SchemaDevice::class.java,
                    Supplier { schemaDevice },
                )
        })
    }
}

