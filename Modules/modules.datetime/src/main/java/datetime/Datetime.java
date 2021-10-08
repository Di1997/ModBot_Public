package datetime;

import classes.Tools;
import classes.modules.BotModule;
import com.google.gson.reflect.TypeToken;
import core.Constants;
import datetime.Parser.Parser;
import datetime.Parser.ParsedData;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Datetime extends BotModule {
    private static final String SERVER_ID = "869769742620172308";
    private final UUID MODULE_ID = UUID.randomUUID();
    private TextChannel REPORT_CHANNEL;

    Helpers.DatetimeData datetimeData;
    final Object dataLock = new Object();
    Thread timeThread;
    boolean started = false;

    public Datetime() {
        whitelistServer(SERVER_ID);

        Name = "Date module";
        Description = "Date module for server date/time/weather";
    }

    @Override
    public void readyModule(JDA jda) throws Exception {
        REPORT_CHANNEL = jda.getTextChannelById(Constants.Module.REPORT_CHANNEL);
        datetimeData =  GetData(SERVER_ID);
        if(datetimeData == null) {
            datetimeData = new Helpers.DatetimeData();

            WriteData(SERVER_ID, datetimeData);
        }

        if(datetimeData.weekday == null)
            datetimeData.weekday = Helpers.Weekday.Mon;
        if(datetimeData.timeOfDay == null)
            datetimeData.timeOfDay = Helpers.TimeOfDay.Morning;
        if(datetimeData.weather == null)
            datetimeData.weather = Helpers.Weather.Clear;

        if(!datetimeData.freeze)
            StartThread();

        //datetimeData.nextUpdate = Instant.now().minus(3*365, ChronoUnit.DAYS).toString();
        SendInfo("Module started");
        UpdateChannels(datetimeData);
    }

    @Override
    protected void process(GenericEvent event, String command) throws Exception {
        if(event instanceof GuildMessageReceivedEvent) {
            synchronized (dataLock) {
                InnerProcess((GuildMessageReceivedEvent) event, command);
            }
        }
    }

    private void InnerProcess(GuildMessageReceivedEvent event, String command) {
        if(event.getAuthor().isBot()
                || event.isWebhookMessage()
                || event.getMember() == null
                || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) return;

        Queue<String> queue = new LinkedList<>(List.of(command.split(" ")));

        if(queue.size() > 0 && !queue.poll().toLowerCase(Locale.ROOT).equals("set")) return;
        if(queue.size() == 0) {
            Tools.SendMessage(event, """
                    Available commands:
                    
                    `time`
                    `date`
                    `weather`
                    `interval`
                    `pause`
                    `channel`
                    `enum`
                    `reload`
                    `event`""", "Info");

            return;
        }

        String innerCommand = queue.poll().toLowerCase(Locale.ROOT);
        String args = "";

        if(queue.size() > 0)
            args = String.join(" ", queue);

        datetimeData = GetData(SERVER_ID);
        switch (innerCommand) {
            //.set time [h]:[m]
            case "time" -> {
                if(args.equals("")) {
                    Tools.SendMessage(event, String.format("Current time: %s\n\n`.set time [h]:[m]`", datetimeData.getTimeString()), "Info");
                } else {
                    args = args.strip();
                    String validTimeError = ValidateTime(args);

                    if(!validTimeError.equals("")) {
                        Tools.SendMessage(event, String.format("%s\n\n`.set time [h]:[m]`", validTimeError), "Error");
                        return;
                    }

                    datetimeData.hour = Integer.parseInt(args.split(":")[0]);
                    datetimeData.minute = Integer.parseInt(args.split(":")[1]);


                    SetTimeOfDay(datetimeData);

                    datetimeData.nextUpdate = null;
                    if(!datetimeData.freeze) {
                        StopThread();
                        StartThread();
                    }

                    UpdateChannels(datetimeData);

                    Tools.SendMessage(event, String.format("Time of day set to: %s", datetimeData.getTimeString()), "Success");
                }
            }
            //.set date [m].[d]
            case "date" -> {

                if(args.equals("")) {
                    Tools.SendMessage(event, String.format("Current date: %s\n\n`.set date [m].[d]`", datetimeData.getDateString()), "Info");
                } else {
                    args = args.strip();
                    String[] dateStrArray = args.split("\\.");

                    if(dateStrArray.length != 2) {
                        Tools.SendMessage(event, "Date is in wrong format!\n\n`.set date [m].[d]`", "Error");
                        return;
                    }

                    int day = 0;
                    int month = 0;

                    try {
                        month = Integer.parseInt(dateStrArray[0].strip());
                    } catch (NumberFormatException e) {
                        Tools.SendMessage(event, "Month is not a number!\n\n`.set date [m].[d]`", "Error");
                        return;
                    }

                    try {
                        day = Integer.parseInt(dateStrArray[1].strip());
                    } catch (NumberFormatException e) {
                        Tools.SendMessage(event, "Day is not a number!\n\n`.set date [m].[d]`", "Error");
                        return;
                    }

                    if(month < 1 || month > 12) {
                        Tools.SendMessage(event, "Month must be between 1 and 12!\n\n`.set date [m].[d]`", "Error");
                        return;
                    }

                    if(day < 1 || day > Helpers.Months.get(month)) {
                        Tools.SendMessage(event, String.format("For month %s day must be between 1 and %s!\n\n`.set date [m].[d]`", month, Helpers.Months.get(month)), "Error");
                        return;
                    }

                    datetimeData.day = day;
                    datetimeData.month = month;
                    datetimeData.timeOfDay = Helpers.TimeOfDay.Morning;
                    datetimeData.nextUpdate = null;
                    datetimeData.weather = WeatherEngine.getWeather(datetimeData);

                    if(!datetimeData.freeze) {
                        StopThread();
                        StartThread();
                    }

                    UpdateChannels(datetimeData);
                    Tools.SendMessage(event, String.format("Date set to: %s", datetimeData.getDateString()), "Success");
                }
            }
            //.set weather [Weather]
            case "weather" -> {

                if(args.equals("")) {
                    Tools.SendMessage(event, String.format("Current weather: %s\n\n`.set weather [%s]`", datetimeData.weather.name(), String.join("|", Helpers.getEnumNames(Helpers.Weather.class))), "Info");
                } else {
                    args = args.strip();
                    HashMap<String, Helpers.Weather> weatherNames = Helpers.getEnumNames(Helpers.Weather.class).stream().collect(
                            Collectors.toMap(
                                    k -> k.toLowerCase(Locale.ROOT),
                                    Helpers.Weather::valueOf,
                                    (e, r) -> r,
                                    HashMap::new));

                    if(weatherNames.containsKey(args.toLowerCase(Locale.ROOT))) {
                        datetimeData.weather = weatherNames.get(args.toLowerCase(Locale.ROOT));
                    } else {
                        Tools.SendMessage(event, String.format("Unable to set weather!\n\n`.set weather [%s]`", String.join("|", Helpers.getEnumNames(Helpers.Weather.class))), "Error");
                        return;
                    }

                    UpdateChannels(datetimeData);

                    Tools.SendMessage(event, String.format("Weather set to: %s", datetimeData.weather.name()), "Success");
                }
            }
            //.set interval 15 (5 minutes per 15 rl minutes)
            case "interval" -> {
                if(args.equals("")) {
                    Tools.SendMessage(event, String.format("Current interval: %s\n\n`.set interval [Amount of RL minutes between 5 IC minutes]`", datetimeData.interval), "Info");
                } else {
                    args = args.strip();

                    try {
                        int interval = Integer.parseInt(args);

                        if(interval < 10) {
                            Tools.SendMessage(event, "Interval must be more or equal to 10!\n\n`.set interval [Amount of RL minutes between 5 IC minutes]`", "Error");
                            return;
                        }

                        datetimeData.interval = interval;

                        if(!datetimeData.freeze) {
                            datetimeData.nextUpdate = null;
                            StopThread();
                            StartThread();
                        }
                        Tools.SendMessage(event, String.format("Duration set to: %s", datetimeData.interval), "Success");
                    } catch (NumberFormatException e) {
                        Tools.SendMessage(event, "Invalid interval!\n\n`.set interval [Amount of RL minutes between 5 IC minutes]`", "Error");
                        return;
                    }
                }
            }
            //.set pause [1|0]
            case "pause" -> {
                if(args.equals("")) {
                    Tools.SendMessage(event, String.format("Is time paused?: %s\n\n`.set pause [1|0]`", datetimeData.freeze?1:0), "Info");
                } else {
                    args = args.strip();

                    try {
                        int data = Integer.parseInt(args);

                        if(data != 0 && data != 1) {
                            Tools.SendMessage(event, "Unable to set pause!\n\n`.set pause [1|0]`", "Error");
                            return;
                        }

                        datetimeData.freeze = data == 1;

                        UpdateChannels(datetimeData);

                        if(datetimeData.freeze)
                            StopThread();
                        else
                            StartThread();

                        Tools.SendMessage(event, String.format("Pause set to: %s", datetimeData.freeze?1:0), "Success");
                    } catch (NumberFormatException e) {
                        Tools.SendMessage(event, "Unable to set pause!\n\n`.set pause [1|0]`", "Error");
                        return;
                    }
                }
            }
            //.set channel [Channel] [Template]
            case "channel" -> {
                Queue<String> innerArgs = new LinkedList<>(List.of(args.split(" ")));

                if(innerArgs.size() == 0 || innerArgs.peek() == null || innerArgs.peek().equals("")) {
                    Tools.SendMessage(event, String.format("""
                        Channels:
                        %s
                        
                        `.set channel [Channel] [Template]`""",
                            GetChannels(datetimeData)), "Info");

                    return;
                }

                ProcessChannelCommand(event, innerArgs);
            }
            //.set enum [Enum] [Override]
            case "enum" -> {
                if(args.length() == 0) {
                    Tools.SendMessage(event, String.format("""
                            Current Enums:
                            %s
                            
                            `.set enum [Enum] [Override]`
                            """, GetEnumOverrides(datetimeData)), "Info");
                } else {
                    Queue<String> innerArgs = new LinkedList<String>(List.of(args.split(" ")));
                    String enumName = innerArgs.poll();
                    if(enumName == null) return;

                    enumName = Helpers.GetEnumName(enumName);

                    if(enumName.equals("")) {
                        Tools.SendMessage(event, """
                            Enum not found!
                            
                            `.set enum [Enum] [Override]`
                            """, "Error");

                        return;
                    }

                    if(innerArgs.size() == 0) {
                        String enumOverride = datetimeData.GetEnumOverride(enumName);
                        if(enumOverride.equals("")) enumOverride = enumName;

                        Tools.SendMessage(event, String.format("""
                            %s -> %s
                            
                            `.set enum [Enum] [Override]`
                            """, enumName, enumOverride), "Info");

                        return;
                    }

                    String override = String.join(" ", innerArgs);
                    Parser parser = new Parser();
                    parser.openingChar = '"';
                    parser.closingChar = '"';
                    ParsedData data = parser.Process(override);
                    if(data.GroupStrings.size() == 1) {
                        override = data.GroupStrings.stream().findFirst().get();
                    }

                    datetimeData.SetEnumOverride(enumName, override);
                    UpdateChannels(datetimeData);
                    Tools.SendMessage(event, String.format("%s is set to %s", enumName, datetimeData.GetEnumOverride(enumName)), "Success");
                }
            }
            //.set event [Channel] [Weekday] [h]:[m] [Text]
            case "event" -> {
                if(args.equals("")) {
                    Tools.SendMessage(event, "Sets event for the channel.\n\n`.set event [Channel] [Weekday] [h]:[m] [Text]`", "Info");
                    return;
                }

                Parser parser = new Parser();
                parser.splittingChar = '"';
                ParsedData dataArgs = parser.Process(args);
                Queue<String> innerArgs = new LinkedList<>(dataArgs.SplitStrings);

                if(innerArgs.size() < 4) {
                    Tools.SendMessage(event, "Insufficient amount of arguments!\n\n`.set event [Channel] [Weekday] [h]:[m] [Text]`", "Error");
                    return;
                }

                String error = ProcessEventCommand(innerArgs);
                if(!error.equals("")) {
                    Tools.SendMessage(event, String.format("%s\n\n`.set event [Channel] [Weekday] [h]:[m] [Text]`", error), "Error");
                    return;
                }

                Tools.SendMessage(event, "Event set successfully!\n\n`.set event [Channel] [Weekday] [h]:[m] [Text]`", "Success");
            }
            //.set reload
            case "reload" -> {
                datetimeData = GetData(SERVER_ID);
                UpdateChannels(datetimeData);

                if(!datetimeData.freeze) {
                    StopThread();
                    StartThread();
                }
                Tools.SendMessage(event, "Reload complete!", "Success");
            }
        }

        WriteData(SERVER_ID, datetimeData);
    }

    private String ProcessEventCommand(Queue<String> args) {
        if(args.size() < 4)
            return "Insufficient amount of arguments!";

        HashSet<String> channels = new HashSet<>();
        String channelsString = args.poll();

        for(String channelString : channelsString.split(",")) {
            channelString = channelString.strip();

            TextChannel channel = GetChannel(channelString);

            if(channel == null)
                return String.format("Channel not found! [%s]", channelString);

            if(!channel.canTalk())
                return  String.format("Unable to send messages to this channel! [%s]", channel.getName());

            channels.add(channel.getId());
        }

        HashSet<Helpers.Weekday> weekdays = new HashSet<>();
        String weekdaysString = args.poll();

        if(weekdaysString == null) {
            return "Err: weekdaysString == null";
        }

        for(String weekdayString : weekdaysString.split(",")) {
            weekdayString = weekdayString.strip();

            String weekdayName = Helpers.GetEnumName(weekdayString, Helpers.Weekday.class);
            if(weekdayName.equals(""))
                return String.format("Invalid weekday name! [%s]", weekdayString);

            weekdays.add(Helpers.Weekday.valueOf(weekdayName));
        }

        HashSet<int[]> times = new HashSet<>();
        String timesString = args.poll();

        if(timesString == null) {
            return "Err: timesString == null";
        }

        for(String timeString : timesString.split(",")) {
            timeString = timeString.strip();

            String timeError = ValidateTime(timeString);
            if(!timeError.equals(""))
                return String.format("Time error! [%s]\n%s", timeString, timeError);

            int hour = Integer.parseInt(timeString.split(":")[0]);
            int minute = Integer.parseInt(timeString.split(":")[1]);

            times.add(new int[]{hour, minute});
        }

        String text = String.join(" ", args).strip();

        for(String channel : channels) {
            for(Helpers.Weekday weekday : weekdays) {
                for(int[] time : times) {
                    datetimeData.SetEvent(channel, weekday, time, text);
                }
            }
        }

        return "";
    }

    private TextChannel GetChannel(String channel) {
        channel = channel.replaceAll("[#<> ]", "").strip();

        try {
            return JDA.getTextChannelById(channel);
        } catch (Exception e) {
            return null;
        }
    }

    private String ValidateTime(String time) {
        int hour;
        int minute;

        if(time.split(":").length != 2) {
            return "Invalid time format!";
        }

        try {
            hour = Integer.parseInt(time.split(":")[0]);

            if(hour < 0 || hour > 23) {
                return "Hour must be between 0 and 23!";
            }
        } catch (NumberFormatException e) {
            return "Hour is not a number!";
        }

        try {
            minute = Integer.parseInt(time.split(":")[1]);

            if(minute < 0 || minute > 59) {
                return "Minute must be between 0 and 59!";
            }

            if(minute % 5 != 0) {
                return "Minute must be a multiple of 5!";
            }

        } catch (NumberFormatException e) {
            return "Minute is not a number!";
        }

        return "";
    }

    private String GetEnumOverrides(Helpers.DatetimeData data) {
        StringBuilder builder = new StringBuilder();

        for(Enum<?> _enum: Helpers.GetEnums()) {
            String override = data.GetEnumOverride(_enum.name());
            if(override.equals("")) override = _enum.name();

            builder.append(String.format("%s -> %s\n", _enum.name(), override));
        }

        if(builder.isEmpty()) return "None";
        return builder.toString();
    }

    private String ProcessStringToChannel(String data) {
        if(data == null || data.equals("")) return "None";

        return String.format("<#%s>", data);
    }

    private String GetChannels(Helpers.DatetimeData data) {
        if(!HasValidChannels(data)) return "None";

        StringBuilder builder = new StringBuilder();
        for(String channelId: data.customChannels.keySet()) {
            builder.append(String.format("%s: %s\n", ProcessStringToChannel(channelId), data.GetChannelDeco(channelId)));
        }

        return builder.toString();
    }

    private void ProcessChannelCommand(GuildMessageReceivedEvent event, Queue<String> args) {
        String strChannel = args.poll();

        GuildChannel channel;

        try {
            if(strChannel == null) {
                Tools.SendMessage(event,  """
                    Channel not found!
                    
                    `.set channel [Channel] [Template]`""", "Error");

                return;
            }
            channel = JDA.getGuildChannelById(strChannel);

            if(channel == null) {
                Tools.SendMessage(event,  """
                    Channel not found!
                    
                    `.set channel [Channel] [Template]`""", "Error");

                return;
            }
        } catch (NumberFormatException e) {
            Tools.SendMessage(event,  """
                    Channel id is not a valid snowflake!
                    
                    `.set channel [Channel] [Template]`""", "Error");

            return;
        }

        if(args.size() == 0) {
            Tools.SendMessage(event, String.format("""
                    Channel: %s
                    Template: %s
                    
                    `.set channel [Channel] [Template]`""", ProcessStringToChannel(channel.getId()), datetimeData.GetChannelDeco(channel.getId())), "Info");
            return;
        }

        String data = String.join(" ", args).strip();

        ParsedData parsedData = new Parser().Process(data);

        if(!ValidateParsedData(parsedData)) {
            Tools.SendMessage(event, String.format("""
                    Template string must contain at least one of: %s!
                    
                    `.set channel [Channel] [Template]`""", String.join(", ", Helpers.ParsedDataMap.keySet())), "Error");

            return;
        }

        datetimeData.SetChannelDeco(channel.getId(), data);
        UpdateChannel(channel.getId(), datetimeData);

        Tools.SendMessage(event,String.format("""
                    Channel has been updated!
                    
                    Channel: %s
                    Template: %s
                    
                    `.set channel [Channel] [Template]`""", channel.getId(), data), "Success");
    }

    private void CyclingThread(Helpers.DatetimeData data) {
        if(!HasValidChannels(data)) {
            data.freeze = true;
            data.customChannels.clear();
            WriteData(SERVER_ID, data);
            return;
        }

        if(data.freeze || data.nextUpdate == null)
            return;

        Instant nextUpdate = Instant.parse(data.nextUpdate);
        if(Instant.now().isAfter(nextUpdate)) {
            synchronized (dataLock) {
                while (Instant.now().isAfter(nextUpdate)) {
                    ProcessTick(data, true);

                    if(DEBUG) UpdateChannels(data);

                    nextUpdate = data.AddInterval(nextUpdate);
                }

                UpdateChannels(data);
                data.nextUpdate = nextUpdate.toString();
                WriteData(SERVER_ID, data);
            }
        }

        AtomicReference<Long> wait = new AtomicReference<>();
        wait.set(Duration.between(Instant.now(), nextUpdate).toMillis());

        AtomicReference<Instant> atomicNextUpdate = new AtomicReference<>(nextUpdate);

        while (Wait(wait.get())) {
            synchronized (dataLock) {
                if(!started) return;

                if(Instant.now().isBefore(atomicNextUpdate.get())) {
                    wait.set(Duration.between(Instant.now(), nextUpdate).plus(500, ChronoUnit.MILLIS).toMillis());
                    SendInfo(String.format("Early trigger, wait has been set to: %s", wait.get()));

                    if(wait.get() < 0) {
                        SendInfo("<@195592551120502794> Critical error!");
                        wait.set(0L);
                    }
                    continue;
                }

                SendInfo("Processing...");

                data = GetData(SERVER_ID);
                ProcessTick(data, false);

                if(!HasValidChannels(data)) {
                    data.freeze = true;
                    data.customChannels.clear();
                    WriteData(SERVER_ID, data);
                    break;
                }

                UpdateChannels(data);

                atomicNextUpdate.set(data.AddInterval(atomicNextUpdate.get()));
                data.nextUpdate = atomicNextUpdate.get().toString();
                wait.set(Duration.between(Instant.now(), atomicNextUpdate.get()).toMillis());
                WriteData(SERVER_ID, data);
            }
        }
    }

    private void ProcessTick(Helpers.DatetimeData data, boolean catchUp) {
        data.minute += 5;

        if(data.minute == 60) {
            data.minute = 0;
            data.hour++;
        }

        if(data.weekday != Helpers.Weekday.Sat && data.weekday != Helpers.Weekday.Sun) {
            if (data.hour == 8 && data.minute == 30) {
                data.hour = 15;
            }
        }

        if(data.hour == 24) {
            data.hour = 6;
            ProcessNewDay(data);
        }

        if(!catchUp || DEBUG)
            CheckEvents(data);

        SetTimeOfDay(data);
    }

    private void CheckEvents(Helpers.DatetimeData data) {
        for(String channel : data.events.keySet()) {
            String event = data.GetEvent(channel, data.weekday, new int[]{data.hour, data.minute});

            if(event.equals("")) {
                data.SetEvent(channel, data.weekday, new int[]{data.hour, data.minute}, "");
                continue;
            }

            TextChannel guildChannel = GetChannel(channel);
            if(guildChannel == null) {
                data.events.remove(channel);
                 continue;
            }

            if(DEBUG) {
                System.out.printf("Event::%s\nChannel: %s\n", event, channel);
            } else {
                if(guildChannel.canTalk())
                    guildChannel.sendMessage(event).queue();
            }
        }
    }

    private void SetTimeOfDay(Helpers.DatetimeData data) {
        if(data.hour >= 0 && data.hour < 6)
            data.timeOfDay = Helpers.TimeOfDay.Night;
        else if(data.hour >= 6 && data.hour < 12)
            data.timeOfDay = Helpers.TimeOfDay.Morning;
        else if(data.hour >= 12 && data.hour < 18)
            data.timeOfDay = Helpers.TimeOfDay.Afternoon;
        else if(data.hour >= 18 && data.hour <= 23)
            data.timeOfDay = Helpers.TimeOfDay.Evening;
    }

    private void UpdateChannels(Helpers.DatetimeData data) {
        for (String channelId : data.customChannels.keySet()) {
            UpdateChannel(channelId, data);
        }
    }

    private void UpdateChannel(String channelId, Helpers.DatetimeData data) {
        if(data.customChannels.get(channelId) == null) return;

        GuildChannel guildChannel = JDA.getGuildChannelById(channelId);

        if(guildChannel == null) {
            data.customChannels.remove(channelId);
            return;
        }

        String format = new Parser().Process(data.GetChannelDeco(channelId), Helpers.ParsedDataMap).NewString;

        String timeOfDay = data.timeOfDay.name();
        if(!data.GetEnumOverride(timeOfDay).equals(""))
            timeOfDay = data.GetEnumOverride(timeOfDay);

        String weather = data.weather.name();
        if(!data.GetEnumOverride(timeOfDay).equals(""))
            weather = data.GetEnumOverride(timeOfDay);

        String weekday = data.weekday.name();
        if(!data.GetEnumOverride(timeOfDay).equals(""))
            weekday = data.GetEnumOverride(timeOfDay);

        String status = GetCurrentStatus(data).name();
        if(!data.GetEnumOverride(status).equals(""))
            status = data.GetEnumOverride(status);

        String channelName = String.format(format,
                timeOfDay,
                Helpers.FormatToDoubleZero(String.valueOf(data.month)),
                Helpers.FormatToDoubleZero(String.valueOf(data.day)),
                weather,
                weekday,
                Helpers.FormatToDoubleZero(String.valueOf(data.hour)),
                Helpers.FormatToDoubleZero(String.valueOf(data.minute)),
                status
                );

        if(!DEBUG) {
            if (!channelName.equals(guildChannel.getName()))
                guildChannel.getManager().setName(channelName).queue();
        } else
            System.out.println(channelName);
    }

    private Helpers.Status GetCurrentStatus(Helpers.DatetimeData data) {
        return data.freeze? Helpers.Status.Paused: Helpers.Status.Resumed;
    }

    private boolean HasValidChannels(Helpers.DatetimeData data) {
        for (String channelId: data.customChannels.keySet()) {
            try {
                if(JDA.getGuildChannelById(channelId) != null) {
                    return true;
                } else {
                    data.customChannels.remove(channelId);
                }
            } catch (NumberFormatException | NullPointerException ignored) {
                data.customChannels.remove(channelId);
            }
        }

        return false;
    }

    private void ProcessNewDay(Helpers.DatetimeData data) {
        data.day++;
        if(data.day > Helpers.Months.get(data.month)) {
            data.month++;
            data.day = 1;

            if(data.month > 12)
                data.month = 1;
        }

        data.weather = WeatherEngine.getWeather(data);

        switch (data.weekday) {
            case Mon -> data.weekday = Helpers.Weekday.Tue;
            case Tue -> data.weekday = Helpers.Weekday.Wed;
            case Wed -> data.weekday = Helpers.Weekday.Thu;
            case Thu -> data.weekday = Helpers.Weekday.Fri;
            case Fri -> data.weekday = Helpers.Weekday.Sat;
            case Sat -> data.weekday = Helpers.Weekday.Sun;
            case Sun -> data.weekday = Helpers.Weekday.Mon;
        }
    }

    private boolean Wait(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private void StopThread() {
        if(!started) return;

        datetimeData.nextUpdate = null;
        datetimeData.freeze = true;
        timeThread.interrupt();
        started = false;

        WriteData(SERVER_ID, datetimeData);
    }

    private void StartThread() {
        if(started) return;

        if(datetimeData.nextUpdate == null)
            datetimeData.nextUpdate = datetimeData.CalculateNextUpdate(Instant.now());
        else
            try {
                Instant __ = Instant.parse(datetimeData.nextUpdate);
            } catch (DateTimeParseException e) {
                datetimeData.nextUpdate = datetimeData.CalculateNextUpdate(Instant.now());
            }

        datetimeData.freeze = false;
        timeThread = new Thread(() -> CyclingThread(datetimeData));
        timeThread.start();
        started = true;

        WriteData(SERVER_ID, datetimeData);
    }

    private Helpers.DatetimeData GetData(String serverId) {
        return Tools.convertToClass(getDatabase().get(serverId), new TypeToken<>() {
        });
    }

    private void WriteData(String serverId, Helpers.DatetimeData data) {
        getDatabase().set(serverId, Tools.convertToJson(data));
    }

    private boolean ValidateParsedData(ParsedData data) {
        boolean valid = false;
        HashSet<String> keys = new HashSet<>(data.MatchingStrings.keySet());

        for(String key: Helpers.ParsedDataMap.keySet()) {
            if(keys.contains(key)) {
                valid = true;
                break;
            }
        }

        return valid;
    }

    private void SendInfo(String info) {
        if(REPORT_CHANNEL == null) return;
        REPORT_CHANNEL.sendMessage(String.format("[%s]: %s",MODULE_ID,info)).queue();
    }

    @Override
    protected void deInit() {
        if(!datetimeData.freeze) {
            SendInfo("Module Datetime stopping. Stopping thread...");

            started = false;
            timeThread.interrupt();
            WriteData(SERVER_ID, datetimeData);
        }
    }
}
