package datetime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class Helpers {
    public static HashMap<Integer, Integer> Months = new HashMap<>();
    public static HashMap<String, String> ParsedDataMap = new HashMap<>();

    static {
        Months.put(1,31);
        Months.put(2,28);
        Months.put(3,31);
        Months.put(4,30);
        Months.put(5,31);
        Months.put(6,30);
        Months.put(7,31);
        Months.put(8,31);
        Months.put(9,30);
        Months.put(10,31);
        Months.put(11,30);
        Months.put(12,31);

        ParsedDataMap.put("{time}","%1$s");
        ParsedDataMap.put("{month}","%2$s");
        ParsedDataMap.put("{day}","%3$s");
        ParsedDataMap.put("{weather}","%4$s");
        ParsedDataMap.put("{weekday}","%5$s");
        ParsedDataMap.put("{hour}","%6$s");
        ParsedDataMap.put("{minute}","%7$s");
        ParsedDataMap.put("{pause}","%8$s");
    }

    public static class DatetimeData {
        public HashMap<String, String> customChannels = new HashMap<>();
        public HashMap<String, String> overrides = new HashMap<>();
        public HashMap<String, Event> events = new HashMap<>();

        public String nextUpdate;
        public boolean freeze = true;
        public int interval = 20;

        public int hour = 12;
        public int minute = 0;
        public int day = 1;
        public int month = 4;
        public Weekday weekday = Weekday.Mon;
        public TimeOfDay timeOfDay = TimeOfDay.Evening;
        public Weather weather = Weather.Clear;

        public String CalculateNextUpdate(Instant from) {
            return AddInterval(from).toString();
        }

        public Instant AddInterval(Instant from) {
            return from.plus(interval, ChronoUnit.MINUTES);
        }

        public String GetChannelDeco(String channelId) {
            if (!customChannels.containsKey(channelId)) return "";
            return new String(Base64.getDecoder().decode(customChannels.get(channelId)), StandardCharsets.UTF_8);
        }

        public void SetChannelDeco(String channelId, String string) {
            customChannels.put(channelId, Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8)));
        }

        public String GetEnumOverride(String enumName) {
            if(!overrides.containsKey(enumName)) return "";
            return new String(Base64.getDecoder().decode(overrides.get(enumName)), StandardCharsets.UTF_8);
        }

        public void SetEnumOverride(String enumName, String enumOverride) {
            if(enumOverride.equals(enumName)) {
                overrides.remove(enumName);
                return;
            }

            overrides.put(enumName, Base64.getEncoder().encodeToString(enumOverride.getBytes(StandardCharsets.UTF_8)));
        }

        public String GetEvent(String channel, Weekday weekday, int[] time) {
            if(!events.containsKey(channel)) return "";
            Event channelEvents = events.get(channel);
            if(!channelEvents.events.containsKey(weekday)) return "";
            var weekdayEvents = events.get(channel).events.get(weekday);

            String hour = FormatToDoubleZero(String.valueOf(time[0]));
            String minute = FormatToDoubleZero(String.valueOf(time[1]));
            String timeString = hour + ":" + minute;

            if(!weekdayEvents.containsKey(timeString)) return "";
            return new String(Base64.getDecoder().decode(weekdayEvents.get(timeString)), StandardCharsets.UTF_8);
        }

        public void SetEvent(String channel, Weekday weekday, int[] time, String string) {
            boolean deletion = Objects.equals(string, "");
            if(!events.containsKey(channel)) {
                if(deletion) return;
                events.put(channel, new Event());
            }

            var channelEvents = events.get(channel).events;

            if(!channelEvents.containsKey(weekday)) {
                if(deletion) return;
                channelEvents.put(weekday, new HashMap<>());
            }
            var weekdayEvents = channelEvents.get(weekday);

            String hour = FormatToDoubleZero(String.valueOf(time[0]));
            String minute = FormatToDoubleZero(String.valueOf(time[1]));
            String timeString = hour + ":" + minute;

            if(deletion) {
                weekdayEvents.remove(timeString);
                if(channelEvents.get(weekday).size() == 0)
                    channelEvents.remove(weekday);
                if(events.get(channel).events.size() == 0)
                    events.remove(channel);
            }
            else {
                weekdayEvents.put(timeString, Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8)));
            }
        }

        String getDateString() {
            return String.format("%s.%s",
                    FormatToDoubleZero(String.valueOf(month)),
                    FormatToDoubleZero(String.valueOf(day)));
        }

        String getTimeString() {
            return String.format("%s:%s",
                    FormatToDoubleZero(String.valueOf(hour)),
                    FormatToDoubleZero(String.valueOf(minute)));
        }
    }

    public static class Event {
        public HashMap<Weekday, HashMap<String, String>> events = new HashMap<>();
    }

    //region Enums
    public enum TimeOfDay {
        Morning,
        Afternoon,
        Evening,
        Night
    }

    public enum Weather {
        Cloudy,
        Clear,
        Rainy,
        Stormy,
        Snowy,
        Blizzard
    }

    public enum Weekday {
        Mon,
        Tue,
        Wed,
        Thu,
        Fri,
        Sat,
        Sun
    }

    public enum Status {
        Paused,
        Resumed
    }
    //endregion

    //region Functions

    static String FormatToDoubleZero(String string) {
        if(string.length() > 2) return string;
        return ("00" + string).substring(string.length());
    }

    static HashSet<Enum<?>> GetEnums() {
        HashSet<Enum<?>> enumHashSet = new LinkedHashSet<>();

        enumHashSet.addAll(List.of(Helpers.TimeOfDay.values()));
        enumHashSet.addAll(List.of(Helpers.Weather.values()));
        enumHashSet.addAll(List.of(Helpers.Weekday.values()));
        enumHashSet.addAll(List.of(Helpers.Status.values()));

        return enumHashSet;
    }

    static HashSet<String> getEnumNames(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).collect(Collectors.toCollection(HashSet::new));
    }

    static String GetEnumName(String enumName) {
        enumName = enumName.toLowerCase(Locale.ROOT).strip();

        String foundEnumName = GetEnumName(enumName, Helpers.TimeOfDay.class);
        if(!foundEnumName.equals("")) return foundEnumName;
        foundEnumName = GetEnumName(enumName, Helpers.Weather.class);
        if(!foundEnumName.equals("")) return foundEnumName;
        foundEnumName = GetEnumName(enumName, Helpers.Weekday.class);
        if(!foundEnumName.equals("")) return foundEnumName;
        foundEnumName = GetEnumName(enumName, Helpers.Status.class);
        if(!foundEnumName.equals("")) return foundEnumName;

        return "";
    }

    static String GetEnumName(String enumName, Class<? extends Enum<?>> enumClass) {
        enumName = enumName.toLowerCase(Locale.ROOT).strip();

        for(Enum<?> _enum : enumClass.getEnumConstants()) {
            String currentEnumName = _enum.name().toLowerCase(Locale.ROOT);
            if(enumName.equals(currentEnumName))
                return _enum.name();
        }

        return "";
    }

    //endregion
}
