package wtf.bens.minecraft.mspatterns

import co.aikar.commands.PaperCommandManager
import de.slikey.effectlib.EffectManager
import org.bukkit.plugin.java.JavaPlugin
import wtf.bens.minecraft.mspatterns.command.TestCommand

class Plugin: JavaPlugin() {
    private lateinit var commandManager: PaperCommandManager
    private lateinit var effectManager: EffectManager
    private lateinit var entityManager: EntityManager

    override fun onEnable() {
        commandManager = PaperCommandManager(this)
        effectManager = EffectManager(this)
        entityManager = EntityManager(this)
        server.pluginManager.registerEvents(entityManager, this)

        commandManager.registerCommand(
            TestCommand(
                plugin = this,
                effectManager = effectManager,
                entityManager = entityManager
            )
        )
    }

    override fun onDisable() {
        if (commandManager != null) {
            commandManager.unregisterCommands()
        }

        if (effectManager != null) {
            effectManager.cancel(false)
        }

        if (entityManager != null) {
            server.worlds.forEach(entityManager::removeAll)
        }
    }
}