package xin.vanilla.sakura.data.calendar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xin.vanilla.sakura.data.personaldate.LunarCalendar;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Reads external calendar tables and writes the built-in 1900-2100 rules on first use. */
public final class CalendarRuleRepository {
    public static final String FILE_NAME = "calendar-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;

    public CalendarRuleRepository(Path directory) {
        this.directory = directory;
    }

    public CalendarRegistry loadOrCreate() throws IOException {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            saveDocument(defaultDocument());
        }
        return CalendarRegistry.fromDefinitions(readDocument().getCalendars());
    }

    public CalendarRuleDocument readDocument() throws IOException {
        try (Reader reader = Files.newBufferedReader(directory.resolve(FILE_NAME),
                StandardCharsets.UTF_8)) {
            CalendarRuleDocument document = GSON.fromJson(reader, CalendarRuleDocument.class);
            if (document == null || document.getSchemaVersion() != 1
                    || document.getCalendars() == null) {
                throw new IllegalArgumentException("Unsupported calendar rule document");
            }
            return document;
        }
    }

    public void saveDocument(CalendarRuleDocument document) throws IOException {
        Files.createDirectories(directory);
        try (Writer writer = Files.newBufferedWriter(directory.resolve(FILE_NAME),
                StandardCharsets.UTF_8)) {
            GSON.toJson(document, writer);
        }
    }

    public static CalendarRuleDocument defaultDocument() {
        CalendarRuleDocument document = new CalendarRuleDocument();
        document.setCalendars(new ArrayList<>());
        document.getCalendars().add(LunarCalendar.ruleDefinition());
        return document;
    }
}
