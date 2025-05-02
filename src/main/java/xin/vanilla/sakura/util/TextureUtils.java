package xin.vanilla.sakura.util;

import com.mojang.blaze3d.platform.NativeImage;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.effect.MobEffectInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

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
        ResourceLocation customTextureLocation = ResourceLocation.fromNamespaceAndPath(SakuraSignIn.MODID, TextureUtils.getSafeThemePath(textureName));
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
                customTextureLocation = ResourceLocation.fromNamespaceAndPath(SakuraSignIn.MODID, INTERNAL_THEME_DIR + DEFAULT_THEME);
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
                    customTextureLocation = ResourceLocation.fromNamespaceAndPath(SakuraSignIn.MODID, INTERNAL_THEME_DIR + DEFAULT_THEME);
                }
            }
        }
        return customTextureLocation;
    }

    public static String getSafeThemePath(String path) {
        return path.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }

    // 判断不了了捏
    // public static boolean isTextureAvailable(ResourceLocation resourceLocation) {
    //     TextureManager textureManager = Minecraft.getInstance().getTextureManager();
    //     AbstractTexture texture = textureManager.getTexture(resourceLocation);
    //     // 确保纹理已经加载
    //     return texture.getId() != -1;
    // }

    @NonNull
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
    public static ResourceLocation getEffectTexture(MobEffectInstance mobEffectInstance) {
        ResourceLocation effectIcon;
        ResourceLocation registryName = BuiltInRegistries.MOB_EFFECT.getKey(mobEffectInstance.getEffect().value());
        if (registryName != null) {
            effectIcon = ResourceLocation.fromNamespaceAndPath(registryName.getNamespace(), DEFAULT_EFFECT_DIR + registryName.getPath() + ".png");
        } else {
            effectIcon = null;
        }
        return effectIcon;
    }

    private static final Map<ResourceLocation, NativeImage> CACHE = new HashMap<>();

    /**
     * 从资源中加载纹理并转换为 NativeImage。
     *
     * @param texture 纹理的 ResourceLocation
     * @return 纹理对应的 NativeImage 或 null
     */
    public static NativeImage getTextureImage(ResourceLocation texture) {
        // 优先从缓存中获取
        if (CACHE.containsKey(texture)) {
            return CACHE.get(texture);
        }
        try {
            // 获取资源管理器
            Resource resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture);
            // 打开资源输入流并加载为 NativeImage
            try (InputStream inputStream = resource.open()) {
                NativeImage nativeImage = NativeImage.read(inputStream);
                CACHE.put(texture, nativeImage);
                return nativeImage;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load texture: {}", texture);
            return null;
        }
    }
}
