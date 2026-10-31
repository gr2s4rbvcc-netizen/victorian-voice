package com.victorianvoice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based chat translator. It rewrites slang, contractions and common phrases into
 * Victorian-style English in a single pass (so replacements never get re-translated),
 * then occasionally frames a sentence with a period-appropriate opener or closer.
 */
public final class VictorianTranslator {
    private static final int MAX_CHAT = 256;
    private static final double OPENER_CHANCE = 0.35;
    private static final double CLOSER_CHANCE = 0.20;
    private static final Random RNG = new Random();
    private static final Map<String, String[]> DICT = new HashMap<>();
    private static final Pattern WORDS;
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern PUNCT_TAIL = Pattern.compile("^(.*?)([.!?]+)?$", Pattern.DOTALL);
    private static final Pattern COORDS = Pattern.compile("-?\\d+\\s+-?\\d+\\s+-?\\d+");
    private static String lastFrame = "";

    private static final String[] QUESTION_OPEN = {"I say, ", "Pray tell, ", "If I may be so bold, ", "Forgive my curiosity, but "};
    private static final String[] EXCLAIM_OPEN = {"Good heavens, ", "Upon my word, ", "By Jove, ", "Great Scott, "};
    private static final String[] STATEMENT_OPEN = {"I dare say, ", "I must observe that ", "Quite frankly, ", "It strikes me that ", "Allow me to remark that "};
    private static final String[] GENERIC_OPEN = {"I say, ", "Now then, ", "Come now, "};
    private static final String[] STATEMENT_CLOSE = {", I dare say", ", if I may say so", ", my good fellow"};
    private static final String[] QUESTION_CLOSE = {", if you please", ", pray"};
    private static final String[] EXCLAIM_CLOSE = {", upon my word", ", I say"};

    private static final Set<String> SAFE_LOWER = new HashSet<>(Arrays.asList(
        "the", "a", "an", "this", "that", "these", "those", "it", "there", "we", "you", "they", "he", "she",
        "what", "who", "whom", "whose", "how", "why", "when", "where", "which", "is", "are", "was", "were",
        "do", "does", "did", "can", "could", "would", "will", "shall", "should", "have", "has", "had", "may",
        "might", "must", "my", "your", "our", "their", "his", "her", "its", "let", "if", "but", "and", "so",
        "then", "just", "also", "not", "some", "any", "all", "everyone", "everybody", "nobody", "nothing",
        "something", "anything", "everything", "please", "kindly", "pray", "wait", "go", "come", "stop"));

    private static final Set<String> SUBJECT_WORDS = new HashSet<>(Arrays.asList(
        "it", "there", "we", "they", "he", "she", "this", "that", "the", "a", "an", "my", "i", "you", "these",
        "those", "everyone", "everybody", "nobody", "our", "your", "his", "her", "its", "someone", "nothing", "something"));

    private static final String[] INTERJECTIONS = {
        "good ", "indeed", "pray", "i say", "upon my", "by jove", "great scott", "alas", "huzzah", "hurrah",
        "dear me", "ha ha", "well met", "quite so", "just so", "certainly", "by no means", "i think not",
        "much obliged", "farewell", "how do you do", "my apologies", "i do beg", "well played", "well done",
        "think nothing", "i confess", "i thank", "a very good"};

    private static void d(String keys, String... variants) {
        for (String k : keys.split("\\|")) DICT.put(k, variants);
    }

