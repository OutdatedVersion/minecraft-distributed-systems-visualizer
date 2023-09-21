package wtf.bens.minecraft.mspatterns.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Subcommand
import com.google.common.collect.EvictingQueue
import de.slikey.effectlib.EffectManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.EntityEffect
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Chicken
import org.bukkit.entity.Cow
import org.bukkit.entity.Entity
import org.bukkit.entity.Firework
import org.bukkit.entity.Panda
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import wtf.bens.minecraft.mspatterns.EntityManager
import wtf.bens.minecraft.mspatterns.Plugin
import wtf.bens.minecraft.mspatterns.effect.DrawLineEffect
import java.util.*

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

data class RequestData(
    val successful: Boolean,
    val ms: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

data class ProxyTraffic(
    var pending: Int = 0,
    var successful: Int = 0,
    var failed: Int = 0,
    var total: Int = 0,
    var isHealthy: Boolean = true,
    var unhealthySince: Long = 0,
    var unhealthyLength: Int = 0,
    var requests: Queue<RequestData> = EvictingQueue.create(20),
)

fun ProxyTraffic.asComponent(): Component {
    return Component.text(
        "${successRate()}% of ${requests.size}",
        NamedTextColor.GRAY
    )
}

fun ProxyTraffic.successRate(): Double {
    val successes = requests.count { it.successful }
    return ((successes.toDouble() / requests.size.toDouble()) * 100).round(2)
}

fun ProxyTraffic.rps(): Int {
    val now = System.currentTimeMillis()
    // cheating because true high throughput services are hard to visualize
    return requests.count { now - it.timestamp <= 2050  }
}

enum class CircuitBreakerState {
    Open,
    HalfOpen,
    Closed,
}

data class CircuitBreaker(
    var state: CircuitBreakerState = CircuitBreakerState.Open,
)

@CommandAlias("circuitbreaker")
class TestCommand(
    private val plugin: Plugin,
    private val entityManager: EntityManager,
    private val effectManager: EffectManager,
): BaseCommand() {
    fun left(facing: BlockFace): BlockFace {
        val go = facing.ordinal
        if (go == 0) {
            return BlockFace.WEST
        }
        return BlockFace.values()[go - 1]
    }

    fun right(facing: BlockFace): BlockFace {
        val go = facing.ordinal
        if (go == 3) {
            return BlockFace.NORTH
        }
        return BlockFace.values()[go + 1]
    }

    private val uuids = mutableListOf<UUID>()
    private val proxyEntityUuids = mutableListOf<UUID>()
    private val proxyToService = mutableMapOf<UUID, UUID>()
    private val proxyToStand = mutableMapOf<UUID, UUID>()
    private val proxyToTraffic = mutableMapOf<UUID, ProxyTraffic>()
    private val serviceToResource = mutableMapOf<UUID, UUID>()
    private val serviceToStand = mutableMapOf<UUID, UUID>()
    private var current: BukkitTask? = null
    private var currentData: BukkitTask? = null

    private var baseFailureRate = 15

    @Subcommand("basefailurerate")
    fun handleFailureRateCommand(player: Player, rate: Int) {
        val current = baseFailureRate
        baseFailureRate = rate
        player.sendMessage(Component.text("Base failure rate set to $baseFailureRate from $current"))
    }

    @Default
    fun handleBaseCommand(player: Player) {
        val world = player.location.world
        if (current != null) {
            current!!.cancel()
            currentData?.cancel()

            uuids.forEach { world.getEntity(it)?.remove() }
            uuids.clear()
            proxyEntityUuids.clear()
            proxyToService.clear()
            proxyToStand.clear()
            proxyToTraffic.clear()
            current = null
            currentData = null

            player.sendMessage(Component.text("Cancelled existing visualizer"))
            return
        }

        val facing = player.facing
        val source = player.location.toBlockLocation().clone().add(facing.direction.multiply(2))

        val origin = source.clone().add(left(facing).direction.multiply(4)).add(facing.direction.multiply(7))
        origin.direction = facing.oppositeFace.direction

        for (i in 1..3 * 3 step 3) {
            val r = origin.clone().add(right(facing).direction.multiply(i)).toCenterLocation()
            r.y -= .5

            val cow = world.spawn(r, Cow::class.java)
            cow.setAI(false)
            cow.customName(Component.text("auth-${UUID.randomUUID().toString().substring(0, 8)}", NamedTextColor.GREEN))
            cow.isCustomNameVisible = true
            entityManager.setOurEntity(cow)

            val resourceEntity = world.spawn(r.clone().add(left(facing).direction), Chicken::class.java)
            resourceEntity.setAI(false)
            resourceEntity.customName(Component.text("redis", NamedTextColor.LIGHT_PURPLE))
            resourceEntity.isCustomNameVisible = true
            resourceEntity.setLeashHolder(cow)
            entityManager.setOurEntity(resourceEntity)
            entityManager.setDamageOverride(resourceEntity, damagable = true)
            serviceToResource[cow.uniqueId] = resourceEntity.uniqueId

            val serviceDataStand = world.spawn(cow.location.clone().subtract(0.0, 0.2, 0.0), ArmorStand::class.java)
            serviceDataStand.customName(Component.text("-- rps", NamedTextColor.GRAY))
            serviceDataStand.isCustomNameVisible = true
            serviceDataStand.isVisible = false
            serviceDataStand.setGravity(false)
            entityManager.setOurEntity(serviceDataStand)
            serviceToStand[cow.uniqueId] = serviceDataStand.uniqueId
            uuids.add(serviceDataStand.uniqueId)

            val panda = world.spawn(r.clone().add(facing.oppositeFace.direction.multiply(3)), Panda::class.java)
            panda.setBaby()
            panda.ageLock = true
            panda.setAI(false)
            panda.setLeashHolder(cow)
            panda.customName(Component.text("proxy", NamedTextColor.AQUA))
            panda.isCustomNameVisible = true
            entityManager.setOurEntity(panda)

            val trafficStand = world.spawn(panda.location.clone().subtract(0.0, 0.8, 0.0), ArmorStand::class.java)
            trafficStand.customName(Component.text("--", NamedTextColor.GRAY))
            trafficStand.isCustomNameVisible = true
            trafficStand.isVisible = false
            trafficStand.setGravity(false)
            entityManager.setOurEntity(trafficStand)

            val stateStand = world.spawn(panda.location.clone().subtract(0.0, 0.5, 0.0), ArmorStand::class.java)
            stateStand.customName(Component.text("Open", NamedTextColor.GRAY))
            stateStand.isCustomNameVisible = true
            stateStand.isVisible = false
            stateStand.setGravity(false)
            entityManager.setOurEntity(stateStand)
            uuids.add(stateStand.uniqueId)

            proxyToService[panda.uniqueId] = cow.uniqueId
            proxyToStand[panda.uniqueId] = trafficStand.uniqueId
            proxyToTraffic[panda.uniqueId] = ProxyTraffic()
            proxyEntityUuids.add(panda.uniqueId)
            uuids.add(panda.uniqueId)
            uuids.add(cow.uniqueId)
            uuids.add(trafficStand.uniqueId)
            uuids.add(resourceEntity.uniqueId)

            // val b = r.block.location.clone().add(facing.oppositeFace.direction)
        }

        origin.world.spawn(origin, Firework::class.java)
        val pig = world.spawn(source, Pig::class.java)
        pig.setAI(false)
        pig.customName(Component.text("Load balancer", NamedTextColor.GOLD))
        pig.isCustomNameVisible = true
        pig.setSaddle(true)
        uuids.add(pig.uniqueId)
        entityManager.setOurEntity(pig)

        currentData = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            // service metrics
            // proxy metrics
            for (i in 1..proxyEntityUuids.size) {
                val proxyEntity = world.getEntity(proxyEntityUuids[i - 1]) ?: continue

                val traffic = proxyToTraffic[proxyEntity.uniqueId]!!

                val serviceEntityUuid = proxyToService[proxyEntity.uniqueId]!!
                val serviceDataStand = world.getEntity(serviceToStand[serviceEntityUuid]!!)
                val rps = traffic.rps()
                serviceDataStand?.customName(Component.text("${rps * 50} rps, ${Math.min(100, baseFailureRate + (rps * 6))}% failure rate", NamedTextColor.GRAY))

                val standEntity = world.getEntity(proxyToStand[proxyEntity.uniqueId]!!)

                if (rps == 0) {
                    standEntity?.customName(Component.text("--", NamedTextColor.GRAY))
                } else {
                    standEntity?.customName(traffic.asComponent())
                }
            }
        }, 0L, 20L)

        var idx = 0
        current = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val effect = DrawLineEffect(effectManager)
            effect.entity = pig

            // getProxyEntity
            var proxyEntity: Entity? = null
            var didFindTarget = false
            for (i in 1..proxyEntityUuids.size) {
                if (idx + 1 > proxyEntityUuids.size) {
                    idx = 0
                }

                proxyEntity = world.getEntity(proxyEntityUuids[idx++])

                val loc = proxyEntity!!.location.clone().add(facing.oppositeFace.direction)
                if (loc.block.type == Material.AIR) {
                    didFindTarget = true
                    break
                }
            }

            if (!didFindTarget) {
                pig.world.spawnParticle(Particle.BLOCK_DUST, pig.location, 8, Material.REDSTONE_BLOCK.createBlockData())
                return@Runnable
            }

            effect.targetEntity = proxyEntity
            effect.color = Color.YELLOW
            effect.particleSize = .60f
            effect.callback = Runnable {
                val traffic = proxyToTraffic[proxyEntity!!.uniqueId]!!
                traffic.pending += 1

                val e = DrawLineEffect(effectManager)
                e.entity = proxyEntity
                val serviceEntity = world.getEntity(proxyToService[proxyEntity!!.uniqueId]!!)
                e.targetEntity = serviceEntity
                e.color = Color.YELLOW
                e.particleSize = .60f
                e.start()

                e.callback = Runnable {
                    val backToProxy = DrawLineEffect(effectManager)
                    var wasSuccessful = Random().nextInt(100) > Math.min(100, baseFailureRate + (traffic.rps() * 10))

                    val resourceEntity = world.getEntity(serviceToResource[serviceEntity!!.uniqueId]!!)
                    if (resourceEntity == null) {
                        wasSuccessful = false
                    }

                    backToProxy.entity = serviceEntity
                    backToProxy.targetEntity = proxyEntity
                    backToProxy.color = if (wasSuccessful) Color.GREEN else Color.RED
                    backToProxy.particleSize = .60f
                    backToProxy.callback = Runnable {
                        if (wasSuccessful) {
                            traffic.successful += 1
                            traffic.requests.add(RequestData(successful = true, ms = 1))
                        } else {
                            traffic.failed += 1
                            traffic.requests.add(RequestData(successful = false, ms = 1))
                        }
                        traffic.total += 1
                        traffic.pending -= 1

                        val backToBalancer = DrawLineEffect(effectManager)
                        backToBalancer.entity = proxyEntity
                        backToBalancer.targetEntity = pig
                        backToBalancer.color = if (wasSuccessful) Color.GREEN else Color.RED
                        backToBalancer.particleSize = .60f
                        backToBalancer.start()
                    }
                    backToProxy.start()
                }
            }
            effect.start()
        }, 0L, 10L)
    }

//            val effect = SphereEffect(effectManager)
//            effect.color = Color.RED
//            effect.particle = Particle.REDSTONE
//            effect.particleSize = .60f
//            effect.entity = panda
//            effect.yOffset = -0.5
//            effect.particles = 40
//            effect.start()
}