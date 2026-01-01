/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.poetry;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.ResourceLoader;

/**
 * RhymeDatabase - Comprehensive rhyme and synonym database for poetry.
 */
public final class RhymeDatabase {
    private RhymeDatabase() {}
    
    // Static rhyme/synonym groups (small, fixed data - ~10KB total)
    private static Map<String, List<String>> rhymeGroups;
    private static Map<String, List<String>> synonymGroups;
    
    // Java fallback dictionary - only loaded when native unavailable
    // This is expensive (~20MB+) so avoid loading when possible
    private static Map<String, List<String>> dictRhymesByKey;
    private static boolean dictionaryLoaded = false;
    private static boolean nativeAvailable = false;
    private static boolean nativeChecked = false;
    
    private static boolean isNativeAvailable() {
        if (!nativeChecked) {
            nativeChecked = true;
            nativeAvailable = NativeAccess.dictionaryReady();
        }
        return nativeAvailable;
    }
    
    public static List<String> getRhymesFor(String word) {
        if (word == null) return Collections.emptyList();
        
        // Try native rhyme engine first (fastest, no memory overhead)
        List<String> nativeRhymes = NativeAccess.rhymeFind(word, 20);
        if (nativeRhymes != null && !nativeRhymes.isEmpty()) {
            return nativeRhymes.size() > 10 ? nativeRhymes.subList(0, 10) : nativeRhymes;
        }
        
        // Try native dictionary rhymes
        List<String> dictRhymes = NativeAccess.dictionaryRhymes(word, 64);
        if (dictRhymes != null && !dictRhymes.isEmpty()) {
            List<String> filtered = new ArrayList<>();
            for (String w : dictRhymes) {
                if (!w.equalsIgnoreCase(word)) filtered.add(w);
            }
            return filtered.size() > 10 ? filtered.subList(0, 10) : filtered;
        }
        
        // If native is available but returned nothing, use built-in rhyme groups only
        // Avoid loading expensive Java dictionary
        if (isNativeAvailable()) {
            return getBuiltInRhymes(word);
        }
        
        // Fallback: use built-in groups + Java dictionary (expensive)
        String key = PoetryUtils.rhymeKey(word);
        if (key == null) return Collections.emptyList();
        
        List<String> result = getBuiltInRhymes(word);
        
        // Only load Java dictionary if native is truly unavailable
        ensureDictionaryLoaded();
        if (dictRhymesByKey != null) {
            List<String> fallback = dictRhymesByKey.get(key);
            if (fallback != null) {
                for (String w : fallback) {
                    if (!w.equalsIgnoreCase(word) && !result.contains(w)) {
                        result.add(w);
                    }
                }
            }
        }
        return result.size() > 10 ? result.subList(0, 10) : result;
    }
    
