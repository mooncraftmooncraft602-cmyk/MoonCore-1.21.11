package com.mooncore.modules.crop;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Reprise des visuels de cultures au chargement/déchargement des chunks (Étape C4).
 * Les données vivent en base/mémoire ; seules les entités d'affichage (transitoires)
 * sont (re)construites par chunk — jamais de scan global du monde.
 */
public final class CropListener implements Listener {

    private final CropManagerModule module;

    public CropListener(CropManagerModule module) {
        this.module = module;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        module.spawnVisualsInChunk(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        module.despawnVisualsInChunk(e.getChunk());
    }
}
