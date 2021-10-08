package datetime.Parser;

import java.util.HashMap;
import java.util.HashSet;

public class Parser {
    public char openingChar = '{';
    public char closingChar = '}';
    public Character splittingChar = null;

    char screeningChar = '\\';
    boolean isScreening = false;
    boolean opened;

    public ParsedData Process(String string) {
        return Process(string, new HashMap<>());
    }

    public ParsedData Process(String string, HashMap<String, String> replaceMap) {
        ParsedData data = new ParsedData();
        data.OriginalString = string;
        int i = 0;
        StringBuilder strData = new StringBuilder();
        StringBuilder newString = new StringBuilder();
        StringBuilder splitString = new StringBuilder();
        int[] dataIndexes = new int[2];
        boolean replace = replaceMap.size() > 0;
        int diff = 0;


        for (char c: string.toCharArray()) {
            if(isScreening) {
                isScreening = false;
                if(replace) newString.append(c);
                continue;
            }

            if(c == screeningChar) {
                isScreening = true;
                continue;
            }
            if(replace) newString.append(c);

            if(!opened && splittingChar != null) {
                if(c == splittingChar) {
                    data.SplitStrings.add(splitString.toString());
                    splitString = new StringBuilder();
                } else {
                    splitString.append(c);
                }
            }

            if(!opened && c == openingChar) {
                opened = true;
                strData.append(c);
                dataIndexes[0] = i;
            } else if (opened) {
                if(c == closingChar) {
                    opened = false;
                    strData.append(c);
                    dataIndexes[1] = i;
                    String builtString = strData.toString();

                    if(replace) {
                        if(replaceMap.containsKey(builtString)) {
                            String replacement = replaceMap.get(builtString);
                            int startIdx = dataIndexes[0] - diff;
                            int endIdx = dataIndexes[1] - diff;
                            newString.replace(startIdx, endIdx + 1, replacement);

                            diff += builtString.length() - replacement.length();
                        }
                    }

                    strData = new StringBuilder();

                    if(!data.MatchingStrings.containsKey(builtString)) data.MatchingStrings.put(builtString, new HashSet<>());
                    data.GroupStrings.add(builtString.substring(1, builtString.length()-1));

                    data.MatchingStrings.get(builtString).add(dataIndexes);
                    dataIndexes = new int[2];
                } else {
                    strData.append(c);
                }
            }

            i++;
        }

        if(opened) throw new IllegalArgumentException("Couldn't close bracket group!");

        if(replace) data.NewString = newString.toString();
        return data;
    }
}
