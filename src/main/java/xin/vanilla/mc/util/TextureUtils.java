package xin.vanilla.mc.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.Texture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.mc.SakuraSignIn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextureUtils {
    /**
     * 默认主题文件名
     */
    public static final String DEFAULT_THEME = "sign_in_calendar_original.png";
    /**
     * 外部主题文件夹路径
     */
    public static final String CUSTOM_THEME_DIR = "config/sakura_sign_in/themes/";
    /**
     * 内部主题文件夹路径
     */
    public static final String INTERNAL_THEME_DIR = "textures/gui/";
    /**
     * 药水图标文件夹路径
     */
    public static final String DEFAULT_EFFECT_DIR = "textures/mob_effect/";

    private static final Logger LOGGER = LogManager.getLogger();

    public static ResourceLocation loadCustomTexture(String textureName) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        textureName = textureName.replaceAll("\\\\", "/");
        textureName = textureName.startsWith("./") ? textureName.substring(2) : textureName;
        ResourceLocation customTextureLocation = new ResourceLocation(SakuraSignIn.MODID, TextureUtils.getSafeThemePath(textureName));
        if (!TextureUtils.isTextureAvailable(customTextureLocation)) {
            if (!textureName.startsWith(INTERNAL_THEME_DIR)) {
                File textureFile;
                // 指定外部路径的纹理文件
                if (textureName.startsWith(CUSTOM_THEME_DIR)) {
                    textureFile = new File(Minecraft.getInstance().gameDirectory, textureName);
                } else {
                    textureFile = new File(textureName);
                }
                // 检查文件是否存在
                if (!textureFile.exists()) {
                    LOGGER.warn("Texture file not found: {}", textureFile.getAbsolutePath());
                    customTextureLocation = new ResourceLocation(SakuraSignIn.MODID, INTERNAL_THEME_DIR + DEFAULT_THEME);
                } else {
                    try (InputStream inputStream = Files.newInputStream(textureFile.toPath())) {
                        // 直接从InputStream创建NativeImage
                        NativeImage nativeImage = NativeImage.read(inputStream);
                        // 创建DynamicTexture并注册到TextureManager
                        DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                        textureManager.register(customTextureLocation, dynamicTexture);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to load texture: {}", textureFile.getAbsolutePath());
                        LOGGER.error(e);
                        customTextureLocation = new ResourceLocation(SakuraSignIn.MODID, INTERNAL_THEME_DIR + DEFAULT_THEME);
                    }
                }
            }
        }
        return customTextureLocation;
    }

    public static String getSafeThemePath(String path) {
        return path.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }

    public static boolean isTextureAvailable(ResourceLocation resourceLocation) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        Texture texture = textureManager.getTexture(resourceLocation);
        if (texture == null) {
            return false;
        }
        // 确保纹理已经加载
        return texture.getId() != -1;
    }

    public static List<File> getPngFilesInDirectory(String directoryPath) {
        List<File> pngFiles = new ArrayList<>();
        // 获取 .minecraft 文件夹的根目录
        File configDir = new File(Minecraft.getInstance().gameDirectory, directoryPath);
        // 检查目录是否存在
        if (!configDir.exists() || !configDir.isDirectory()) {
            LOGGER.error("The directory does not exist: {}", configDir.getAbsolutePath());
        } else {
            // 使用文件过滤器仅获取 .png 文件
            File[] files = configDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            if (files != null) {
                Collections.addAll(pngFiles, files);
            }
        }
        return pngFiles;
    }

    /**
     * 获取药水效果图标
     */
    public static ResourceLocation getEffectTexture(EffectInstance effectInstance) {
        ResourceLocation registryName = effectInstance.getEffect().getRegistryName();
        ResourceLocation effectIcon;
        if (registryName != null) {
            effectIcon = new ResourceLocation(registryName.getNamespace(), DEFAULT_EFFECT_DIR + registryName.getPath() + ".png");
        } else {
            // TODO 添加自定义效果图标
            effectIcon = new ResourceLocation(SakuraSignIn.MODID, INTERNAL_THEME_DIR + DEFAULT_THEME);
        }
        return effectIcon;
    }
}