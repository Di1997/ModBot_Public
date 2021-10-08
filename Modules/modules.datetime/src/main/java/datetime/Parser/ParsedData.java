package datetime.Parser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class ParsedData {
    public LinkedHashMap<String, HashSet<int[]>> MatchingStrings = new LinkedHashMap<>();
    public LinkedHashSet<String> GroupStrings = new LinkedHashSet<>();
    public LinkedHashSet<String> SplitStrings = new LinkedHashSet<>();
    public String OriginalString;
    public String NewString;
}
