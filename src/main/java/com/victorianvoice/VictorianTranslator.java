package com.victorianvoice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class VictorianTranslator {
    private static final Map<Pattern, String> RULES = new LinkedHashMap<>();
    static {
        add("\\bhello\\b", "Good day");
        add("\\bhi\\b", "Good morrow");
        add("\\bhey\\b", "Well, I say");
        add("\\bhey guys\\b", "Good morrow, esteemed companions");
        add("\\bwhat's up\\b", "what news, pray tell");
        add("\\bwhats up\\b", "what news, pray tell");
        add("\\bhow are you\\b", "how dost thou fare");
        add("\\bthanks\\b", "many thanks, good sir");
        add("\\bthank you\\b", "I thank thee most sincerely");
        add("\\bplease\\b", "if you would be so kind");
        add("\\byeah\\b", "indeed");
        add("\\byes\\b", "indeed");
        add("\\bno\\b", "most certainly not");
        add("\\bokay\\b", "very well");
        add("\\bok\\b", "very well");
        add("\\bbye\\b", "fare thee well");
        add("\\bgoodbye\\b", "I bid thee farewell");
        add("\\bfriend\\b", "esteemed companion");
        add("\\bmy bad\\b", "I must offer my sincerest apologies");
        add("\\bsorry\\b", "I beg thy pardon");
        add("\\bwait\\b", "pray, hold a moment");
        add("\\bcome here\\b", "pray, approach");
        add("\\blook\\b", "behold");
        add("\\bamazing\\b", "positively marvellous");
        add("\\bcool\\b", "most splendid");
        add("\\bawesome\\b", "absolutely magnificent");
        add("\\bhelp me\\b", "I beseech thee, lend me assistance");
        add("\\bI don't know\\b", "I know not");
        add("\\bi don't know\\b", "I know not");
        add("\\bI think\\b", "I daresay");
        add("\\bI\\b", "I");
        add("\\byou\\b", "thou");
        add("\\byour\\b", "thy");
        add("\\byou're\\b", "thou art");
        add("\\byou are\\b", "thou art");
        add("\\bdon't\\b", "dost not");
        add("\\bcan't\\b", "cannot");
        add("\\bwant\\b", "desire");
        add("\\bvery\\b", "exceedingly");
        add("\\bthe best\\b", "the finest");
        add("\\bLOL\\b", "I daresay, how droll");
    }

    private static void add(String regex, String replacement) {
        RULES.put(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), replacement);
    }

    public static String translate(String input) {
        String result = input;
        for (var rule : RULES.entrySet()) result = rule.getKey().matcher(result).replaceAll(rule.getValue());
        if (!result.isEmpty()) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
            if (!result.matches(".*[.!?]$")) result += ", what ho!";
        }
        return result;
    }
}
