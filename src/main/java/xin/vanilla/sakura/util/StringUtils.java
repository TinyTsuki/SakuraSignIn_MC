package xin.vanilla.sakura.util;


import lombok.NonNull;
import xin.vanilla.sakura.enums.EnumMCColor;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class StringUtils {

    public static final String METHOD_SET_PREFIX = "set";
    public static final String METHOD_GET_PREFIX = "get";
    public static final String COMMON_MARK = ",<.>/?;:'\"[{]}\\|`~!@#$%^&*()-_=+，《。》、？；：‘“【】·~！￥…（）—";

    /**
     * NanoId默认随机字符串长度
     */
    public static final int DEFAULT_SIZE = 21;

    /**
     * 字符串是否为常用标点符号
     */
    public static boolean isCommonMark(String s) {
        if (s.length() != 1) return false;
        return COMMON_MARK.contains(s);
    }

    /**
     * 转义正则特殊字符  $()*+.[]?\^{},|
     */
    public static String escapeExprSpecialWord(String keyword) {
        if (!StringUtils.isNullOrEmpty(keyword)) {
            String[] fbsArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
            for (String key : fbsArr) {
                if (keyword.contains(key)) {
                    keyword = keyword.replace(key, "\\" + key);
                }
            }
        }
        return keyword;
    }

    /**
     * 将字符串转为逻辑真假
     *
     * @param s 0|1|真|假|是|否|true|false|y|n|t|f
     */
    public static boolean stringToBoolean(String s) {
        if (null == s) return false;
        switch (s.toLowerCase().trim()) {
            case "1":
            case "真":
            case "是":
            case "true":
            case "y":
            case "t":
                return true;
            case "0":
            case "假":
            case "否":
            case "false":
            case "n":
            case "f":
            default:
                return false;
        }
    }

    /**
     * 将String[][] 格式化为 String
     */
    public static String convertToString(String[][] stringArray, String x, String y) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stringArray.length; i++) {
            for (int j = 0; j < stringArray[i].length; j++) {
                sb.append(stringArray[i][j]);
                if (j < stringArray[i].length - 1) {
                    sb.append(x);
                }
            }
            if (i < stringArray.length - 1) {
                sb.append(y);
            }
        }
        return sb.toString();
    }

    public static boolean isNullOrEmpty(String s) {
        return null == s || s.isEmpty();
    }

    public static boolean isNullOrEmptyEx(String s) {
        return null == s || s.trim().isEmpty();
    }

    public static boolean isNotNullOrEmpty(String s) {
        return !isNullOrEmpty(s);
    }

    public static boolean isNotNullOrEmptyEx(String s) {
        return !isNullOrEmptyEx(s);
    }

    public static boolean isNotNull(Object s) {
        return s != null;
    }

    /**
     * @param s 字符串
     * @return 空字符串or本身
     */
    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String substring(String s, int start, int end) {
        if (isNullOrEmpty(s)) {
            return "";
        }
        int length = s.length();
        if (end < start) {
            return s;
        }
        if (length >= start && length >= end) {
            return s.substring(start, end);
        }
        return s;
    }

    public static String substring(String s, int start) {
        if (isNullOrEmpty(s)) {
            return "";
        }
        int length = s.length();
        if (start > length) {
            return s;
        }
        return s.substring(start);
    }

    public static String substringEnd(String s, int len) {
        if (isNullOrEmpty(s)) {
            return "";
        }
        int length = s.length();
        if (len > length) {
            return s;
        }
        return s.substring(0, length - len);
    }

    public static String toString(String s, String emptyDefault) {
        return StringUtils.isNullOrEmpty(s) ? emptyDefault : s;
    }

    /**
     * 替换换行符
     */
    @NonNull
    public static String replaceLine(String s) {
        if (s == null) return "";
        return s.replaceAll("<br>", "\n")
                .replaceAll("\\\\n", "\n")
                .replaceAll("\\\\r", "\r")
                .replaceAll("\\n", "\n")
                .replaceAll("\\r", "\r")
                .replaceAll("\r\n", "\n");
    }

    public static int getLineCount(String s) {
        if (StringUtils.isNullOrEmpty(s)) return 0;
        return StringUtils.replaceLine(s).split("\n").length;
    }

    public static int toInt(String s) {
        return toInt(s, 0);
    }

    public static int toInt(String s, int defaultValue) {
        int result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static long toLong(String s) {
        return toLong(s, 0);
    }

    public static long toLong(String s, long defaultValue) {
        long result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static float toFloat(String s) {
        return toFloat(s, 0);
    }

    public static float toFloat(String s, float defaultValue) {
        float result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = Float.parseFloat(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static double toDouble(String s) {
        return toDouble(s, 0);
    }

    public static double toDouble(String s, double defaultValue) {
        double result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static BigDecimal toBigDecimal(String s) {
        return toBigDecimal(s, BigDecimal.ZERO);
    }

    public static BigDecimal toBigDecimal(String s, BigDecimal defaultValue) {
        BigDecimal result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    /**
     * 整数转罗马数字
     */
    public static String intToRoman(int num) {
        StringBuilder roman = new StringBuilder();
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 0; i < values.length; i++) {
            while (num >= values[i]) {
                roman.append(symbols[i]);
                num -= values[i];
            }
        }
        return roman.toString();
    }

    /**
     * 转百分数
     */
    public static String toPercent(double num) {
        return toPercent(num, 2);
    }

    /**
     * 转百分数
     */
    public static String toPercent(double num, int scale) {
        return String.format(String.format("%%.%df%%%%", scale), num * 100);
    }

    /**
     * 转百分数
     */
    public static String toPercent(BigDecimal num) {
        return toPercent(num.doubleValue());
    }

    /**
     * 转百分数
     */
    public static String toPercent(BigDecimal num, int scale) {
        return toPercent(num.doubleValue(), scale);
    }

    public static String toFixed(double d, int scale) {
        return new BigDecimal(d).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    public static String toFixedEx(double d, int scale) {
        return toFixed(d, scale).replaceAll("(\\.\\d*?)0+$", "$1").replaceAll("[.]$", "");
    }

    public static String toFixedEx(BigDecimal d, int scale) {
        return d.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replaceAll("(\\.\\d*?)0+$", "$1").replaceAll("[.]$", "");
    }

    /**
     * 获取指定数量的某个字符串
     */
    public static String getString(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    public static final String FORMAT_REGEX = "%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

    /**
     * 自定义格式化方法，支持位置重排
     *
     * @param string 格式化字符串
     * @param args   参数
     */
    public static String format(String string, Object... args) {
        StringBuilder result = new StringBuilder();
        // 使用正则匹配格式化占位符
        Pattern pattern = Pattern.compile(FORMAT_REGEX);
        Matcher matcher = pattern.matcher(string);
        int i = 0;
        while (matcher.find()) {
            // 获取当前占位符
            String placeholder = matcher.group();

            // 获取位置标识符，如 %1$s 中的 1
            int index = placeholder.contains("$") ? toInt(placeholder.split("\\$")[0].substring(1)) - 1 : -1;
            // 如果占位符中没有显式的数字索引，则默认按顺序处理
            if (index == -1) {
                index = i;
            }
            // 检查是否有足够的参数
            String formattedArg = placeholder;
            if (index < args.length) {
                formattedArg = formatArgument(placeholder, args[index]);
            }
            // 替换占位符为对应的参数
            string = string.replaceFirst(Pattern.quote(placeholder), Matcher.quoteReplacement(formattedArg));
            i++;
        }
        return string;
    }

    /**
     * 根据占位符的类型格式化参数
     *
     * @param placeholder 占位符
     * @param arg         参数
     */
    private static String formatArgument(String placeholder, Object arg) {
        if (arg == null) return "null";
        try {
            return String.format(placeholder.replaceAll("^%\\d+\\$", "%"), arg);
        } catch (Exception e) {
            return arg.toString();
        }
    }

    public static int argbToHex(String argb) {
        try {
            if (argb.startsWith("#")) {
                return (int) Long.parseLong(argb.substring(1), 16);
            } else if (argb.startsWith("0x")) {
                return (int) Long.parseLong(argb.substring(2), 16);
            } else {
                return (int) Long.parseLong(argb, 16);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * RGB颜色转换为Minecraft颜色代码
     *
     * @param color 颜色值 (ARGB: 0xAARRGGBB 或 RGB: 0xRRGGBB)
     */
    public static String argbToMinecraftColorString(int color) {
        return "§" + argbToMinecraftColor(color).getCode();
    }

    public static EnumMCColor argbToMinecraftColor(int color) {
        // 获取 RGB 分量
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        // 颜色匹配
        double closestDistance = Double.MAX_VALUE;
        // 默认为白色
        EnumMCColor result = EnumMCColor.WHITE;
        for (EnumMCColor mcColor : EnumMCColor.values()) {
            int colorRGB = mcColor.getColor();
            int r = (colorRGB >> 16) & 0xFF;
            int g = (colorRGB >> 8) & 0xFF;
            int b = colorRGB & 0xFF;
            // 加权欧几里得距离计算
            double distance = Math.sqrt(2 * Math.pow(red - r, 2) + 4 * Math.pow(green - g, 2) + 3 * Math.pow(blue - b, 2));
            if (distance < closestDistance) {
                closestDistance = distance;
                result = mcColor;
            }
        }
        return result;
    }

    /**
     * 将字符串转换为驼峰命名
     */
    public static String toPascalCase(String input) {
        if (isNullOrEmptyEx(input)) return "";

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            } else {
                capitalizeNext = true;
            }
        }
        return result.toString();
    }

    public static String padOptimizedLeft(Object value, int length, String padChar) {
        return padOptimized(value, length, padChar, true);
    }

    public static String padOptimizedRight(Object value, int length, String padChar) {
        return padOptimized(value, length, padChar, false);
    }

    /**
     * 在字符串前或后补全字符
     */
    public static String padOptimized(Object value, int length, String padChar, boolean left) {
        String str = String.valueOf(value);
        int currentLength = str.length();

        if (length <= currentLength) return str;

        char paddingChar = padChar != null && !padChar.isEmpty() ? padChar.charAt(0) : ' ';
        char[] chars = new char[length - currentLength];
        Arrays.fill(chars, paddingChar);

        return left ? new String(chars) + str : str + new String(chars);
    }

    public static String md5(byte[] data) {
        return digest(data);
    }

    public static String md5(String text) {
        return digest(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String md5(InputStream input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private static String digest(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return bytesToHex(md.digest(data));
        } catch (Exception e) {
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

}