    private static List<String> getBuiltInRhymes(String word) {
        String key = PoetryUtils.rhymeKey(word);
        if (key == null) return new ArrayList<>();
        
        initRhymes();
        List<String> result = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> e : rhymeGroups.entrySet()) {
            if (key.endsWith(e.getKey()) || e.getKey().endsWith(key)) {
                for (String w : e.getValue()) {
                    if (!w.equalsIgnoreCase(word) && !result.contains(w)) {
                        result.add(w);
                    }
                }
            }
        }
        return result;
    }
    
    public static List<String> getSynonymsFor(String word) {
        if (word == null) return Collections.emptyList();
        String lower = word.toLowerCase(Locale.ROOT);
        
        initSynonyms();
        List<String> merged = new ArrayList<>();
        List<String> dict = PoetryDictionary.getSynonyms(lower);
        if (dict != null) merged.addAll(dict);
        List<String> direct = synonymGroups.get(lower);
        if (direct != null) {
            for (String s : direct) {
                if (!merged.contains(s)) merged.add(s);
            }
        }
        return merged;
    }
    
    private static synchronized void initRhymes() {
        if (rhymeGroups != null) return;
        rhymeGroups = new HashMap<>();
        
        // Common rhyme endings
        rhymeGroups.put("ight", Arrays.asList("light","night","bright","sight","flight","might","right","tight","white","write","fright","plight","slight","knight","height"));
        rhymeGroups.put("ay", Arrays.asList("day","way","say","play","stay","may","ray","gray","pray","away","today","display","delay","decay","betray","portray"));
        rhymeGroups.put("ow", Arrays.asList("glow","flow","show","know","grow","slow","snow","throw","blow","below","shadow","window","follow","sorrow","tomorrow","borrow"));
        rhymeGroups.put("ing", Arrays.asList("sing","ring","bring","thing","spring","string","wing","king","swing","cling","fling","sting"));
        rhymeGroups.put("ove", Arrays.asList("love","dove","above","shove","glove"));
        rhymeGroups.put("eam", Arrays.asList("dream","stream","beam","team","cream","gleam","scream","seem","theme","scheme"));
        rhymeGroups.put("eart", Arrays.asList("heart","part","start","art","apart","chart","smart","cart","dart"));
        rhymeGroups.put("ound", Arrays.asList("sound","ground","found","round","bound","profound","around","surround","astound","wound"));
        rhymeGroups.put("ain", Arrays.asList("rain","pain","gain","train","brain","chain","plain","strain","domain","remain","contain","sustain","explain","refrain","maintain"));
        rhymeGroups.put("ine", Arrays.asList("line","mine","shine","fine","vine","wine","divine","define","combine","decline","design","confine","incline","recline","refine"));
        rhymeGroups.put("ore", Arrays.asList("more","shore","store","core","door","floor","before","explore","ignore","restore","adore","deplore","implore"));
        rhymeGroups.put("ire", Arrays.asList("fire","desire","inspire","admire","acquire","retire","entire","expire","conspire","require"));
        rhymeGroups.put("ear", Arrays.asList("hear","dear","fear","near","clear","year","tear","appear","disappear","sincere","severe","persevere"));
        rhymeGroups.put("eak", Arrays.asList("speak","weak","peak","seek","creek","cheek","bleak","sleek","unique","antique","critique"));
        rhymeGroups.put("ace", Arrays.asList("face","place","space","grace","trace","race","embrace","replace","disgrace","interface"));
        rhymeGroups.put("ue", Arrays.asList("blue","true","new","few","view","due","pursue","continue","rescue","value","avenue"));
        rhymeGroups.put("oon", Arrays.asList("moon","soon","tune","noon","boon","swoon","balloon","cartoon","monsoon","afternoon","honeymoon"));
        rhymeGroups.put("ide", Arrays.asList("side","hide","wide","ride","guide","pride","slide","stride","bride","divide","provide","decide","inside","outside","beside","confide"));
        rhymeGroups.put("eed", Arrays.asList("need","seed","feed","lead","read","speed","breed","greed","exceed","succeed","proceed","indeed"));
        rhymeGroups.put("eep", Arrays.asList("deep","sleep","keep","weep","creep","steep","sweep","asleep"));
        rhymeGroups.put("old", Arrays.asList("old","gold","hold","cold","bold","told","fold","sold","behold","unfold","withhold"));
        rhymeGroups.put("all", Arrays.asList("all","call","fall","tall","wall","small","ball","hall","recall","install","overall","waterfall"));
        rhymeGroups.put("ess", Arrays.asList("less","bless","dress","press","stress","guess","confess","express","progress","success","possess","address","impress","compress","distress"));
        rhymeGroups.put("ust", Arrays.asList("just","must","trust","dust","rust","adjust","disgust","robust"));
        rhymeGroups.put("ong", Arrays.asList("long","song","strong","wrong","along","belong","among"));
        rhymeGroups.put("ive", Arrays.asList("live","give","forgive","relive","outlive"));
        rhymeGroups.put("ife", Arrays.asList("life","wife","knife","strife"));
        rhymeGroups.put("ose", Arrays.asList("rose","close","chose","those","compose","suppose","propose","disclose","expose","impose"));
        rhymeGroups.put("oom", Arrays.asList("room","bloom","gloom","doom","zoom","tomb","womb","assume","consume","resume","perfume"));
        rhymeGroups.put("und", Arrays.asList("and","hand","land","stand","band","sand","brand","grand","demand","expand","command","understand"));
    }
    
    private static synchronized void initSynonyms() {
        if (synonymGroups != null) return;
        synonymGroups = new HashMap<>();
        
        // Common poetic words and their synonyms
        synonymGroups.put("love", Arrays.asList("affection","devotion","passion","adoration","fondness","ardor","tenderness","warmth"));
        synonymGroups.put("dark", Arrays.asList("dim","shadowy","murky","gloomy","dusky","obscure","tenebrous","somber"));
        synonymGroups.put("light", Arrays.asList("glow","gleam","radiance","luminescence","shine","brilliance","luster","illumination"));
        synonymGroups.put("night", Arrays.asList("darkness","evening","dusk","twilight","midnight","nightfall"));
        synonymGroups.put("day", Arrays.asList("daylight","morning","dawn","sunrise","daytime","noon"));
        synonymGroups.put("beautiful", Arrays.asList("lovely","gorgeous","stunning","exquisite","radiant","magnificent","splendid","breathtaking"));
        synonymGroups.put("sad", Arrays.asList("sorrowful","melancholy","mournful","forlorn","dejected","woeful","despondent","gloomy"));
        synonymGroups.put("happy", Arrays.asList("joyful","blissful","elated","delighted","cheerful","jubilant","content","gleeful"));
        synonymGroups.put("heart", Arrays.asList("soul","spirit","core","essence","center"));
        synonymGroups.put("dream", Arrays.asList("vision","fantasy","reverie","aspiration","hope","illusion"));
        synonymGroups.put("river", Arrays.asList("stream","brook","creek","current","flow","waters"));
        synonymGroups.put("sky", Arrays.asList("heavens","firmament","expanse","atmosphere","azure","ether"));
        synonymGroups.put("tree", Arrays.asList("oak","willow","pine","birch","maple","elm"));
        synonymGroups.put("flower", Arrays.asList("bloom","blossom","petal","rose","lily","bud"));
        synonymGroups.put("wind", Arrays.asList("breeze","gust","gale","zephyr","draft","tempest"));
        synonymGroups.put("rain", Arrays.asList("shower","downpour","drizzle","storm","deluge","precipitation"));
        synonymGroups.put("sun", Arrays.asList("sol","daystar","sunlight","sunshine","rays","warmth"));
        synonymGroups.put("moon", Arrays.asList("luna","crescent","lunar","moonlight","orb"));
        synonymGroups.put("star", Arrays.asList("celestial","constellation","luminary","twinkle","sparkle"));
        synonymGroups.put("sea", Arrays.asList("ocean","waves","waters","deep","marine","tide"));
        synonymGroups.put("mountain", Arrays.asList("peak","summit","hill","ridge","cliff","highland"));
        synonymGroups.put("time", Arrays.asList("moment","era","age","epoch","eternity","instant"));
        synonymGroups.put("death", Arrays.asList("end","demise","passing","departure","mortality","eternal rest"));
        synonymGroups.put("life", Arrays.asList("existence","being","living","vitality","breath","spirit"));
        synonymGroups.put("hope", Arrays.asList("aspiration","wish","desire","optimism","faith","expectation"));
        synonymGroups.put("fear", Arrays.asList("dread","terror","fright","anxiety","alarm","trepidation"));
        synonymGroups.put("silence", Arrays.asList("quiet","stillness","hush","calm","peace","tranquility"));
        synonymGroups.put("voice", Arrays.asList("speech","tone","sound","utterance","expression"));
        synonymGroups.put("eye", Arrays.asList("gaze","sight","vision","glance","look"));
        synonymGroups.put("hand", Arrays.asList("palm","grasp","touch","grip","fingers"));
        synonymGroups.put("soul", Arrays.asList("spirit","essence","heart","psyche","inner self"));
        synonymGroups.put("mind", Arrays.asList("thought","intellect","reason","consciousness","psyche"));
        synonymGroups.put("walk", Arrays.asList("stroll","wander","roam","traverse","journey","tread"));
        synonymGroups.put("run", Arrays.asList("dash","sprint","race","flee","rush","hasten"));
        synonymGroups.put("see", Arrays.asList("behold","witness","observe","perceive","view","gaze"));
        synonymGroups.put("say", Arrays.asList("speak","utter","declare","proclaim","whisper","murmur"));
        synonymGroups.put("think", Arrays.asList("ponder","contemplate","reflect","consider","muse","meditate"));
        synonymGroups.put("feel", Arrays.asList("sense","perceive","experience","touch","emotion"));
        synonymGroups.put("old", Arrays.asList("ancient","aged","timeless","eternal","vintage","antique"));
        synonymGroups.put("young", Arrays.asList("youthful","fresh","new","juvenile","tender"));
        synonymGroups.put("big", Arrays.asList("vast","immense","enormous","grand","mighty","colossal"));
        synonymGroups.put("small", Arrays.asList("tiny","minute","little","petite","modest","humble"));
        synonymGroups.put("fast", Arrays.asList("swift","rapid","quick","speedy","fleet","hasty"));
        synonymGroups.put("slow", Arrays.asList("gradual","leisurely","unhurried","measured","deliberate"));
        synonymGroups.put("strong", Arrays.asList("powerful","mighty","sturdy","robust","firm","resolute"));
        synonymGroups.put("weak", Arrays.asList("feeble","frail","fragile","delicate","tender"));
        synonymGroups.put("cold", Arrays.asList("chill","frost","icy","frigid","cool","wintry"));
        synonymGroups.put("warm", Arrays.asList("heated","cozy","mild","balmy","tepid"));
        synonymGroups.put("bright", Arrays.asList("luminous","radiant","brilliant","vivid","glowing","shining"));
        synonymGroups.put("quiet", Arrays.asList("silent","still","hushed","peaceful","serene","calm"));
        synonymGroups.put("loud", Arrays.asList("noisy","thunderous","booming","clamorous","roaring"));
        synonymGroups.put("sweet", Arrays.asList("gentle","tender","pleasant","delightful","lovely"));
        synonymGroups.put("bitter", Arrays.asList("harsh","acrid","sharp","cutting","poignant"));
        synonymGroups.put("true", Arrays.asList("honest","genuine","authentic","sincere","real","faithful"));
        synonymGroups.put("false", Arrays.asList("untrue","fake","deceptive","dishonest","hollow"));
        synonymGroups.put("good", Arrays.asList("kind","noble","virtuous","worthy","pure","righteous"));
        synonymGroups.put("evil", Arrays.asList("wicked","dark","sinister","malevolent","vile","corrupt"));
    }

    private static synchronized void ensureDictionaryLoaded() {
        if (dictionaryLoaded) return;
        
        // Skip Java dictionary loading if native is available
        if (isNativeAvailable()) {
            dictionaryLoaded = true;
            return;
        }
        
        dictRhymesByKey = new HashMap<>();

        // Load lightweight word list from the simple dictionary JSON files (a.json ... z.json)
        for (char c = 'a'; c <= 'z'; c++) {
            String path = "simple-english-dictionary/data/" + c + ".json";
            List<String> nativeWords = loadDictWordsNative(path);
            if (nativeWords != null && !nativeWords.isEmpty()) {
                for (String word : nativeWords) {
                    addWordToDictionary(word);
                }
                continue;
            }
            try (java.io.InputStream in = ResourceLoader.getResourceAsStream(path)) {
                if (in == null) continue;
                String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                parseDictionaryChunk(json);
            } catch (Throwable ignored) {
                // fail-safe: skip malformed file but keep others
            }
        }
        dictionaryLoaded = true;
    }

    // Very small, tolerant parser that extracts the top-level word keys.
    private static void parseDictionaryChunk(String json) {
        if (json == null || json.isEmpty()) return;
        int idx = 0;
        final int len = json.length();
        while (idx < len) {
            int keyStart = json.indexOf('"', idx);
            if (keyStart < 0 || keyStart + 1 >= len) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String word = json.substring(keyStart + 1, keyEnd);
            idx = keyEnd + 1;
            int objStart = json.indexOf('{', idx);
            if (objStart < 0) break;
            int depth = 1;
            int cursor = objStart + 1;
            while (cursor < len && depth > 0) {
                char ch = json.charAt(cursor);
                if (ch == '{') depth++;
                else if (ch == '}') depth--;
                cursor++;
            }
            int objEnd = cursor;
            if (depth != 0 || objEnd <= objStart) {
                idx = objStart + 1;
                continue;
            }
            idx = objEnd;
            addWordToDictionary(word);
        }
    }

    private static void addWordToDictionary(String word) {
        if (word == null || word.isEmpty()) return;
        String lower = word.toLowerCase(Locale.ROOT);
        String key = PoetryUtils.rhymeKey(lower);
        if (key == null || key.isBlank()) return;
        List<String> bucket = dictRhymesByKey.computeIfAbsent(key, k -> new ArrayList<>());
        if (!bucket.contains(lower)) {
            bucket.add(lower);
        }
    }

    private static List<String> loadDictWordsNative(String resourcePath) {
        URL url = ResourceLoader.getResource(resourcePath);
        if (url == null || !"file".equalsIgnoreCase(url.getProtocol())) return null;
        try {
            String filePath = Paths.get(url.toURI()).toString();
            return NativeAccess.jsonLoadDictWords(filePath);
        } catch (Exception ignored) {
            return null;
        }
    }
}
