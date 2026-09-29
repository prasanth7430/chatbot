package com.example.chatbot.nlp;

import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Preprocesses raw input text through a deterministic NLP pipeline:
 * <ol>
 *   <li>Normalization and lowercasing</li>
 *   <li>URL and emoji/punctuation removal (with negation preservation)</li>
 *   <li>Tokenization</li>
 *   <li>Stopword removal (keeping negation words)</li>
 *   <li>Porter Stemming</li>
 * </ol>
 */
@Component
public class TextPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(TextPreprocessor.class);

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+");
    private static final Pattern CONTRACTION_NT = Pattern.compile("(?i)\\b(\\w+)n't\\b");
    private static final Pattern PUNCTUATION_AND_EMOJIS = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private final Set<String> stopwords;
    private final PorterStemmer stemmer;
    private final SimpleTokenizer tokenizer;

    public TextPreprocessor() {
        this.stopwords = loadStopwords("stopwords.txt");
        this.stemmer = new PorterStemmer();
        this.tokenizer = SimpleTokenizer.INSTANCE;
    }

    /**
     * Constructs a preprocessor with a custom stopwords set (useful for unit testing).
     *
     * @param stopwords Set of stopwords to filter.
     */
    public TextPreprocessor(Set<String> stopwords) {
        this.stopwords = stopwords == null ? Set.of() : Set.copyOf(stopwords);
        this.stemmer = new PorterStemmer();
        this.tokenizer = SimpleTokenizer.INSTANCE;
    }

    /**
     * Loads stopwords from a classpath file.
     *
     * @param resourcePath Classpath path to the stopwords file.
     * @return Set of lowercase stopwords.
     */
    private Set<String> loadStopwords(String resourcePath) {
        Set<String> words = new HashSet<>();
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase(Locale.ROOT);
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        words.add(trimmed);
                    }
                }
            }
            log.info("Loaded {} stopwords from classpath:{}", words.size(), resourcePath);
        } catch (IOException e) {
            log.error("Failed to load stopwords from {}: {}", resourcePath, e.getMessage(), e);
            throw new IllegalStateException("Cannot initialize TextPreprocessor without stopwords: " + resourcePath, e);
        }
        return Collections.unmodifiableSet(words);
    }

    /**
     * Normalizes text, strips URLs, handles contractions to preserve negation,
     * removes emojis and punctuation.
     *
     * @param text Raw input string.
     * @return Cleaned string.
     */
    public String cleanText(String text) {
        if (text == null) {
            return "";
        }

        // Lowercase
        String cleaned = text.toLowerCase(Locale.ROOT);

        // Strip URLs
        cleaned = URL_PATTERN.matcher(cleaned).replaceAll(" ");

        // Preserve negations in contractions e.g. "don't" -> "do not", "can't" -> "can not"
        cleaned = CONTRACTION_NT.matcher(cleaned).replaceAll("$1 not");

        // Strip emojis, punctuation, symbols (preserve alphanumeric and whitespace)
        cleaned = PUNCTUATION_AND_EMOJIS.matcher(cleaned).replaceAll(" ");

        // Collapse multiple whitespaces
        return MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ").trim();
    }

    /**
     * Preprocesses text into an array of stemmed, non-stopword tokens.
     *
     * @param text Raw input text.
     * @return Array of preprocessed tokens.
     */
    public String[] preprocessToArray(String text) {
        List<String> tokens = preprocessToTokens(text);
        return tokens.toArray(new String[0]);
    }

    /**
     * Preprocesses text into a list of stemmed, non-stopword tokens.
     *
     * @param text Raw input text.
     * @return List of preprocessed tokens.
     */
    public List<String> preprocessToTokens(String text) {
        String cleaned = cleanText(text);
        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }

        String[] rawTokens = tokenizer.tokenize(cleaned);
        List<String> result = new ArrayList<>();

        for (String rawToken : rawTokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }

            // Exclude stopwords, but ensure negations are never filtered
            if (stopwords.contains(token) && !isNegationWord(token)) {
                continue;
            }

            // Skip pure numbers/digits (such as order IDs) so they don't introduce OOV noise
            if (token.matches("\\d+")) {
                continue;
            }

            // Porter Stemming
            CharSequence stemmed = stemmer.stem(token);
            result.add(stemmed.toString());
        }

        return result;
    }

    /**
     * Preprocesses text and joins the resulting tokens with single spaces.
     *
     * @param text Raw input text.
     * @return Space-separated preprocessed tokens.
     */
    public String preprocess(String text) {
        List<String> tokens = preprocessToTokens(text);
        return String.join(" ", tokens);
    }

    /**
     * Verifies if a token is a negation word that must always be retained.
     */
    private boolean isNegationWord(String token) {
        return "not".equals(token) || "no".equals(token) || "never".equals(token)
                || "nor".equals(token) || "cannot".equals(token) || "cant".equals(token)
                || "dont".equals(token) || "wont".equals(token);
    }

    public Set<String> getStopwords() {
        return stopwords;
    }
}
