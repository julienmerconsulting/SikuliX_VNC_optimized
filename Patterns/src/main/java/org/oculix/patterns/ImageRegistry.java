package org.oculix.patterns;

import org.sikuli.script.Image;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registre central des patterns avec cache en mémoire.
 * Façade statique thread-safe pour l'accès aux patterns.
 * Appelé UNE FOIS au démarrage via initialize().
 */
public class ImageRegistry {

    private static volatile DataStore dataStore;
    private static final Map<String, PatternMetadata> cache =
            Collections.synchronizedMap(new HashMap<>());
    private static final Object LOCK = new Object();

    private ImageRegistry() {
    }

    /**
     * Initialise le registre: lance DataStore, charge patterns en mémoire.
     * Appelé UNE FOIS au démarrage. Thread-safe.
     */
    public static void initialize(String projectId) {
        synchronized (LOCK) {
            if (dataStore != null) {
                dataStore.close();
            }
            dataStore = new DataStore(projectId);
            dataStore.initializeDatabase();
            reloadCache();
        }
    }

    /**
     * Enregistre un nouveau pattern.
     * Sauvegarde en DB puis recharge le cache.
     */
    public static PatternMetadata register(String name, File imageFile) {
        synchronized (LOCK) {
            checkInitialized();
            PatternMetadata meta = dataStore.registerPattern(name, imageFile);
            reloadCache();
            return meta;
        }
    }

    /**
     * Retourne l'Image SikuliX correspondant au pattern.
     * Cherche dans le cache (HashMap).
     * Lance OculixPatternException si introuvable.
     */
    public static Image get(String patternName) {
        checkInitialized();
        PatternMetadata meta = cache.get(patternName);
        if (meta == null) {
            throw OculixPatternException.patternNotFound(patternName);
        }
        return Image.create(meta.getFilepath());
    }

    /**
     * Recherche des patterns par nom (LIKE %query%).
     */
    public static List<PatternMetadata> search(String query) {
        checkInitialized();
        return dataStore.searchPatterns(query);
    }

    /**
     * Retourne tous les patterns (ORDER BY name).
     */
    public static List<PatternMetadata> getAllPatterns() {
        checkInitialized();
        return dataStore.getAllPatterns();
    }

    /**
     * Recharge le cache depuis la DB.
     */
    public static void reload() {
        synchronized (LOCK) {
            checkInitialized();
            reloadCache();
        }
    }

    /**
     * Ferme le DataStore et vide le cache.
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (dataStore != null) {
                dataStore.close();
                dataStore = null;
            }
            cache.clear();
        }
    }

    /**
     * Retourne le DataStore sous-jacent (usage interne).
     */
    static DataStore getDataStore() {
        return dataStore;
    }

    /**
     * Retourne le metadata d'un pattern depuis le cache.
     */
    static PatternMetadata getMetadata(String patternName) {
        return cache.get(patternName);
    }

    private static void reloadCache() {
        cache.clear();
        List<PatternMetadata> all = dataStore.getAllPatterns();
        for (PatternMetadata meta : all) {
            cache.put(meta.getName(), meta);
        }
    }

    private static void checkInitialized() {
        if (dataStore == null) {
            throw new OculixPatternException("ImageRegistry not initialized. Call initialize() first.");
        }
    }
}
