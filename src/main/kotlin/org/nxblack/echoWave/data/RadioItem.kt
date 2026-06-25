package org.nxblack.echoWave.data

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.awt.Component

object RadioItem {

    fun create(): ItemStack {

        val item = ItemStack(Material.RECOVERY_COMPASS)

        val meta = item.itemMeta!!

        meta.setDisplayName("§bEchoWave Radio")

        meta.setCustomModelData(1001)

        meta.isUnbreakable = true

        item.itemMeta = meta

        return item
    }
}