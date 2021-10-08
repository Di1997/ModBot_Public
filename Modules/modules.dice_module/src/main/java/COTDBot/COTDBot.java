package COTDBot;

import COTDBot.Methods.Methods;
import classes.modules.BotModule;
import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import core.BotMain;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageAction;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class COTDBot extends BotModule {
    private final HashMap<String, String> lastMessages;
    @SuppressWarnings("FieldCanBeLocal")
    private final boolean Debug = false;
    private String disguiseUserID;
    private HashMap<String, LastRolledModel> lastRolled;
    private boolean wasRefreshed;
    private JDA jda;
    private WebhookClient whClient;

    public COTDBot() {
        Name = "COTD dice module";
        Description = "Dice-rolling system. Exclusive for COTD server";

        wasRefreshed = true;
        lastMessages = new HashMap<>();
        lastRolled = new HashMap<>();
        whitelistServer("547167760011231268");

        addAllowedEvent(SlashCommandEvent.class);
        addAllowedEvent(ButtonClickEvent.class);

        registerCommand("roll", "Roll the dice", x -> {
            OptionMapping data = x.getOption("sides");
            try {
                Command command;

                if (data == null) {
                    Member member = x.getMember();
                    int sides;
                    if(member == null || lastRolled.get(member.getId()) == null) throw new IllegalArgumentException("Die sides unknown!");
                    sides = lastRolled.get(member.getId()).Last10Roll;
                    if(sides < 10 && sides % 10 != 0) throw new IllegalArgumentException("Die sides unknown!");

                    command = Parse(Integer.toString(sides));
                } else
                    command = Parse(data.getAsString());

                if(command == null) {
                    x.reply("Couldn't parse command!").setEphemeral(true).queue();
                    return;
                }

                if (command.Action != Action.ROLL)
                    throw new IllegalArgumentException("Not a rolling command");

                if(command.OperatorAction != Action.NONE && command.AdditionalNumber < 0)
                    throw new IllegalArgumentException("Modifying number must be more than 0!");

                if(command.Number < 0) throw new IllegalArgumentException("Number must be more than 0!");

                String message = RollTheDice(x.getMember(), command.Number, command.getOperator(), command.AdditionalNumber);
                x.deferReply(false).queue();
                RelayOrSendCommandMessage(x.getHook(), "Dice command result", message, true);
            } catch (IllegalArgumentException e) {
                x.reply(e.getMessage()).setEphemeral(true).queue();
            }
        }, new OptionData(OptionType.STRING, "sides", "How many sides die will have", false));

        registerCommand("list", "Shows list of last rolled players", x -> {
            String message = GetLastRolled(x.getTextChannel());
            x.deferReply(false).queue();
            RelayOrSendCommandMessage(x.getHook(), "List of players rolled", message, false);
        });

        registerButton("RollDice", x -> {
            try {
                Member member = x.getMember();
                int sides;
                Command command;

                if (member == null || lastRolled.get(member.getId()) == null)
                    throw new IllegalArgumentException("Die sides unknown!");
                sides = lastRolled.get(member.getId()).Last10Roll;
                if (sides < 10 && sides % 10 != 0) throw new IllegalArgumentException("Die sides unknown!");

                command = Parse(Integer.toString(sides));

                if (command.Action != Action.ROLL)
                    throw new IllegalArgumentException("Not a rolling command");

                if (command.OperatorAction != Action.NONE && command.AdditionalNumber < 0)
                    throw new IllegalArgumentException("Modifying number must be more than 0!");

                if (command.Number < 0) throw new IllegalArgumentException("Number must be more than 0!");

                String message = RollTheDice(x.getMember(), command.Number, command.getOperator(), command.AdditionalNumber);
                x.deferReply(false).queue();
                RelayOrSendCommandMessage(x.getHook(), "Dice button result", message, true);
            } catch (IllegalArgumentException e) {
                x.reply(e.getMessage()).setEphemeral(true).queue();
            }
        });

        registerButton("GetRolled", x -> {
            String message = GetLastRolled(x.getTextChannel());
            x.deferReply(false).queue();
            RelayOrSendCommandMessage(x.getHook(), "List of players rolled", message, false);
        });

        registerCommand(new CommandData("disguise", "Disguise bot as someone else")
        .addSubcommands(new SubcommandData("reset", "Resets disguise to it's default"),
                new SubcommandData("set", "Sets disguise user")
                .addOption(OptionType.USER, "user", "User to disguise as", true))
        , x -> {
            if(!CheckPermission(x.getMember())) {
                x.reply("Not enough permissions!").setEphemeral(true).queue();
                return;
            }
            if(x.getSubcommandName() != null && x.getSubcommandName().equals("set")) {
                OptionMapping data = x.getOption("user");
                if (data == null) {
                    x.reply("User data is null!").setEphemeral(true).queue();
                    return;
                }
                getDatabase().set("disguise", data.getAsUser().getId());
                disguiseUserID = data.getAsUser().getId();
                x.reply("Success!").setEphemeral(true).queue();
            } else if (x.getSubcommandName() != null && x.getSubcommandName().equals("reset")) {
                getDatabase().delete("disguise");
                disguiseUserID = "";
                x.reply("Success!").setEphemeral(true).queue();
            }
        });
    }

    //region Main Processes

    @Override
    protected void process(GenericEvent event, String command) {
        try {
            jda = event.getJDA();
            if(event instanceof GuildMessageReceivedEvent)
                InnerProcess((GuildMessageReceivedEvent) event);

            else if(event instanceof GuildMessageUpdateEvent)
                InnerProcess((GuildMessageUpdateEvent) event);

            else if(event instanceof SlashCommandEvent)
                processSlashCommand((SlashCommandEvent) event);

            else if(event instanceof ButtonClickEvent)
                processButton((ButtonClickEvent) event);
        } catch (Exception ignored) {

        }
    }

    @Override
    public void readyModule(JDA jda) {
        disguiseUserID = getDatabase().get("disguise");
        if(disguiseUserID == null) disguiseUserID = "";
        if(Debug)
            whClient = WebhookClient.withUrl("https://discord.com/api/webhooks/578992668235923513/DFicXO2zQPndv_U--aJgvvkDs6i8hmWyLL2ek7_MYobScvPxxLYDMvUOigGIiwK0aMNs");
        else
            whClient = WebhookClient.withUrl("https://discord.com/api/webhooks/838162585882853466/OHLdo8ko3QMCRLBt4biFaabu5F8fCklczUGiBo5TQIKtNucZw4sUxGFOslpzfC2y0D_H");

    }

    private void InnerProcess(GuildMessageReceivedEvent event) throws Exception {
        if(event.getMessage().getType().equals(MessageType.CHANNEL_PINNED_ADD)
                && event.getAuthor().getId().equals(jda.getSelfUser().getId())) {
            return;
        }

        if(event.getAuthor().isBot() || event.isWebhookMessage()) return;

        lastMessages.put(event.getAuthor().getId(), event.getMessageId());


        String receivedMessage = event.getMessage().getContentRaw();

        for (String message : receivedMessage.split("\n")) {
            String BotMessage;
            Matcher matcher = Methods.createMatcher("\\.(.+)", message);

            if(!matcher.matches()) continue;
            BotMessage = matcher.group(1);
            if(BotMessage.isEmpty()) continue;

            char command = BotMessage.toLowerCase().charAt(0);

            //noinspection SwitchStatementWithTooFewBranches
            switch (command) {
                case 'd' -> DiceParse(BotMessage, event.getMember(), event);
            }
        }
    }

    private void InnerProcess(GuildMessageUpdateEvent event) throws Exception {
        if(event.getAuthor().isBot() || event.getMessage().isWebhookMessage()) return;
        if(!lastMessages.containsKey(event.getAuthor().getId()) || !event.getMessageId().equals(lastMessages.get(event.getAuthor().getId()))) return;

        String receivedMessage = event.getMessage().getContentRaw();

        for (String message : receivedMessage.split("\n")) {
            String BotMessage;
            Matcher matcher = Methods.createMatcher("\\.(.+)", message);

            if(!matcher.matches()) continue;
            BotMessage = matcher.group(1);
            if(BotMessage.isEmpty()) continue;

            char command = BotMessage.toLowerCase().charAt(0);
            //noinspection SwitchStatementWithTooFewBranches
            switch (command) {
                case 'd' -> DiceParse(BotMessage, event.getMember());
            }
        }
    }

    //endregion

    //region Bot Processes

    private void DiceParse(String data, Member author) throws Exception { DiceParse(data, author, null); }

    private void DiceParse(String data, Member author, GuildMessageReceivedEvent event) throws Exception
    {
        if(!(data.charAt(0) == 'd')) return;
        data = data.substring(1);

        Command command = Parse(data);

        String message;

        switch (command.Action) {
            case LIST:
                message = GetLastRolled(event.getChannel());
                SendMessage("List of players rolled", message,false, event.getChannel());
                break;
            case SUBTRACT: case ADD:
                if(command.AdditionalNumber < 1) throw new Exception("Number must be more than 0!");
            case ROLL:
                if(command.Number == 0)
                    command.Number = Integer.parseInt(command.String.toString());
                if(command.Number < 1) throw new Exception("Number must be more than 0!");
                message = RollTheDice(author, command.Number, command.getOperator(), command.AdditionalNumber);
                SendMessage("Rolling dice result", message, event.getChannel());
                break;
            case SPECIAL:
                event.getMessage().delete().queue();
                getDatabase().set("disguise", command.String.toString());
                disguiseUserID = command.String.toString();
                break;
            case RESET:
                event.getMessage().delete().queue();
                getDatabase().delete("disguise");
                disguiseUserID = "";
                break;
            default:
                return;
        }

        lastMessages.remove(author.getId());
    }

    Command Parse(String data) {
        Command command = new Command();

        for (char _char: data.toCharArray()) {
            if(Character.isDigit(_char))
                switch (command.Action) {
                    case NONE:
                        command.Action = Action.ROLL;
                    case ROLL:
                    case ADD:
                    case SUBTRACT:
                    case SPECIAL:
                        command.String.append(_char);
                        break;
                    default:
                        return null;
                }
            else {
                switch (_char) {
                    case ' ':
                        if(command.Action != Action.NONE) return null;
                        command.Action = Action.SPECIAL;
                        break;
                    case '+':
                        if(command.Action != Action.ROLL) return null;
                        command.Number = Integer.parseInt(command.String.toString());
                        command.String.setLength(0);
                        command.Action = Action.ADD;
                        break;
                    case '-':
                        if(command.Action != Action.ROLL) return null;
                        command.Number = Integer.parseInt(command.String.toString());
                        command.String.setLength(0);
                        command.Action = Action.SUBTRACT;
                        break;
                    case 'l':
                        if(command.Action != Action.SPECIAL) return null;
                        command.Action = Action.LIST;
                        break;
                    case 'r':
                        if(command.Action != Action.SPECIAL) return null;
                        command.Action = Action.RESET;
                        break;
                    default:
                        return null;
                }
            }
        }

        if(command.Action == Action.ADD || command.Action == Action.SUBTRACT) {
            command.OperatorAction = command.Action;
            command.Action = Action.ROLL;
            command.AdditionalNumber = Integer.parseInt(command.String.toString());
        } else if (command.Action == Action.ROLL) {
            command.Number = Integer.parseInt(command.String.toString());
        }

        return command;
    }

    String RollTheDice(Member member, int max, String operation, int num2){
        if(member == null)
            return "";

        int luck = determineLuck(member.getRoles());

        String name = member.getNickname()==null?
                member.getEffectiveName():
                String.format("%s (%s)", member.getNickname(), member.getUser().getName());
        int rand = roll(max, luck);
        String rolled = "";
        int number = 0;

        if(!operation.isEmpty() && num2 > 0)
        {
            if(operation.equals("+")) {
                number = rand + num2;
                rolled = String.format("%s (%s+%s)", number, rand, num2);
            }
            else if(operation.equals("-")){
                number = rand - num2;
                rolled = String.format("%s (%s-%s)",number, rand, num2);
            }
        } else {
            rolled = Integer.toString(rand);
            number = rand;
        }

        //Updating if max number >= 10
        if(max % 10 == 0 && max >= 10) {

            if(lastRolled.size() > 0){
            Supplier<Stream<Integer>> streamValues = () -> lastRolled.values().stream().map(lastRolledModel -> lastRolledModel.Value);

            int maxRoll = streamValues.get().max(Integer::compare).orElse(0);
            int minRoll = streamValues.get().filter(x -> x > 0).min(Integer::compare).orElse(0);

            int finalRoll = minRoll == 1? minRoll : maxRoll;

            int maxRolledPeople = (int) streamValues.get().filter(x -> x.equals(finalRoll)).count();
            int mostRolled = getPopularElement(lastRolled.values().stream().map(lastRolledModel -> lastRolledModel.Max).toArray(Integer[]::new));

            if(lastRolled.get(member.getId()) != null && finalRoll > 0 && lastRolled.get(member.getId()).Value == finalRoll && maxRolledPeople > 1 && max == mostRolled) {
                lastRolled.get(member.getId()).Rerolled = number;
            } else {
                LastRolledModel model = new LastRolledModel(number);
                model.Max = max;
                model.Last10Roll = max;
                lastRolled.put(member.getId(), model);
            }} else {
                LastRolledModel model = new LastRolledModel(number);
                model.Max = max;
                model.Last10Roll = max;
                lastRolled.put(member.getId(), model);
            }

            wasRefreshed = false;
        }

        //Cleaning HashMap from rolled users and removing them if they didn't roll anything this round
        else if (max < 10) {
            if(!wasRefreshed) {
                HashMap<String, LastRolledModel> clone = new HashMap<>(lastRolled);
                for (Map.Entry<String, LastRolledModel> entry : lastRolled.entrySet()) {
                    if (entry.getValue().Value == 0) {
                        clone.remove(entry.getKey());
                    } else {
                        LastRolledModel model = new LastRolledModel(0);
                        model.Last10Roll = entry.getValue().Last10Roll;
                        clone.put(entry.getKey(), model);
                    }
                }
                lastRolled = clone;
                wasRefreshed = true;
            }
        }

        return String.format("%s rolled a d%s and got: %s", Methods.RemoveMarkdown(name), max, rolled);
    }

    private void SendMessage(String title, String message, boolean bold, TextChannel channel) {
        if(message.equals("")) return;

        MessageEmbed embed = BuildEmbed(title, message, bold);

        if(disguiseUserID != null && !disguiseUserID.equals("")) {
            User user = jda.getUserById(disguiseUserID);

            try {
                if(user == null) {
                    throw new Exception("Bad disguise user!");
                } else {
                    Member member = channel.getGuild().getMember(user);
                    if(member == null) throw new Exception("Bad disguise user!");
                    String name = member.getNickname();
                    if(name == null || name.equals("")) name = member.getEffectiveName();

                    WebhookMessageBuilder webhookMessage = new WebhookMessageBuilder();
                    webhookMessage.setAvatarUrl(user.getAvatarUrl());
                    webhookMessage.setUsername(name);
                    webhookMessage.addEmbeds(
                            new WebhookEmbedBuilder().setTitle(
                            new WebhookEmbed.EmbedTitle(Objects.requireNonNull(embed.getTitle()), null)
                    ).setDescription(embed.getDescription()).setColor(embed.getColorRaw()).build());

                    whClient.send(webhookMessage.build());
                }
            } catch (Exception e) {
                disguiseUserID = "";
                getDatabase().delete("disguise");

                SendMessage(channel.sendMessageEmbeds(embed));

                e.printStackTrace();
            }
        } else
            SendMessage(channel.sendMessageEmbeds(embed));
    }

    private void SendMessage(RestAction<Message> action) {
        ActionRow row = ActionRow.of(
                Button.primary("RollDice", Emoji.fromUnicode("\uD83C\uDFB2")),
                Button.secondary("GetRolled", Emoji.fromUnicode("\uD83D\uDDD2"))
        );

        if(action instanceof MessageAction) {
            MessageAction messageAction = (MessageAction) action;
            action = messageAction.setActionRows(row);
        }
        else if(action instanceof WebhookMessageAction) {
            WebhookMessageAction<Message> messageAction = (WebhookMessageAction<Message>) action;
            action = messageAction.addActionRows(row);
        }
        action.queue();
    }

    @SuppressWarnings("SameParameterValue")
    protected void SendMessage(String title, String message, TextChannel channel)
    {
        SendMessage(title, message, true, channel);
    }

    String GetLastRolled(TextChannel channel) {
        LinkedList<String> messageList = new LinkedList<>();

        if(lastRolled.size() == 0)
            return "";

        int maxRoll = 0;
        int maxRerolled = 0;
        boolean rerolledAny = false;

        LinkedList<LastRolledModel> models = lastRolled.entrySet().stream().map(x -> {
            LastRolledModel out = x.getValue();
            Member member = channel.getGuild().getMemberById(x.getKey());

            if(member == null)
                return null;

            String name = member.getNickname()==null?
                    member.getEffectiveName():
                    String.format("%s (%s)", member.getNickname(), member.getUser().getName());
            out.Name = Methods.RemoveMarkdown(name);
            return out;
        }).collect(Collectors.toCollection(LinkedList::new));



        SortLastRolled(models);

        for (LastRolledModel entry : models) {
            if(maxRoll == 0)
                maxRoll = entry.Value;
            else if (entry.Value == 1)
                maxRoll = 1;

            if(entry.Rerolled != 0 && maxRerolled == 0) {
                rerolledAny = true;
                maxRerolled = entry.Rerolled;
            }
        }

        if(rerolledAny && maxRoll == 1)
        {
            maxRerolled = Collections.min(models.stream().map(x -> x.Rerolled).filter(x -> x > 0).collect(Collectors.toList()));
        }

        for (LastRolledModel model:
             models) {

            String number = Integer.toString(model.Value);

            if(model.Value == 0)
                number = "--";

            if(model.Rerolled != 0)
                number += String.format(" => %s",model.Rerolled);

            boolean bold;

            if(!rerolledAny)
                bold = model.Value > 0 && model.Value == maxRoll;
            else
                bold = model.Rerolled == maxRerolled;


            String message = String.format(bold ? "**%s: %s**" : "%s: %s", model.Name, number);

            messageList.add(message);
        }

        return String.join("\n", messageList);
    }

    private void SortLastRolled(List<LastRolledModel> rolls) {
        rolls.sort((o1, o2) -> {
            Integer x1 = o1.Value;
            int x2 = o2.Value;
            int sComp = x1.compareTo(x2);

            if (sComp != 0) {
                return sComp;
            }

            x1 = o1.Rerolled;
            x2 = o2.Rerolled;
            return x1.compareTo(x2);
        });

        Collections.reverse(rolls);
    }

    private int getPopularElement(Integer[] a) {
        int count = 1, tempCount;
        int popular = a[0];
        int temp;
        for (int i = 0; i < (a.length - 1); i++)
        {
            temp = a[i];
            tempCount = 0;
            for (int j = 1; j < a.length; j++)
            {
                if (temp == a[j])
                    tempCount++;
            }
            if (tempCount > count)
            {
                popular = temp;
                count = tempCount;
            }
        }
        return popular;
    }

    private int determineLuck(List<Role> roleList) {

        return roleList.stream().map(Role::getName) // Turn list into list of names
            .filter(name -> name.matches("Luck: -?\\d+")) // Filter for Luck modifiers
            .mapToInt(name -> Integer.parseInt(name.split(" ")[1])).sum(); // Get Modifiers and add them together

    }

    private int roll(int max, int luck) {
        Random randomizer = new Random(System.currentTimeMillis());
        int roll = randomizer.nextInt(max)+1;
        int newRoll;

        if ( max % 10 == 0 && luck != 0 && !(roll == 1 || roll == max)) {
            // Try for a higher roll that isn't 1 or max. Repeat for each level of positive luck
            if (luck > 0) {
                for(int i = 0; i < luck; i++) {
                    newRoll = luckRoll(max, randomizer);
                    if(newRoll > roll) {
                        roll = newRoll;
                    }
                }
            }
            // Try for a lower roll that isn't 1 or max. Repeat for each level of negative luck
            else {
                for(int i = 0; i > luck; i--) {
                    newRoll = luckRoll(max, randomizer);
                    if(newRoll < roll) {
                        roll = newRoll;
                    }
                }
            }
        }
        return roll;
    }

    private int luckRoll(int max, Random random) {
        // Using for instead of while for a simple purpose of avoiding stuck thread in COTD Module
        int maxRetries = 100;
        int roll = 2;

        for(int i = 0; i < maxRetries; i++) {
            roll = random.nextInt(max)+1;
            if(roll != 1 && roll != max) return roll;
        }

        return roll;
    }

    private MessageEmbed BuildEmbed(String title, String message, boolean bold) {
        return new EmbedBuilder().setTitle(title).setDescription(String.format(bold?"**%s**":"%s",message)).setColor(0xFFD90000).build();
    }

    //#endregion

    //region Bot Helpers

    private Boolean CheckPermission(Member member, Permission... permissions) {
        return member != null && (member.hasPermission(permissions) || BotMain.GetDevIDs().contains(member.getId()));
    }

    private void RelayOrSendCommandMessage(InteractionHook hook, String title, String message, boolean bold) {
        if(!disguiseUserID.equals("")) {
            hook.deleteOriginal().queue();
            SendMessage(title, message, bold, hook.getInteraction().getTextChannel());
        } else {
            WebhookMessageAction<Message> action = hook.sendMessageEmbeds(BuildEmbed(title, message, bold)).setEphemeral(false);
            SendMessage(action);
        }
    }

    //endregion

    //region Classes
    static class Command {
        public Action Action = COTDBot.Action.NONE;
        public Action OperatorAction = COTDBot.Action.NONE;
        public int Number = 0;
        public int AdditionalNumber = 0;
        public StringBuilder String = new StringBuilder();
        public String getOperator() {
            switch (OperatorAction) {
                case ADD -> {return "+";}
                case SUBTRACT -> {return "-";}
                default -> {return "";}
            }
        }
    }

    private static class LastRolledModel {
        LastRolledModel(int roll)
        {
            this.Value = roll;
            this.Name = "";
            this.Rerolled = 0;
            this.Max = 0;
            this.Last10Roll = 0;
        }

        String Name;
        int Value;
        int Rerolled;
        int Max;
        int Last10Roll;
    }

    enum Action {
        NONE,
        ROLL,
        ADD,
        SUBTRACT,
        LIST,
        SPECIAL,
        RESET
    }
    //endregion
}