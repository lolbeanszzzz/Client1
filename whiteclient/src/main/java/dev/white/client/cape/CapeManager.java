package dev.white.client.cape;

import dev.white.client.command.CommandManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Loads a user-selected cape locally. The custom texture is substituted only for the local
 * player's render state; it is not sent to a server or made visible to other clients.
 */
public final class CapeManager {
    private static final Identifier TEXTURE_ID = Identifier.of("whiteclient", "dynamic/kirx_cape");
    private static final Path CAPE_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("kirxclient/cape.png");
    private static boolean loaded;

    private CapeManager() {}

    /** Called on the client thread during mod initialization. */
    public static void loadSaved() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (Files.isRegularFile(CAPE_FILE)) {
                try {
                    registerPng(CAPE_FILE);
                    loaded = true;
                } catch (IOException e) {
                    CommandManager.say("Couldn't load your saved cape: " + e.getMessage());
                }
            }
        });
    }

    public static boolean hasCape() {
        return loaded;
    }

    public static SkinTextures withLocalCape(SkinTextures original) {
        AssetInfo.TextureAsset cape = new AssetInfo.TextureAssetInfo(TEXTURE_ID, TEXTURE_ID);
        return new SkinTextures(original.body(), cape, original.elytra(), original.model(), original.secure());
    }

    /** Opens a native desktop picker without blocking Minecraft's render thread. */
    public static void chooseCape() {
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Choose a KIRXCLIENT cape image");
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setFileFilter(new FileNameExtensionFilter(
                        "Cape images (PNG, JPEG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    Path selected = chooser.getSelectedFile().toPath();
                    MinecraftClient.getInstance().execute(() -> importCape(selected));
                }
            } catch (RuntimeException e) {
                MinecraftClient.getInstance().execute(() ->
                        CommandManager.say("Couldn't open the cape picker: " + e.getMessage()));
            }
        });
    }

    public static void removeCape() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                loaded = false;
                client.getTextureManager().destroyTexture(TEXTURE_ID);
                Files.deleteIfExists(CAPE_FILE);
                CommandManager.say("Custom cape removed.");
            } catch (IOException e) {
                CommandManager.say("Couldn't remove the saved cape: " + e.getMessage());
            }
        });
    }

    private static void importCape(Path source) {
        try {
            String ext = extension(source);
            if (!("png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext)
                    || "gif".equals(ext) || "bmp".equals(ext))) {
                throw new IOException("Choose a PNG, JPG, GIF, or BMP image.");
            }
            if (Files.size(source) > 10L * 1024 * 1024) {
                throw new IOException("Image must be smaller than 10 MB.");
            }

            // ImageIO decodes GIFs as their first frame. Store a normalized PNG so the selected
            // source can be moved or deleted after import.
            BufferedImage input = ImageIO.read(source.toFile());
            if (input == null) throw new IOException("This image format could not be decoded.");
            if (input.getWidth() < 1 || input.getHeight() < 1
                    || input.getWidth() > 4096 || input.getHeight() > 4096) {
                throw new IOException("Image dimensions must be between 1 and 4096 pixels.");
            }

            BufferedImage cape = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = cape.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(input, 0, 0, 64, 32, null);
            } finally {
                graphics.dispose();
            }

            Files.createDirectories(CAPE_FILE.getParent());
            if (!ImageIO.write(cape, "png", CAPE_FILE.toFile())) {
                throw new IOException("PNG encoding is unavailable.");
            }
            registerPng(CAPE_FILE);
            loaded = true;
            CommandManager.say("Cape loaded. It is visible on your client only; GIF uploads use their first frame.");
        } catch (IOException | RuntimeException e) {
            CommandManager.say("Cape upload failed: " + e.getMessage());
        }
    }

    private static void registerPng(Path path) throws IOException {
        NativeImage image;
        try (var stream = Files.newInputStream(path)) {
            image = NativeImage.read(stream);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        client.getTextureManager().destroyTexture(TEXTURE_ID);
        client.getTextureManager().registerTexture(TEXTURE_ID,
                new NativeImageBackedTexture(() -> "kirx-custom-cape", image));
    }

    private static String extension(Path path) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
