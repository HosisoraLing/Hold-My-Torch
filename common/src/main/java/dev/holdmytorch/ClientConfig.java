package dev.holdmytorch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;

public final class ClientConfig {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static ClientConfig current = new ClientConfig();
    private static Path file;
    public Rule torch = new Rule();
    public Rule drop = new Rule();
    public boolean customEnabled = true;
    public int brightnessThreshold = 12;
    public boolean dynamicLights = true;
    public Map<String, Rule> keys = new LinkedHashMap<>();

    public static final class Rule {
        public boolean enabled = true;
        public Confirmation.Mode mode = Confirmation.Mode.HOLD;
        public int holdMs = 500;
        public int doubleMs = 300;
        public String confirmKey = "key.keyboard.left.alt";

        public void validate() {
            if (mode == null) mode = Confirmation.Mode.HOLD;
            holdMs = Math.max(100, Math.min(10000, holdMs));
            doubleMs = Math.max(100, Math.min(2000, doubleMs));
            if (confirmKey == null) confirmKey = "key.keyboard.left.alt";
            try { com.mojang.blaze3d.platform.InputConstants.getKey(confirmKey); }
            catch (IllegalArgumentException e) { confirmKey = "key.keyboard.left.alt"; }
        }
    }

    public static void load(Path directory) {
        file = directory.resolve("holdmytorch-client.json");
        if (!Files.exists(file)) return;
        try {
            ClientConfig loaded = JSON.fromJson(Files.readString(file), ClientConfig.class);
            if (loaded == null) throw new IOException("Empty config");
            loaded.validate();
            current = loaded;
        } catch (IOException | RuntimeException e) {
            LoggerFactory.getLogger("holdmytorch").error("Cannot read {}; using defaults without overwriting it", file, e);
        }
    }

    public void validate() {
        if (torch == null) torch = new Rule();
        if (drop == null) drop = new Rule();
        if (keys == null) keys = new LinkedHashMap<>();
        keys.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        torch.validate(); drop.validate(); keys.values().forEach(Rule::validate);
        brightnessThreshold = Math.max(0, Math.min(15, brightnessThreshold));
    }

    public static boolean save() {
        current.validate();
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, JSON.toJson(current));
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            InputFilter.reset();
            return true;
        } catch (IOException e) {
            LoggerFactory.getLogger("holdmytorch").error("Cannot save {}", file, e);
            return false;
        }
    }
}
