package COTDBot.Methods;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Methods {
    public static Matcher createMatcher(String regex, String in){return Pattern.compile(regex).matcher(in);}
    public static String RemoveMarkdown(String string)
    {
        final String regex = "([*_~`|\\\\])";
        final String subst = "\\\\$1";
        final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(string);
        return matcher.replaceAll(subst);
    }
}
