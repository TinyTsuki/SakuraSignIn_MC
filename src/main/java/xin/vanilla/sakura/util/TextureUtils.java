package xin.vanilla.sakura.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.potion.EffectInstance;
import net.minecraft.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class TextureUtils {
    /**
     * 药水图标文件夹路径
     */
    public static final String DEFAULT_EFFECT_DIR = "textures/mob_effect/";

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 获取药水效果图标
     */
    public static ResourceLocation getEffectTexture(EffectInstance effectInstance) {
        ResourceLocation effectIcon;
        ResourceLocation registryName = effectInstance.getEffect().getRegistryName();
        if (registryName != null) {
            effectIcon = new ResourceLocation(registryName.getNamespace(), DEFAULT_EFFECT_DIR + registryName.getPath() + ".png");
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
            IResource resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            // 打开资源输入流并加载为 NativeImage
            try (InputStream inputStream = resource.getInputStream()) {
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
