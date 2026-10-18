/*
 * Copyright 2026 chaevsfe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.patryk3211.powergrid.compat;

import com.zurrtum.create.compat.Mods;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.patryk3211.powergrid.PowerGrid;

public final class CompatMod {
    private CompatMod() {
    }

    public static void register() {
        if (Mods.JEI.isLoaded()) {
            // NeoForge: vanilla 26.x no longer sends recipes to clients. The Fabric build asked Fabric API to sync
            // Power Grid's recipe serializers for recipe viewers; NeoForge syncs by recipe type.
            NeoForge.EVENT_BUS.addListener(CompatMod::sendRecipes);
        }
    }

    private static void sendRecipes(OnDatapackSyncEvent event) {
        BuiltInRegistries.RECIPE_TYPE.entrySet().forEach(entry -> {
            if (entry.getKey().identifier().getNamespace().equals(PowerGrid.MOD_ID))
                event.sendRecipes(entry.getValue());
        });
    }
}