    static {
        // Greetings and farewells
        d("hello|hi|hey|heya|howdy|yo", "good day~", "good day to you~", "how do you do~");
        d("hey guys|hi guys|hello guys|hey everyone|hi everyone|hello everyone|hey all|hi all",
            "good day, ladies and gentlemen~", "good day to you all, esteemed companions~");
        d("what's up|whats up|wassup|sup|wazzup", "what news have you", "how goes the day");
        d("how are you|how are u|how r u|hru", "how do you do~", "I trust you are well~");
        d("how's it going|hows it going", "how goes it with you", "how fares the day");
        d("good morning|gm", "a very good morning to you~");
        d("good night|goodnight|gn", "good night and sweet dreams", "I bid you a restful night");
        d("bye|goodbye|bai|cya|see ya later|see you later|gtg|g2g", "farewell~", "good day to you~",
            "until we meet again~", "I must take my leave~");
        // Thanks, apologies, politeness
        d("thanks|thx|thanx|tysm|ty", "much obliged~", "I am most grateful~", "my sincerest thanks~");
        d("thanks for|thx for|ty for", "much obliged for", "I am grateful for");
        d("thank you for", "I thank you kindly for", "I am most grateful for");
        d("thank you|thank u", "I thank you most kindly", "you have my deepest gratitude");
        d("thanks a lot|thank you so much|thanks so much", "I am exceedingly obliged to you", "I cannot thank you enough");
        d("please|pls|plz", "kindly");
        d("sorry|sry", "my apologies~", "I do beg your pardon~", "my humblest apologies~");
        d("sorry for", "I do apologise for");
        d("sorry about", "my apologies concerning");
        d("my bad", "the fault is entirely mine", "I do beg your pardon");
        d("np|no problem|no prob", "think nothing of it~", "it is no trouble at all~");
        d("congrats|congratulations|grats", "my heartiest congratulations~", "well done indeed~");
        d("nice one|good one", "well done indeed", "a capital effort");
        // Answers
        d("yes|yeah|yep|yup|yea", "indeed~", "quite so~", "just so~", "certainly~");
        d("nope|nah|no", "I think not~", "certainly not~", "by no means~");
        d("ok|okay|k|kk|alright|aight", "very well~", "quite all right~", "splendid~");
        // Reactions
        d("lol|lmao|lmfao|rofl|haha|hahaha|hehe|lul|xd", "ha ha, how droll~", "I confess that amuses me~", "upon my word, how diverting~");
        d("omg|omfg|oh my god", "good heavens~", "good gracious~", "great Scott~");
        d("wtf|wth|what the heck|what the hell", "what in heaven's name~");
        d("wow|woah|whoa", "good heavens~", "I say~", "great Scott~");
        d("ugh|ughh|bruh", "dear me~", "how tiresome~");
        d("oof|rip", "alas~", "what a pity~");
        d("yay|yey|woo|woohoo", "huzzah~", "hurrah~");
        d("gg|ggs", "well played~", "capital sport~");
        d("heck", "heavens");
        d("damn|dammit|damnit|crap|shit", "blast~", "confound it~");
        // Chat shorthand
        d("brb", "I shall return momentarily");
        d("afk", "I shall be away from my post");
        d("wb", "welcome back");
        d("gl|good luck", "the best of fortune", "good fortune attend you");
        d("hf", "enjoy yourself");
        d("idk|i don't know|i dont know|i do not know", "I confess I know not", "I haven't the faintest notion");
        d("idc|i don't care|i dont care", "I care not", "it is a matter of indifference to me");
        d("idk man|idk bro|idk dude", "I confess I know not, old chap");
        d("ngl", "I confess~");
        d("tbh", "to speak frankly");
        d("imo|imho", "in my humble opinion");
        d("btw", "by the by");
        d("fyi", "for your information");
        d("rn", "at present");
        d("cuz|bc|cus", "because");
        d("tho", "though");
        d("u", "you");
        d("ur", "your");
        d("y'all|yall", "all of you");
        // Opinions and wants
        d("i think|i thought", "I daresay", "I dare say", "I fancy", "it seems to me");
        d("i guess|i suppose", "I suppose", "I daresay");
        d("maybe|perhaps", "perhaps", "mayhap", "it may be that");
        d("probably", "in all likelihood", "most probably");
        d("definitely|totally|absolutely", "most certainly", "without a doubt", "positively");
        d("i want", "I should like", "I do desire");
        d("i need", "I find myself in need of", "I require");
        d("i love", "I am exceedingly fond of", "I do adore");
        d("i like", "I am rather partial to", "I am quite fond of");
        d("i hate", "I abhor", "I cannot abide");
        d("wanna", "wish to");
        d("gonna", "going to");
        d("gotta", "must");
        d("kinda|kind of|sorta|sort of", "rather", "somewhat");
        d("let's go|lets go", "let us away", "let us be off");
        d("let's", "let us");
        d("hurry up", "make haste");
        d("come on", "come now", "come, come");
        d("right now", "this very instant", "at once");
        d("wait for", "tarry for");
        d("wait a sec|wait a second|wait a minute|one sec|one second|hold on", "pray allow me a moment~", "one moment, if you please~");
        d("wait", "pray wait", "one moment~");
        d("look at", "observe", "behold");
        // People
        d("guys", "good people", "gentlemen");
        d("guy", "fellow", "gentleman");
        d("girl", "young lady");
        d("girls", "young ladies");
        d("boy", "young gentleman");
        d("boys", "young gentlemen");
        d("kid", "youngster");
        d("kids", "youngsters");
        d("dude|bro|buddy|pal|fam", "old chap", "my good fellow", "dear fellow");
        d("friend", "dear friend", "good friend");
        d("friends", "dear friends", "good friends");
        d("mom|mum|mommy", "dear mother");
        d("dad|daddy", "dear father");
        d("noob", "novice", "greenhorn");
        d("idiot|moron", "simpleton", "dunderhead", "blockhead");
        // Describing things
        d("very", "most", "exceedingly", "remarkably");
        d("really", "truly", "positively", "most");
        d("the best", "the finest", "the very best");
        d("awesome|amazing|epic|sick|insane", "splendid", "capital", "marvellous");
        d("cool|nice|sweet|dope|lit", "splendid", "capital", "first-rate");
        d("great", "splendid", "marvellous", "first-rate");
        d("bad|terrible|awful|horrible|trash", "dreadful", "frightful", "ghastly");
        d("annoying", "vexing", "tiresome");
        d("boring", "tedious", "dreadfully dull");
        d("funny", "diverting", "droll");
        d("weird|strange", "peculiar", "singular", "most irregular");
        d("scary", "alarming", "most frightening");
        d("stupid|dumb|idiotic", "foolish", "absurd");
        d("crazy", "quite mad", "positively unhinged");
        d("hungry", "famished", "positively ravenous");
        d("tired", "rather fatigued", "quite exhausted");
        d("food", "victuals", "provisions");
        d("money|cash", "coin", "funds");
        d("sleep", "slumber");
        // Contractions
        d("don't|dont", "do not");
        d("doesn't|doesnt", "does not");
        d("didn't|didnt", "did not");
        d("can't|cant", "cannot");
        d("won't|wont", "shall not");
        d("isn't", "is not");
        d("aren't", "are not");
        d("wasn't", "was not");
        d("weren't", "were not");
        d("couldn't", "could not");
        d("wouldn't", "would not");
        d("shouldn't", "should not");
        d("haven't", "have not");
        d("hasn't", "has not");
        d("hadn't", "had not");
        d("ain't", "is not");
        d("it's", "it is");
        d("that's", "that is");
        d("there's", "there is");
        d("what's", "what is");
        d("who's", "who is");
        d("he's", "he is");
        d("she's", "she is");
        d("here's", "here is");
        d("i'm|im", "I am");
        d("you're|youre", "you are");
        d("we're", "we are");
        d("they're", "they are");
        d("i'll", "I shall");
        d("you'll", "you will");
        d("we'll", "we shall");
        d("they'll", "they will");
        d("i've", "I have");
        d("you've", "you have");
        d("we've", "we have");
        d("i'd", "I should");
        d("i", "I");

        List<String> keys = new ArrayList<>(DICT.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder alt = new StringBuilder();
        for (String k : keys) {
            if (alt.length() > 0) alt.append('|');
            String[] parts = k.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) alt.append("\\s+");
                alt.append(Pattern.quote(parts[i]));
            }
        }
        WORDS = Pattern.compile("(?<![\\w'])(?:" + alt + ")(?![\\w'])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private VictorianTranslator() {}

    public static String translate(String input) {
        return translate(input, RNG);
    }

    static String translate(String input, Random rng) {
        if (input == null) return "";
        String text = input.strip().replace('\u2019', '\'');
        if (text.isEmpty() || text.contains("://") || COORDS.matcher(text).find()) return input;
        String result = build(text, rng, true);
        if (result.length() > MAX_CHAT) result = build(text, rng, false);
        if (result.length() > MAX_CHAT) return input;
        return result;
    }

    private static String build(String text, Random rng, boolean frame) {
        String replaced = replaceWords(text, rng);
        StringBuilder out = new StringBuilder();
        for (String sentence : SENTENCE_SPLIT.split(replaced)) {
            if (sentence.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(frame ? decorate(sentence.strip(), rng) : capitalize(sentence.strip()));
        }
        return out.toString();
    }

    private static String replaceWords(String text, Random rng) {
        Matcher m = WORDS.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String found = m.group();
            String key = found.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            String[] variants = DICT.get(key);
            String rep = found;
            if (variants != null && !(key.equals("no") && !standalone(text, m.end()))) {
                rep = variants[rng.nextInt(variants.length)];
                if (rep.endsWith("~")) {
                    rep = rep.substring(0, rep.length() - 1);
                    if (text.substring(m.end()).matches("(?s)\\s*[\\w'\"(].*")) rep = rep + ",";
                }
                if (found.length() > 1 && found.equals(found.toUpperCase(Locale.ROOT)) && !found.equals(found.toLowerCase(Locale.ROOT))) {
                    rep = rep.toUpperCase(Locale.ROOT);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** "no" only counts as an answer when it ends a clause, so "no one" and "no problem" are left alone. */
    private static boolean standalone(String text, int end) {
        return text.substring(end).matches("(?s)\\s*([.,!?].*)?");
    }

    private static String decorate(String sentence, Random rng) {
        Matcher pm = PUNCT_TAIL.matcher(sentence);
        if (!pm.matches()) return capitalize(sentence);
        String body = pm.group(1).strip();
        String punct = pm.group(2) == null ? "" : pm.group(2);
        String[] words = body.split("\\s+");
        if (words.length < 3 || startsWithInterjection(body)) return capitalize(sentence);

        char end = punct.isEmpty() ? '.' : punct.charAt(punct.length() - 1);
        double roll = rng.nextDouble();
        if (roll < OPENER_CHANCE) {
            String[] pool;
            if (end == '?') pool = QUESTION_OPEN;
            else if (end == '!') pool = EXCLAIM_OPEN;
            else pool = SUBJECT_WORDS.contains(words[0].toLowerCase(Locale.ROOT)) ? STATEMENT_OPEN : GENERIC_OPEN;
            String opener = pick(pool, rng);
            return opener + lowerFirst(body, words[0]) + punct;
        }
        if (roll < OPENER_CHANCE + CLOSER_CHANCE) {
            String[] pool = end == '?' ? QUESTION_CLOSE : end == '!' ? EXCLAIM_CLOSE : STATEMENT_CLOSE;
            String closer = pick(pool, rng);
            return capitalize(body) + closer + (punct.isEmpty() ? "." : punct);
        }
        return capitalize(sentence);
    }

    private static String pick(String[] pool, Random rng) {
        String choice = pool[rng.nextInt(pool.length)];
        for (int i = 0; i < 5 && choice.equals(lastFrame); i++) choice = pool[rng.nextInt(pool.length)];
        lastFrame = choice;
        return choice;
    }

    private static boolean startsWithInterjection(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        for (String s : INTERJECTIONS) if (lower.startsWith(s)) return true;
        return false;
    }

    private static String lowerFirst(String body, String firstWord) {
        if (SAFE_LOWER.contains(firstWord.toLowerCase(Locale.ROOT)) && !firstWord.equals("I")) {
            return Character.toLowerCase(body.charAt(0)) + body.substring(1);
        }
        return body;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
