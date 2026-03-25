package org.oculix.patterns;

import org.oculix.patterns.config.OculixConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès SQLite pour le stockage des patterns et relations.
 * Crée la DB automatiquement au premier lancement.
 * Implémente AutoCloseable pour garantir la fermeture de la connexion.
 */
public class DataStore implements AutoCloseable {

    private final String projectId;
    private Connection connection;

    // SQL de création des tables (embarqué, zéro fichier externe)
    private static final String CREATE_VISUAL_PATTERNS = """
            CREATE TABLE IF NOT EXISTS visual_patterns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id TEXT NOT NULL,
                name TEXT NOT NULL,
                filepath TEXT NOT NULL,
                image_width_pixels INTEGER NOT NULL,
                image_height_pixels INTEGER NOT NULL,
                perceptual_hash TEXT UNIQUE,
                pattern_type TEXT NOT NULL CHECK(pattern_type IN ('standalone', 'parent', 'child')),
                version INTEGER DEFAULT 1,
                is_current BOOLEAN DEFAULT 1,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_by TEXT,
                updated_at TIMESTAMP,
                updated_by TEXT,
                UNIQUE(project_id, name)
            )
            """;

    private static final String CREATE_PATTERN_ABSOLUTE_CONTEXT = """
            CREATE TABLE IF NOT EXISTS pattern_absolute_context (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pattern_id INTEGER NOT NULL UNIQUE,
                capture_screen_x INTEGER NOT NULL,
                capture_screen_y INTEGER NOT NULL,
                capture_screen_width INTEGER NOT NULL,
                capture_screen_height INTEGER NOT NULL,
                roi_margin_left_pixels INTEGER DEFAULT 100,
                roi_margin_right_pixels INTEGER DEFAULT 100,
                roi_margin_top_pixels INTEGER DEFAULT 50,
                roi_margin_bottom_pixels INTEGER DEFAULT 150,
                min_confidence_threshold REAL DEFAULT 0.85,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(pattern_id) REFERENCES visual_patterns(id) ON DELETE CASCADE
            )
            """;

    private static final String CREATE_PARENT_CHILD_RELATIONS = """
            CREATE TABLE IF NOT EXISTS parent_child_relations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                child_pattern_id INTEGER NOT NULL,
                parent_pattern_id INTEGER NOT NULL,
                relative_x_pixels INTEGER NOT NULL,
                relative_y_pixels INTEGER NOT NULL,
                child_width_pixels INTEGER NOT NULL,
                child_height_pixels INTEGER NOT NULL,
                roi_margin_left_pixels INTEGER DEFAULT 30,
                roi_margin_right_pixels INTEGER DEFAULT 30,
                roi_margin_top_pixels INTEGER DEFAULT 20,
                roi_margin_bottom_pixels INTEGER DEFAULT 30,
                priority INTEGER DEFAULT 1 CHECK(priority > 0),
                is_active BOOLEAN DEFAULT 1,
                parent_is_required BOOLEAN DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP,
                UNIQUE(child_pattern_id, parent_pattern_id),
                FOREIGN KEY(child_pattern_id) REFERENCES visual_patterns(id) ON DELETE CASCADE,
                FOREIGN KEY(parent_pattern_id) REFERENCES visual_patterns(id) ON DELETE CASCADE
            )
            """;

    private static final String CREATE_INDEX_PROJECT = "CREATE INDEX IF NOT EXISTS idx_patterns_project ON visual_patterns(project_id)";
    private static final String CREATE_INDEX_NAME = "CREATE INDEX IF NOT EXISTS idx_patterns_name ON visual_patterns(name)";
    private static final String CREATE_INDEX_CHILD = "CREATE INDEX IF NOT EXISTS idx_parent_child_child ON parent_child_relations(child_pattern_id)";
    private static final String CREATE_INDEX_PARENT = "CREATE INDEX IF NOT EXISTS idx_parent_child_parent ON parent_child_relations(parent_pattern_id)";

    public DataStore(String projectId) {
        this.projectId = projectId;
    }

