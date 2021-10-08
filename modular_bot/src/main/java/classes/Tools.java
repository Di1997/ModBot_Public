package classes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Tools {
    public static String convertToJson(Object obj) {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(obj);
    }

    @CheckForNull
    public static <T> T convertToClass(String str, TypeToken<T> type) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(str, type.getType());
    }

    public static void SendMessage(GuildMessageReceivedEvent event, String message, String title) {
        if(message.length() + title.length() < 6000 && message.length() < 2000) {
            EmbedBuilder builder = new EmbedBuilder()
                .setColor(event.getGuild().getSelfMember().getColor())
                .setTitle(title)
                .setDescription(String.format("**%s**", SterilizeMessage(message)));

            event.getChannel().sendMessageEmbeds(builder.build()).queue();
        }
    }

    public static void SendMessage(GuildMessageReceivedEvent event, Page<String> page, String title) {
        String message = String.join(System.lineSeparator(), page.GetCurrentPage());
        String pageInfo = String.format("Page %s/%s", page.index+1, page.GetMaxIndex()+1);

        if(message.length() + title.length() < 6000 && message.length() < 2000) {
            EmbedBuilder builder = new EmbedBuilder()
                .setColor(event.getGuild().getSelfMember().getColor())
                .setTitle(title)
                .setDescription(String.format("**%s**", SterilizeMessage(message)))
                .setFooter(pageInfo);

            event.getChannel().sendMessageEmbeds(builder.build()).queue();
        }
    }

    public static void SendMessage(GuildMessageReceivedEvent event, Page<String> page, EmbedBuilder embed) {
        String message = String.join(System.lineSeparator(), page.GetCurrentPage());
        String pageInfo = String.format("Page %s/%s", page.index+1, page.GetMaxIndex()+1);

        if(message.length() < 2000) {
            EmbedBuilder builder = new EmbedBuilder(embed)
                    .setDescription(String.format("**%s**", SterilizeMessage(message)));

            if(builder.build().getFooter() == null) builder.setFooter(pageInfo);

            event.getChannel().sendMessageEmbeds(builder.build()).queue();
        }
    }

    public static EmbedBuilder GetEmbedTemplate(GuildMessageReceivedEvent event, Page<?> page) {
        String pageInfo = String.format("Page %s/%s", page.index+1, page.GetMaxIndex()+1);
        return new EmbedBuilder()
                .setColor(event.getGuild().getSelfMember().getColor())
                .setFooter(pageInfo);
    }

    public static void SendMessage(GuildMessageReceivedEvent event, String message, EmbedBuilder embed) {
        if(message.length() < 2000) {
            EmbedBuilder builder = new EmbedBuilder(embed)
                    .setDescription(String.format("**%s**", SterilizeMessage(message)));

            event.getChannel().sendMessageEmbeds(builder.build()).queue();
        }
    }

    private static String SterilizeMessage(String message) {
        return message.replaceAll("([*_~`>|:<@])", "\\\\$1");
    }

    public static class Page<T> {
        private List<T> data;
        private int index;
        private int size;

        public Page(List<T> data, int size) {
            this.data = data;
            this.index = 0;
            this.size = size;
        }

        public List<T> GetCurrentPage() {
            int start = index * size;
            int end = Math.min((index + 1) * size, data.size());

            if(index > GetMaxIndex()) {
                return null;
            }

            return data.subList(start, end);
        }

        public List<T> GetPageByIndex(int index) {
            int start = index * size;
            int end = Math.min((index + 1) * size, data.size());

            if(index > GetMaxIndex() || index < 0) {
                return Collections.emptyList();
            }

            return data.subList(start, end);
        }

        public int GetMaxIndex() {
            if(data.size() == 0) return -1;
            else if(data.size() < size) return 0;
            else {
                int index = data.size() / size;
                if(data.size()%size == 0) index--;
                return index;
            }
        }

        public void NextPage() {
            index = Math.min(index + 1, GetMaxIndex());
        }

        public void PreviousPage() {
            index = Math.max(index - 1, 0);
        }

        public void SetIndex(int idx) {
            index = Math.max(Math.min(idx, GetMaxIndex()), 0);
        }
    }
}