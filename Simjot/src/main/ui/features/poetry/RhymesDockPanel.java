package main.ui.features.poetry;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.Duration;

import main.core.poetry.PoetryUtils;
import main.core.sim.prefs.SimSettings;
import main.core.sim.llm.api.SimLLMClient;
import main.core.sim.llm.api.SimLLMRequest;
import main.core.sim.llm.api.SimLLMResponse;
import main.core.sim.llm.ollama.OllamaClient;
import main.core.sim.llm.openai.OpenAIClient;
import main.core.sim.persona.SimPersonality;

/**
 * Simple dock showing rhyme key and lightweight rhyme/synonym suggestions
 * for the caret word. Keeps heuristics local and fast.
 */
public class RhymesDockPanel extends JPanel {
    private final JLabel title = new JLabel("Rhymes & Synonyms");
    private final JTextArea body = new JTextArea();

    public RhymesDockPanel(){
        super(new BorderLayout());
        setOpaque(false);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6,6,4,6));
        // Simple header: just the title (monochrome)
        add(title, BorderLayout.NORTH);

        body.setOpaque(false);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(new Font("SansSerif", Font.PLAIN, 12));
        body.setForeground(new Color(70,70,70));
        JScrollPane sp = new JScrollPane(body){
            { setBorder(BorderFactory.createEmptyBorder()); setOpaque(false); getViewport().setOpaque(false);} };
        add(sp, BorderLayout.CENTER);
        setPreferredSize(new Dimension(220, 0));
        setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
    }

    public void updateForWord(String word){
        update(word, null);
    }

    /**
     * Update using caret word and optional full text context to mine poem-local rhymes.
     */
    public void update(String word, String fullText){
        if (word == null || word.isBlank()) {
            body.setText("Place cursor on a word.");
            return;
        }
        String key = PoetryUtils.rhymeKey(word);
        int sylls = PoetryUtils.countSyllables(word);
        List<String> builtInRhymes = naiveRhymes(word);
        List<String> syns = naiveSynonyms(word);

        // Mine poem-local candidates
        List<String> exact = new ArrayList<>();
        List<String> near = new ArrayList<>();
        if (fullText != null && !fullText.isBlank() && key != null) {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (String line : PoetryUtils.splitLines(fullText)) {
                for (String w : PoetryUtils.wordsInLine(line)) {
                    String norm = w.toLowerCase(Locale.ROOT);
                    if (norm.equalsIgnoreCase(word)) continue;
                    if (!seen.add(norm)) continue;
                    String k = PoetryUtils.rhymeKey(norm);
                    if (k == null) continue;
                    if (k.equals(key)) exact.add(norm);
                    else if (k.endsWith(safeTail(key, 2)) || key.endsWith(safeTail(k, 2))) near.add(norm);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Word: ").append(word).append("  •  ")
          .append(sylls).append(" syllable").append(sylls==1?"":"s").append('\n');
        sb.append("Rhyme key: ").append(key!=null?key:"-").append('\n');

        if (!exact.isEmpty()) sb.append('\n').append("Exact rhymes in poem: ").append(joinCap(exact)).append('\n');
        if (!near.isEmpty()) sb.append("Near rhymes in poem: ").append(joinCap(near)).append('\n');

        // Built-in fallback suggestions
        if (!builtInRhymes.isEmpty()) sb.append('\n').append("Suggestions: ").append(joinCap(builtInRhymes)).append('\n');

        // Synonyms
        sb.append('\n').append("Synonyms: ");
        if (syns.isEmpty()) sb.append("(none)"); else sb.append(joinCap(syns));

        body.setText(sb.toString());
        body.setCaretPosition(0);

        // Hybrid: optionally augment with Sim in background (non-blocking)
        maybeQuerySimAsync(word, key, sylls, fullText);
    }

    // Extremely small heuristic sets to avoid dependencies. You can later plug in a service.
    private List<String> naiveRhymes(String word){
        String key = PoetryUtils.rhymeKey(word);
        if (key == null) return List.of();
        String[][] tiny = new String[][]{
                {"ight","light","night","flight","sight","bright"},
                {"ow","glow","slow","snow","flow","grow"},
                {"ay","day","play","say","away","delay"},
                {"ing","sing","ring","wing","spring","thing"},
                {"ove","love","dove","glove","above"}
        };
        List<String> out = new ArrayList<>();
        for (String[] row : tiny){
            if (key.endsWith(row[0])){
                for (int i=1;i<row.length;i++) if (!row[i].equalsIgnoreCase(word)) out.add(row[i]);
            }
        }
        return out;
    }

    private List<String> naiveSynonyms(String word){
        String w = word.toLowerCase(Locale.ROOT);
        return switch (w) {
            case "love" -> List.of("affection","ardor","devotion","fondness");
            case "dark" -> List.of("dim","murky","dusky","tenebrous");
            case "light" -> List.of("glow","gleam","radiance","luster");
            case "river" -> List.of("stream","brook","current","run");
            default -> List.of();
        };
    }

    private static String safeTail(String s, int n){
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(s.length()-n);
    }

    private static String joinCap(List<String> items){
        java.util.List<String> out = new java.util.ArrayList<>(items.size());
        for (String i : items) out.add(cap(i));
        return String.join(", ", out);
    }

    private static String cap(String s){
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // --- Optional Sim augmentation (async, safe) ---
    private volatile SwingWorker<String, Void> simWorker;

    private void maybeQuerySimAsync(String word, String rhymeKey, int syllables, String fullText) {
        try {
            SimSettings ss = SimSettings.get();
            if (!ss.isEnabled() || !ss.isLlmEnabled()) return;
            // Cancel any in-flight worker
            SwingWorker<String, Void> prev = simWorker;
            if (prev != null && !prev.isDone()) {
                prev.cancel(true);
            }
            final String provider = ss.getLlmProvider();
            final SimLLMClient client = buildClient(provider, ss);
            if (client == null) return;

            final String poem = fullText == null ? "" : fullText;
            final String system = buildSystemPrompt();
            final String user = buildUserPrompt(word, rhymeKey, syllables, poem);
            final SimLLMRequest req = new SimLLMRequest(system, user, 256, 0.7);

            // No header status text while enhancing (keep UI minimal)

            simWorker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws Exception {
                    try {
                        SimLLMResponse resp = client.generate(req, Duration.ofSeconds(10));
                        return resp == null ? "" : resp.text;
                    } catch (Throwable t) {
                        return ""; // fail silent; keep local heuristics
                    }
                }
                @Override
                protected void done() {
                    if (isCancelled()) return;
                    String result = "";
                    try { result = get(); } catch (Throwable ignored) {}
                    final String simOut = (result == null || result.isBlank()) ? null : result.trim();
                    if (simOut != null) {
                        appendSimSection(simOut);
                    }
                }
            };
            simWorker.execute();
        } catch (Throwable ignored) { /* keep UI resilient */ }
    }

    private SimLLMClient buildClient(String provider, SimSettings ss) {
        try {
            String p = (provider == null ? "ollama" : provider.trim().toLowerCase(Locale.ROOT));
            switch (p) {
                case "openai":
                    String key = ss.getOpenAIApiKey();
                    if (key == null || key.isBlank()) return null;
                    return new OpenAIClient(key, ss.getOpenAIModel(), ss.getOpenAIBaseUrl());
                case "ollama":
                default:
                    return new OllamaClient(ss.getOllamaEndpoint(), ss.getOllamaModel());
            }
        } catch (Throwable e) {
            return null;
        }
    }

    private String buildSystemPrompt() {
        // Reuse persona tone but steer towards concise, structured poetry guidance
        String base = main.core.sim.llm.prompt.PromptBuilder.systemPrompt(SimPersonality.Type.GENTLE);
        return base + "\n\nYou are also a succinct poetry craft assistant. When asked for rhyme guidance:"
                + "\n- Consider the poem context."
                + "\n- Prefer concise lists."
                + "\n- Avoid any meta, XML/HTML, code fences, or chain-of-thought."
                + "\n- Output plain text only under 120 words.";
    }

    private String buildUserPrompt(String word, String rhymeKey, int syllables, String poem) {
        String head = "CaretWord: '" + word + "' (syllables=" + syllables + ", rhymeKey=" + (rhymeKey==null?"-":rhymeKey) + ")";
        String ctx = poem == null ? "" : poem.trim();
        if (ctx.length() > 2000) ctx = ctx.substring(Math.max(0, ctx.length()-2000)); // last ~2K chars for locality
        return String.join("\n",
                head,
                "Poem (tail excerpt):",
                ctx,
                "\nTask: Suggest compact, high-quality options for these sections:",
                "- Rhymes (best 6):",
                "- Near rhymes (best 6):",
                "- Slant rhymes (if useful, <=4):",
                "- Poetic synonyms (<=8), avoid clichés:",
                "- Imagery/metaphor cues (<=3, short):"
        );
    }

    private void appendSimSection(String text){
        SwingUtilities.invokeLater(() -> {
            String t = body.getText();
            StringBuilder sb = new StringBuilder(t.length() + text.length() + 64);
            sb.append(t);
            sb.append("\n\n— Sim suggestions —\n");
            sb.append(SimSuggestionFormatter.format(text.trim()));
            body.setText(sb.toString());
            body.setCaretPosition(0);
        });
    }
}
