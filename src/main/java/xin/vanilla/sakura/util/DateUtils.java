package xin.vanilla.sakura.util;

import lombok.Getter;
import lombok.NonNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ServerConfig;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class DateUtils {

    public static final String HMS_FORMAT = "HH:mm:ss";
    public static final String ISO_MONTH_FORMAT = "yyyyMM";
    public static final String ISO_DATE_FORMAT = "yyyyMMdd";
    public static final String ISO_DATE_TIME_FORMAT = "yyyyMMddHHmmss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String TAIWAN_DATE_FORMAT = "yyyy/MM/dd";
    public static final String TAIWAN_DATE_TIME_FORMAT = "yyyy/MM/dd HH:mm:ss";
    public static final String POINT_DATE_FORMAT = "yyyy.MM.dd";
    public static final String CHINESE_DATE_FORMAT = "yyyy年MM月dd日";

    private static final String[] WEEK_NAMES = new String[]{"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    public DateUtils() {
    }

    public static String getWeekName(int week) {
        return WEEK_NAMES[week - 1];
    }

    private static Locale getLocalFromLanguageTag(String languageTag) {
        if (StringUtils.isNullOrEmpty(languageTag)) {
            languageTag = Locale.getDefault().getLanguage();
        } else if (languageTag.contains("_") || languageTag.contains("-")) {
            languageTag = languageTag.replace("-", "_").split("_")[0];
        }
        return Locale.forLanguageTag(languageTag);
    }

    private static Date formatEx(String dateStr, String pattern) {
        if (StringUtils.isNullOrEmpty(dateStr)) {
            return null;
        } else {
            try {
                return (new SimpleDateFormat(pattern)).parse(dateStr);
            } catch (ParseException e) {
                return null;
            }
        }
    }

    private static List<String> getStrings(String pattern) {
        List<String> formats = new ArrayList<>();
        if (!StringUtils.isNullOrEmpty(pattern)) {
            formats.add(pattern);
        } else {
            formats.add("HH:mm:ss");
            formats.add("yyyy");
            formats.add("yyyyMM");
            formats.add("yyyyMMdd");
            formats.add("yyyy-MM-dd");
            formats.add("yyyy-MM-dd HH:mm");
            formats.add("yyyy-MM-dd HH:mm:ss");
            formats.add("yyyy年MM月dd日");
            formats.add("yyyy/MM/ddHHmm");
            formats.add("yyyy/MM/dd");
            formats.add("yyyyMMddHHmmss");
            formats.add("yyyy.MM.dd");
        }
        return formats;
    }

    @Getter
    public enum DateUnit {
        MILLISECOND(1, 1000, "ms"),
        SECOND(2, 60, "s"),
        MINUTE(3, 60, "m"),
        HOUR(4, 24, "h"),
        DAY(5, 30, "d");

        private final int code;
        private final int base;
        private final String unit;

        DateUnit(int code, int base, String unit) {
            this.code = code;
            this.base = base;
            this.unit = unit;
        }

        public static DateUnit valueOf(int code) {
            for (DateUnit status : DateUnit.values()) {
                if (status.code == code) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Invalid code: " + code);
        }

        public static int getMaxCode() {
            return Arrays.stream(DateUnit.values()).max(Comparator.comparingInt(DateUnit::getCode)).orElse(DateUnit.values()[DateUnit.values().length - 1]).getCode();
        }
    }

    public static Date format(String strTime) {
        return format(strTime, null);
    }

    public static Date format(String strTime, String pattern) {
        if (StringUtils.isNullOrEmpty(strTime)) {
            return null;
        } else {
            Date date = null;
            List<String> formats = getStrings(pattern);
            for (String format : formats) {
                if ((strTime.indexOf("-") <= 0 || format.contains("-")) && (strTime.contains("-") || format.indexOf("-") <= 0) && strTime.length() <= format.length()) {
                    date = formatEx(strTime, format);
                    if (date != null) {
                        break;
                    }
                }
            }
            return date;
        }
    }

    public static String toLocalStringYear(Date date, String languageTag) {
        LocalDateTime localDateTime = getLocalDateTime(date);
        if (getLocalFromLanguageTag(languageTag).getLanguage().equalsIgnoreCase(Locale.CHINESE.getLanguage())) {
            return localDateTime.format(DateTimeFormatter.ofPattern("yyyy年"));
        } else {
            return localDateTime.format(DateTimeFormatter.ofPattern("yyyy"));
        }
    }

    public static String toLocalStringMonth(Date date, String languageTag) {
        LocalDateTime localDateTime = getLocalDateTime(date);
        if (getLocalFromLanguageTag(languageTag).getLanguage().equalsIgnoreCase(Locale.CHINESE.getLanguage())) {
            return localDateTime.format(DateTimeFormatter.ofPattern("M月"));
        } else {
            return localDateTime.getMonth().getDisplayName(TextStyle.SHORT, getLocalFromLanguageTag(languageTag));
        }
    }

    public static String toLocalStringWeek(Date date, String languageTag) {
        LocalDateTime localDateTime = getLocalDateTime(date);
        return localDateTime.getDayOfWeek().getDisplayName(TextStyle.SHORT, getLocalFromLanguageTag(languageTag));
    }

    public static String toLocalStringDay(Date date, String languageTag) {
        LocalDateTime localDateTime = getLocalDateTime(date);
        if (getLocalFromLanguageTag(languageTag).getLanguage().equalsIgnoreCase(Locale.CHINESE.getLanguage())) {
            return localDateTime.format(DateTimeFormatter.ofPattern("d日"));
        } else {
            return localDateTime.format(DateTimeFormatter.ofPattern("dd"));
        }
    }

    public static String toString(Date date) {
        return toString(date, DATE_FORMAT);
    }

    public static String toDateTimeString(Date date) {
        return toString(date, DATETIME_FORMAT);
    }

    public static String toString(Date date, String pattern) {
        if (date == null) date = new Date();
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        return format.format(date);
    }

    public static Date toTheDayStart(Date date) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(date);
        ca.set(Calendar.HOUR_OF_DAY, 0);
        ca.set(Calendar.MINUTE, 0);
        ca.set(Calendar.SECOND, 0);
        ca.set(Calendar.MILLISECOND, 0);
        return ca.getTime();
    }

    public static Date toTheDayEnd(Date date) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(date);
        ca.set(Calendar.HOUR_OF_DAY, 23);
        ca.set(Calendar.MINUTE, 59);
        ca.set(Calendar.SECOND, 59);
        ca.set(Calendar.MILLISECOND, 999);
        return ca.getTime();
    }

    public static int toDateInt(Date date) {
        return date == null ? 0 : Integer.parseInt(toString(date, ISO_DATE_FORMAT));
    }

    public static long toDateTimeInt(Date date) {
        return date == null ? 0 : Long.parseLong(toString(date, ISO_DATE_TIME_FORMAT));
    }

    /**
     * 获取给定日期的月份
     */
    public static int getMonthOfDate(Date date) {
        if (date == null) {
            date = new Date();
        }

        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate.getMonthValue();
    }

    /**
     * 获取月初的星期
     */
    public static int getDayOfWeekOfMonthStart(Date date) {
        if (date == null) {
            date = new Date();
        }

        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1);
        return localDate.getDayOfWeek().getValue();
    }

    /**
     * 获取给定日期的年份
     */
    public static int getYearPart(Date date) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(date);
        return ca.get(Calendar.YEAR);
    }

    /**
     * 获取给定日期是当年的第几天
     */
    public static int getDayOfYear(Date date) {
        return getLocalDateTime(date).getDayOfYear();
    }

    /**
     * 获取给定日期是当月的第几天
     */
    public static int getDayOfMonth(Date date) {
        return getLocalDateTime(date).getDayOfMonth();
    }

    /**
     * 获取给定日期是星期几
     */
    public static int getDayOfWeek(Date date) {
        return getLocalDateTime(date).getDayOfWeek().getValue();
    }

    /**
     * 获取给定日期的小时
     */
    public static int getHourOfDay(Date date) {
        return getLocalDateTime(date).getHour();
    }

    /**
     * 获取给定日期的分钟
     */
    public static int getMinuteOfHour(Date date) {
        return getLocalDateTime(date).getMinute();
    }

    /**
     * 获取给定日期的秒
     */
    public static int getSecondOfMinute(Date date) {
        return getLocalDateTime(date).getSecond();
    }

    /**
     * 获取给定年份的总天数
     */
    public static int getDaysOfYear(Date date) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(date);
        return ca.getActualMaximum(Calendar.DAY_OF_YEAR);
    }

    /**
     * 获取给定年份的总天数
     */
    public static int getDaysOfYear(int year) {
        Calendar ca = Calendar.getInstance();
        ca.set(year, Calendar.JANUARY, 1);
        return ca.getActualMaximum(Calendar.DAY_OF_YEAR);
    }

    /**
     * 获取给定月份的总天数
     */
    public static int getDaysOfMonth(Date date) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(date);
        return ca.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /**
     * 将两个Date对象的时间相加
     *
     * @param date     基础日期
     * @param duration 间隔时间
     */
    public static Date addDate(Date date, Duration duration) {
        return getDate(getLocalDateTime(date).plus(duration));
    }

    public static Date addYear(Date current, int year) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.YEAR, year);
        return calendar.getTime();
    }

    public static Date addYear(Date current, float year) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        double floor = Math.floor(year);
        calendar.add(Calendar.YEAR, (int) floor);
        calendar.add(Calendar.DATE, (int) (DateUtils.getDaysOfYear(current) * (year - floor)));
        return calendar.getTime();
    }

    public static Date addMonth(Date current, int month) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.MONTH, month);
        return calendar.getTime();
    }

    public static Date addMonth(Date current, float month) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        double floor = Math.floor(month);
        calendar.add(Calendar.MONTH, (int) floor);
        calendar.add(Calendar.DATE, (int) (DateUtils.getDaysOfMonth(calendar.getTime()) * (month - floor)));
        return calendar.getTime();
    }

    public static Date addDay(Date current, int day) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.DATE, day);
        return calendar.getTime();
    }

    public static Date addDay(Date current, float day) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        double floor = Math.floor(day);
        calendar.add(Calendar.DATE, (int) floor);
        calendar.add(Calendar.MILLISECOND, (int) (24 * 60 * 60 * 1000 * (day - floor)));
        return calendar.getTime();
    }

    public static Date addHour(Date current, int hour) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.HOUR, hour);
        return calendar.getTime();
    }

    public static Date addHour(Date current, float hour) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        double floor = Math.floor(hour);
        calendar.add(Calendar.HOUR, (int) floor);
        calendar.add(Calendar.MILLISECOND, (int) (60 * 60 * 1000 * (hour - floor)));
        return calendar.getTime();
    }

    public static Date addMinute(Date current, int minute) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.MINUTE, minute);
        return calendar.getTime();
    }

    public static Date addMinute(Date current, float minute) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        double floor = Math.floor(minute);
        calendar.add(Calendar.MINUTE, (int) floor);
        calendar.add(Calendar.MILLISECOND, (int) (60 * 1000 * (minute - floor)));
        return calendar.getTime();
    }

    public static Date addSecond(Date current, int second) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.SECOND, second);
        return calendar.getTime();
    }

    public static Date addSecond(Date current, float second) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        double floor = Math.floor(second);
        calendar.add(Calendar.SECOND, (int) floor);
        calendar.add(Calendar.MILLISECOND, (int) (1000 * (second - floor)));
        return calendar.getTime();
    }

    public static Date addMilliSecond(Date current, int ms) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.MILLISECOND, ms);
        return calendar.getTime();
    }

    public static LocalDateTime getLocalDateTime(Date date) {
        if (date == null) {
            date = new Date();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static LocalDate getLocalDate(Date date) {
        if (date == null) {
            date = new Date();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static Date getDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public static Date getDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date getDate(int year, int month, int day, int hour, int minute, int second, int milliSecond) {
        Calendar cal = Calendar.getInstance();
        // cal.setLenient(false);
        cal.set(year, month - 1, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, milliSecond);
        return cal.getTime();
    }

    public static Date getDate(String yearStr, String monthStr, String dayStr, String hourStr, String minuteStr, String secondStr, String milliSecondStr) {
        Date date = null;
        if (StringUtils.isNotNullOrEmpty(yearStr) && StringUtils.isNotNullOrEmpty(monthStr) && StringUtils.isNotNullOrEmpty(dayStr)) {
            int year = 0, month = 0, day = 0, hour = 0, minute = 0, second = 0, milliSecond = 0;
            try {
                year = Integer.parseInt(yearStr);
                month = Integer.parseInt(monthStr);
                day = Integer.parseInt(dayStr);
                hour = StringUtils.isNotNullOrEmpty(hourStr) ? 0 : Integer.parseInt(hourStr);
                minute = StringUtils.isNotNullOrEmpty(minuteStr) ? 0 : Integer.parseInt(minuteStr);
                second = StringUtils.isNotNullOrEmpty(secondStr) ? 0 : Integer.parseInt(secondStr);
                milliSecond = Integer.parseInt(milliSecondStr);
            } catch (NumberFormatException ignored) {
            }
            if (year > 0 && month > 0 && day > 0) {
                date = getDate(year, month, day, hour, minute, second, milliSecond);
            }
        }
        return date;
    }

    public static Date getDate(int year, int month, int day, int hour, int minute, int second) {
        return getDate(year, month, day, hour, minute, second, 0);
    }

    public static Date getDate(String yearStr, String monthStr, String dayStr, String hourStr, String minuteStr, String secondStr) {
        return getDate(yearStr, monthStr, dayStr, hourStr, minuteStr, secondStr, null);
    }

    public static Date getDate(int year, int month, int day) {
        return getDate(year, month, day, 0, 0, 0, 0);
    }

    public static Date getDate(String yearStr, String monthStr, String dayStr) {
        return getDate(yearStr, monthStr, dayStr, null, null, null, null);
    }

    /**
     * @param date yyyyMMdd 或 yyyyMMddHHmmss
     */
    public static Date getDate(long date) {
        return format(String.valueOf(date));
    }

    /**
     * 计算两个日期之间的年数间隔
     */
    public static long yearsOfTwo(Date startDate, Date endDate) {
        return Period.between(getLocalDate(startDate), getLocalDate(endDate)).getYears();
    }

    /**
     * 计算两个日期之间的月数间隔
     */
    public static long monthsOfTwo(Date startDate, Date endDate) {
        return ChronoUnit.MONTHS.between(getLocalDateTime(startDate), getLocalDateTime(endDate));
    }

    /**
     * 计算两个日期之间的天数间隔
     */
    public static long daysOfTwo(Date startDate, Date endDate) {
        return ChronoUnit.DAYS.between(getLocalDateTime(startDate), getLocalDateTime(endDate));
    }

    /**
     * 计算两个日期之间的周数间隔
     */
    public static long weeksOfTwo(Date startDate, Date endDate) {
        return ChronoUnit.WEEKS.between(getLocalDateTime(startDate), getLocalDateTime(endDate));
    }

    /**
     * 计算两个时间之间的小时数间隔
     */
    public static long hoursOfTwo(Date startDateTime, Date endDateTime) {
        return ChronoUnit.HOURS.between(getLocalDateTime(startDateTime), getLocalDateTime(endDateTime));
    }

    /**
     * 计算两个时间之间的分钟数间隔
     */
    public static long minutesOfTwo(Date startDateTime, Date endDateTime) {
        return ChronoUnit.MINUTES.between(getLocalDateTime(startDateTime), getLocalDateTime(endDateTime));
    }

    /**
     * 计算两个时间之间的秒数间隔
     */
    public static long secondsOfTwo(Date startDateTime, Date endDateTime) {
        return ChronoUnit.SECONDS.between(getLocalDateTime(startDateTime), getLocalDateTime(endDateTime));
    }

    /**
     * 计算两个时间之间的毫秒数间隔
     */
    public static long millisOfTwo(Date startDateTime, Date endDateTime) {
        return ChronoUnit.MILLIS.between(getLocalDateTime(startDateTime), getLocalDateTime(endDateTime));
    }

    /**
     * 计算两个时间之间的详细间隔日期
     * 返回一个包含年月日时分秒毫秒的Date对象
     */
    public static Duration dateOfTwo(Date startDateTime, Date endDateTime) {
        LocalDateTime localDateTime = getLocalDateTime(startDateTime);
        return Duration.between(localDateTime, getLocalDateTime(endDateTime));
    }

    /**
     * 添加时间
     *
     * @param date     被计算的时间
     * @param duration 间隔时间(整数部分为小时, 小数部分为分钟)
     */
    public static Date addDate(Date date, double duration) {
        int coolingHour = (int) Math.floor(duration);
        int coolingMinute = (int) Math.floor((coolingHour - duration) * 100);
        return DateUtils.addMinute(DateUtils.addHour(date, coolingHour), coolingMinute);
    }

    public static Date getServerDate() {
        // 校准服务器时间
        return getServerValidDate(new Date());
    }

    public static Date getServerValidDate(Date date) {
        // 校准客户端传过来的时间
        Date originalTime = DateUtils.format(ServerConfig.SERVER_TIME.get());
        Date actualTime = DateUtils.format(ServerConfig.ACTUAL_TIME.get());
        if (originalTime.compareTo(actualTime) != 0) {
            date = DateUtils.addDate(date, DateUtils.dateOfTwo(originalTime, actualTime));
        }
        return date;
    }

    /**
     * 获取客户端校准时间(服务端时间)
     */
    public static Date getClientDate() {
        return getClientValidDate(new Date());
    }

    public static Date getClientValidDate(Date date) {
        Date originalTime = DateUtils.format(SakuraSignIn.getClientServerTime().getKey());
        Date actualTime = DateUtils.format(SakuraSignIn.getClientServerTime().getValue());
        if (originalTime.compareTo(actualTime) != 0) {
            date = DateUtils.addDate(date, DateUtils.dateOfTwo(originalTime, actualTime));
        }
        return date;
    }

    /**
     * 比较两个日期是否相等
     *
     * @param date1     日期1
     * @param date2     日期2
     * @param precision 精度
     */
    public static boolean equals(Date date1, Date date2, @NonNull DateUnit precision) {
        if (date1 == date2) return true;
        else if (date1 == null) {
            return false;
        } else if (date2 == null) {
            return false;
        } else {
            long l = DateUtils.millisOfTwo(date1, date2);
            long l2 = 1;
            for (int i = 1; i < precision.getCode(); i++) {
                l2 *= DateUnit.valueOf(i).getBase();
            }
            return Math.abs(l) < l2;
        }
    }

    /**
     * 将时间转换为最大单位
     *
     * @param time    时间长度
     * @param curUnit 当前单位
     */
    public static String toMaxUnitString(double time, DateUnit curUnit) {
        return toMaxUnitString(time, curUnit, 0, 2);
    }

    /**
     * 将时间转换为最大单位, 最小数值
     * <p>
     * 10000s显示出来太长了, 不如将单位放大, 数值缩小, decimalPlaces = 2, maxNineCount = 2, -> 166.67m<p>
     * 166.67m这也太长了, 继续缩小数值 -> 2.78h<p>
     * 还是长, 但是不能缩小单位了, 那就省略小数部分 decimalPlaces = 0, -> 3h<p>
     *
     * @param time          时间长度
     * @param curUnit       当前单位
     * @param decimalPlaces 小数位数, 不能小于0哦
     * @param maxNineCount  最大整数位数, 不能小于1哦
     */
    public static String toMaxUnitString(double time, DateUnit curUnit, int decimalPlaces, int maxNineCount) {
        String formatPattern = "%." + decimalPlaces + "f";
        String result = String.format(formatPattern, time) + curUnit.getUnit();
        if (decimalPlaces < 0) decimalPlaces = 0;
        if (maxNineCount <= 0) maxNineCount = 1;
        if (String.valueOf((int) time).length() > maxNineCount) {
            int code = curUnit.getCode() + 1;
            if (code <= DateUnit.getMaxCode() && time > curUnit.getBase()) {
                result = toMaxUnitString(time / curUnit.getBase(), DateUnit.valueOf(code), decimalPlaces, maxNineCount);
            } else {
                // 当到达最大单位后，将整数与小数部分填充为指定数量的9
                StringBuilder ninePart = new StringBuilder();
                StringBuilder decimal = new StringBuilder();
                for (int i = 0; i < maxNineCount; i++) {
                    ninePart.append("9");
                }
                for (int i = 0; i < decimalPlaces; i++) {
                    decimal.append("9");
                }
                if (decimalPlaces > 0) {
                    result = ninePart + "." + decimal + "+" + curUnit.getUnit();
                } else {
                    result = ninePart + "+" + curUnit.getUnit();
                }

            }
        }
        return result;
    }

    /**
     * 计算连续签到天数
     *
     * @param dateList 日期列表
     * @param current  当前日期
     */
    public static int calculateContinuousDays(List<Date> dateList, Date current) {
        if (dateList == null || dateList.isEmpty()) {
            return 0;
        }
        dateList.add(current);
        dateList = dateList.stream()
                .map(DateUtils::toDateInt)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(DateUtils::getDate)
                .collect(Collectors.toList());
        if (current == null) current = dateList.get(0);
        int continuousDays = 0;
        for (int i = 0; i < dateList.size(); i++) {
            Date date = dateList.get(i);
            if (i == 0 && DateUtils.toDateInt(current) <= DateUtils.toDateInt(date))
                continuousDays++;
            else if (i - 1 >= 0 && DateUtils.toDateInt(DateUtils.addDay(dateList.get(i - 1), -1)) == DateUtils.toDateInt(date))
                continuousDays++;
            else break;
        }
        return continuousDays;
    }
}
