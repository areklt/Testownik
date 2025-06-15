import java.util.List;
import java.util.Set;

public class Question {
    String questionText;
    List<String> answers;
    Set<Integer> correctAnswers; // Zmieniono z List<Integer> na Set<Integer>
    String imagePath; // Nowe pole na ścieżkę do obrazka

    public Question(String questionText, List<String> answers, Set<Integer> correctAnswers, String imagePath) { // Zmieniono konstruktor
        this.questionText = questionText;
        this.answers = answers;
        this.correctAnswers = correctAnswers;
        this.imagePath = imagePath;
    }
}
QuestionParser.java
Java

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestionParser {

    // Regex do parsowania pytań, odpowiedzi i poprawnych odpowiedzi
    // Dodano grupę (3) dla opcjonalnej ścieżki do obrazka
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "Pytanie:\\s*(.*?)(?:\\s*\\[Obrazek:\\s*(.*?)\\])?\\s*Odpowiedzi:\\s*(.*?)\\s*Poprawna:\\s*([0-9,\\s]+)",
            Pattern.DOTALL
    );
    // Zaktualizowany regex do odpowiedzi, aby obsłużyć numery i tekst
    private static final Pattern ANSWER_PATTERN = Pattern.compile("(\\d+\\.\\s*)(.*?)(?=(?:\\d+\\.\\s*)|$)", Pattern.DOTALL);

    // Dodana zmienna do przechowywania katalogu bazowego dla obrazków
    private File baseDirectory;

    public List<Question> parseFile(File file) {
        List<Question> questions = new ArrayList<>();
        this.baseDirectory = file.getParentFile(); // Ustaw katalog bazowy na katalog pliku TXT

        try {
            String content = Files.readString(file.toPath());
            Matcher questionMatcher = QUESTION_PATTERN.matcher(content);

            while (questionMatcher.find()) {
                String questionText = questionMatcher.group(1).trim();
                String imageFileName = questionMatcher.group(2); // Grupa dla nazwy pliku obrazka
                String answersBlock = questionMatcher.group(3).trim();
                String correctAnswersStr = questionMatcher.group(4).trim();

                List<String> answers = new ArrayList<>();
                Matcher answerMatcher = ANSWER_PATTERN.matcher(answersBlock);
                while (answerMatcher.find()) {
                    answers.add(answerMatcher.group(2).trim());
                }

                Set<Integer> correctAnswers = new HashSet<>(); // Zmieniono na Set
                for (String s : correctAnswersStr.split(",")) {
                    try {
                        // Parsujemy indeksy odpowiedzi od 0, więc odejmujemy 1
                        correctAnswers.add(Integer.parseInt(s.trim()) - 1);
                    } catch (NumberFormatException e) {
                        System.err.println("Błąd parsowania poprawnej odpowiedzi: " + s);
                    }
                }

                String imagePath = null;
                if (imageFileName != null && !imageFileName.isEmpty()) {
                    // Tworzymy absolutną ścieżkę do obrazka
                    File imageFile = new File(baseDirectory, imageFileName);
                    if (imageFile.exists()) {
                        imagePath = imageFile.getAbsolutePath();
                    } else {
                        System.err.println("OSTRZEŻENIE: Plik obrazka nie znaleziony: " + imageFile.getAbsolutePath());
                    }
                }
                
                questions.add(new Question(questionText, answers, correctAnswers, imagePath));
            }
        } catch (IOException e) {
            System.err.println("Błąd odczytu pliku: " + file.getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        return questions;
    }
}
