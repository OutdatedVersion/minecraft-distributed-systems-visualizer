package wtf.bens.minecraft.mspatterns

import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityUnleashEvent
import org.bukkit.persistence.PersistentDataType

class EntityManager(plugin: Plugin): Listener {
    val IsOurEntityTag = NamespacedKey(plugin, "is_our_entity")
    val AllowDamageTag = NamespacedKey(plugin, "allow_damage")

    fun removeAll(world: World) {
        world.entities.forEach {
            if (isOurEntity(it)) {
                it.remove()
            }
        }
    }

    fun isOurEntity(entity: Entity): Boolean {
        val tag = entity.persistentDataContainer.get(IsOurEntityTag, PersistentDataType.BYTE) ?: return false
        return tag == 1.toByte()
    }

    fun setOurEntity(entity: Entity): Entity {
        entity.persistentDataContainer.set(IsOurEntityTag, PersistentDataType.BYTE, 1)
        return entity
    }

    fun setDamageOverride(entity: Entity, damagable: Boolean): Entity {
        entity.persistentDataContainer.set(AllowDamageTag, PersistentDataType.BYTE, if (damagable) 1 else 0)
        return entity
    }

    fun getDamageOverride(entity: Entity): Boolean {
        return entity.persistentDataContainer.getOrDefault(AllowDamageTag, PersistentDataType.BYTE, 0) == 1.toByte()
    }

    @EventHandler
    fun handleEntityDamage(event: EntityDeathEvent) {
        if (isOurEntity(event.entity) && !getDamageOverride(event.entity)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun handleLead(event: EntityUnleashEvent) {
        if (isOurEntity(event.entity)) {
            event.isCancelled = true
        }
    }
}