    /**
     * Crée la DB + applique les migrations si elle n'existe pas.
     * Appelé une seule fois au démarrage.
     */
    public void initializeDatabase() {
        try {
            File dbFile = OculixConfig.getDatabasePath();
            File dbDir = dbFile.getParentFile();
            if (!dbDir.exists() && !dbDir.mkdirs()) {
                throw new IOException("Impossible de créer le dossier: " + dbDir.getAbsolutePath());
            }

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // Activer les foreign keys pour SQLite
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }

            applyMigrations();
        } catch (Exception e) {
            throw OculixPatternException.databaseInitFailed(e);
        }
    }

    private void applyMigrations() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_VISUAL_PATTERNS);
            stmt.execute(CREATE_PATTERN_ABSOLUTE_CONTEXT);
            stmt.execute(CREATE_PARENT_CHILD_RELATIONS);
            stmt.execute(CREATE_INDEX_PROJECT);
            stmt.execute(CREATE_INDEX_NAME);
            stmt.execute(CREATE_INDEX_CHILD);
            stmt.execute(CREATE_INDEX_PARENT);
        }
    }

    /**
     * Enregistre une image dans la DB.
     * Calcule le perceptual hash, retourne PatternMetadata avec ID.
     */
    public PatternMetadata registerPattern(String name, File imageFile) {
        if (!imageFile.exists()) {
            throw new OculixPatternException("Image file not found: " + imageFile.getAbsolutePath());
        }

        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                throw new OculixPatternException("Cannot read image: " + imageFile.getAbsolutePath());
            }

            int width = img.getWidth();
            int height = img.getHeight();
            String pHash = computePerceptualHash(img);

            String sql = "INSERT INTO visual_patterns (project_id, name, filepath, image_width_pixels, " +
                    "image_height_pixels, perceptual_hash, pattern_type) VALUES (?, ?, ?, ?, ?, ?, 'standalone')";

            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, projectId);
                ps.setString(2, name);
                ps.setString(3, imageFile.getAbsolutePath());
                ps.setInt(4, width);
                ps.setInt(5, height);
                ps.setString(6, pHash);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int id = keys.getInt(1);
                        return new PatternMetadata(id, name, imageFile, width, height,
                                pHash, "standalone", LocalDateTime.now());
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw OculixPatternException.patternAlreadyExists(name);
            }
            throw new OculixPatternException("Failed to register pattern: " + name, e);
        } catch (IOException e) {
            throw new OculixPatternException("Failed to read image: " + imageFile.getAbsolutePath(), e);
        }

        throw new OculixPatternException("Failed to register pattern: " + name);
    }

    /**
     * Récupère un pattern par nom.
     * Retourne null si introuvable.
     */
    public PatternMetadata getPattern(String name) {
        String sql = "SELECT id, name, filepath, image_width_pixels, image_height_pixels, " +
                "perceptual_hash, pattern_type, created_at FROM visual_patterns " +
                "WHERE project_id = ? AND name = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, name);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMetadata(rs);
                }
            }
        } catch (SQLException e) {
            throw new OculixPatternException("Failed to get pattern: " + name, e);
        }
        return null;
    }

    /**
     * Recherche les patterns par nom (LIKE %query%).
     */
    public List<PatternMetadata> searchPatterns(String query) {
        String sql = "SELECT id, name, filepath, image_width_pixels, image_height_pixels, " +
                "perceptual_hash, pattern_type, created_at FROM visual_patterns " +
                "WHERE project_id = ? AND name LIKE ? ORDER BY name ASC";

        List<PatternMetadata> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, "%" + query + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToMetadata(rs));
                }
            }
        } catch (SQLException e) {
            throw new OculixPatternException("Failed to search patterns: " + query, e);
        }
        return results;
    }

    /**
     * Retourne tous les patterns du projet.
     */
    public List<PatternMetadata> getAllPatterns() {
        String sql = "SELECT id, name, filepath, image_width_pixels, image_height_pixels, " +
                "perceptual_hash, pattern_type, created_at FROM visual_patterns " +
                "WHERE project_id = ? ORDER BY name ASC";

        List<PatternMetadata> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, projectId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToMetadata(rs));
                }
            }
        } catch (SQLException e) {
            throw new OculixPatternException("Failed to get all patterns", e);
        }
        return results;
    }

    /**
     * Crée une relation parent-child entre deux patterns.
     */
    public void relatePatterns(String childName, String parentName,
                               int relX, int relY, int childW, int childH) {
        PatternMetadata child = getPattern(childName);
        if (child == null) {
            throw OculixPatternException.patternNotFound(childName);
        }
        PatternMetadata parent = getPattern(parentName);
        if (parent == null) {
            throw OculixPatternException.parentNotFound(parentName);
        }

        String sql = "INSERT INTO parent_child_relations (child_pattern_id, parent_pattern_id, " +
                "relative_x_pixels, relative_y_pixels, child_width_pixels, child_height_pixels) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, child.getId());
            ps.setInt(2, parent.getId());
            ps.setInt(3, relX);
            ps.setInt(4, relY);
            ps.setInt(5, childW);
            ps.setInt(6, childH);
            ps.executeUpdate();

            // Met à jour les pattern_type
            updatePatternType(child.getId(), "child");
            updatePatternType(parent.getId(), "parent");
        } catch (SQLException e) {
            throw new OculixPatternException("Failed to create relation: " + childName + " -> " + parentName, e);
        }
    }

    /**
     * Retourne la relation entre un enfant et un parent, ou null.
     */
    public ParentChildRelation getRelation(String childName, String parentName) {
        PatternMetadata child = getPattern(childName);
        PatternMetadata parent = getPattern(parentName);
        if (child == null || parent == null) {
            return null;
        }

        String sql = "SELECT * FROM parent_child_relations " +
                "WHERE child_pattern_id = ? AND parent_pattern_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, child.getId());
            ps.setInt(2, parent.getId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRelation(rs);
                }
            }
        } catch (SQLException e) {
            throw new OculixPatternException("Failed to get relation", e);
        }
        return null;
    }

    /**
     * Retourne toutes les relations actives d'un pattern enfant.
     * ORDER BY priority ASC.
     */
    public List<ParentChildRelation> getActiveRelations(String childName) {
        PatternMetadata child = getPattern(childName);
        if (child == null) {
            return new ArrayList<>();
        }

        String sql = "SELECT * FROM parent_child_relations " +
                "WHERE child_pattern_id = ? AND is_active = 1 ORDER BY priority ASC";

        List<ParentChildRelation> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, child.getId());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToRelation(rs));
                }
            }
        } catch (SQLException e) {
            throw new OculixPatternException("Failed to get active relations for: " + childName, e);
        }
        return results;
    }

    /**
     * Ferme la connexion SQLite.
     */
    @Override
    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                throw new OculixPatternException("Failed to close database connection", e);
            }
        }
    }

    public String getProjectId() {
        return projectId;
    }

    Connection getConnection() {
        return connection;
    }

    private void updatePatternType(int patternId, String type) throws SQLException {
        String sql = "UPDATE visual_patterns SET pattern_type = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setInt(2, patternId);
            ps.executeUpdate();
        }
    }

    private PatternMetadata mapResultSetToMetadata(ResultSet rs) throws SQLException {
        return new PatternMetadata(
                rs.getInt("id"),
                rs.getString("name"),
                new File(rs.getString("filepath")),
                rs.getInt("image_width_pixels"),
                rs.getInt("image_height_pixels"),
                rs.getString("perceptual_hash"),
                rs.getString("pattern_type"),
                parseTimestamp(rs.getString("created_at"))
        );
    }

    private ParentChildRelation mapResultSetToRelation(ResultSet rs) throws SQLException {
        ParentChildRelation rel = new ParentChildRelation(
                rs.getInt("child_pattern_id"),
                rs.getInt("parent_pattern_id"),
                rs.getInt("relative_x_pixels"),
                rs.getInt("relative_y_pixels"),
                rs.getInt("child_width_pixels"),
                rs.getInt("child_height_pixels")
        );
        rel.setRoiMarginLeftPixels(rs.getInt("roi_margin_left_pixels"));
        rel.setRoiMarginRightPixels(rs.getInt("roi_margin_right_pixels"));
        rel.setRoiMarginTopPixels(rs.getInt("roi_margin_top_pixels"));
        rel.setRoiMarginBottomPixels(rs.getInt("roi_margin_bottom_pixels"));
        rel.setPriority(rs.getInt("priority"));
        rel.setActive(rs.getBoolean("is_active"));
        rel.setParentIsRequired(rs.getBoolean("parent_is_required"));
        return rel;
    }

    /**
     * Parse un timestamp SQLite (format "yyyy-MM-dd HH:mm:ss") en LocalDateTime.
     * Retourne now() en fallback si le format est invalide ou null.
     */
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(timestamp.replace(" ", "T"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * Calcul du perceptual hash (pHash) simplifié.
     * Réduit l'image à 8x8 en niveaux de gris, puis compare à la moyenne.
     */
    private String computePerceptualHash(BufferedImage img) {
        // Réduction à 8x8
        java.awt.Image scaled = img.getScaledInstance(8, 8, java.awt.Image.SCALE_SMOOTH);
        BufferedImage gray = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        gray.getGraphics().drawImage(scaled, 0, 0, null);

        // Calcul de la moyenne des pixels
        int[] pixels = new int[64];
        long sum = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int pixel = gray.getRaster().getSample(j, i, 0);
                pixels[i * 8 + j] = pixel;
                sum += pixel;
            }
        }
        double avg = sum / 64.0;

        // Hash: 1 si pixel > moyenne, 0 sinon
        StringBuilder hash = new StringBuilder();
        for (int pixel : pixels) {
            hash.append(pixel > avg ? "1" : "0");
        }

        // Convertir en hexadécimal
        long hashValue = Long.parseUnsignedLong(hash.toString(), 2);
        return String.format("%016x", hashValue);
    }
}
