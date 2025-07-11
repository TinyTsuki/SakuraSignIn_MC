package xin.vanilla.sakura.util.plugin;


import net.minecraft.entity.player.PlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public class PluginUtils {
	private static final String GTS = "SakuraKnot";
	private static final Logger LOGGER = LogManager.getLogger();

	public static Plugin getPlugin(String plugin) {
		return Bukkit.getPluginManager().getPlugin(plugin);
	}

	static Class<?> getPluginClass() {
		return getPlugin(GTS).getClass();
	}

	public static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method;
	}

	public static double getBalance(PlayerEntity player) {
		return getBalance(player.getUUID());
	}

	public static double getBalance(UUID player) {
		try {
			Class<?> clazz = getPluginClass();
			Method method = getMethod(clazz, "getBalance", UUID.class);
			return (double) method.invoke(clazz, player);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return 0d;
	}

	public static void depositPlayer(PlayerEntity player, double price) {
		depositPlayer(player.getUUID(), price);
	}

	public static void depositPlayer(UUID player, double price) {
		try {
			Class<?> clazz = getPluginClass();
			Method method = getMethod(clazz, "depositPlayer", UUID.class, Double.TYPE);
			method.invoke(clazz, player, price);
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean depositPlayerWithResult(UUID player, double price) {
		try {
			Class<?> clazz = getPluginClass();
			Method method = getMethod(clazz, "depositPlayer", UUID.class, Double.TYPE);
			method.invoke(clazz, player, price);
			return true;
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static void withdrawPlayer(PlayerEntity player, double price) {
		withdrawPlayer(player.getUUID(), price);
	}

	public static void withdrawPlayer(UUID player, double price) {
		try {
			Class<?> clazz = getPluginClass();
			Method method = getMethod(clazz, "withdrawPlayer", UUID.class, Double.TYPE);
			method.invoke(clazz, player, price);
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean withdrawPlayerWithResult(UUID player, double price) {
		try {
			Class<?> clazz = getPluginClass();
			Method method = getMethod(clazz, "withdrawPlayer", UUID.class, Double.TYPE);
			method.invoke(clazz, player, price);
			return true;
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static void withdrawPlayer(UUID userUuid, double price, double tax) {
		price += tax;
		withdrawPlayer(userUuid, price);
	}

	public static boolean checkBukkitInstalled() {
		try {
			Server server = Bukkit.getServer();
			return server != null;
		} catch (NoClassDefFoundError e) {
			LOGGER.error("No Bukkit installed on this platform");
		}
		return false;
	}
}
