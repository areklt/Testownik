// QuestionParser.java
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestionParser {

    // Regex do znajdowania tagu [img]...[/img]
    // Grupa 1 przechwytuje nazwę pliku wewnątrz tagu
    private static final Pattern IMAGE_TAG_PATTERN = Pattern.compile("\\[img\\]([^\\[]+?)\\[/img\\]");

    /**
     * Parsuje plik tekstowy zawierający pytania.
     * Używane do ładowania pojedynczych plików TXT.
     *
     * @param file Plik do parsowania.
     * @return Lista obiektów Question.
     */
    public List<Question> parseFile(File file) {
        System.out.println("DEBUG Parser: Rozpoczynam parsowanie pliku: " + file.getName());
        try {
            // Odczytujemy całą zawartość pliku jako String
            String fileContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            System.out.println("DEBUG Parser: Odczytano " + fileContent.length() + " znakow z pliku.");
            // Przekazujemy katalog rodzicielski pliku TXT jako baseDirectory dla obrazków
            return parseString(fileContent, file.getParentFile()); 
        } catch (IOException e) {
            System.err.println("DEBUG Parser: Błąd odczytu pliku: " + file.getName() + " - " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Parsuje zawartość tekstową zawierającą pytania.
     * Ta metoda jest główną logiką parsowania, obsługującą różne struktury pytań.
     * Przyjmuje baseDirectory do określania pełnej ścieżki do obrazków.
     *
     * @param content Zawartość tekstowa do parsowania.
     * @param baseDirectory Katalog bazowy, z którego mają być ładowane obrazki (może być null).
     * @return Lista obiektów Question.
     */
    public List<Question> parseString(String content, File baseDirectory) { 
        List<Question> questions = new ArrayList<>();
        System.out.println("DEBUG Parser: Rozpoczynam parsowanie Stringa. Dlugosc: " + content.length());
        System.out.println("DEBUG Parser: Katalog bazowy dla obrazkow: " + (baseDirectory != null ? baseDirectory.getAbsolutePath() : "brak/nieznany (obraki beda relatywne)"));

        // Usunięcie ewentualnego BOM (Byte Order Mark)
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
            System.out.println("DEBUG Parser: Usunieto BOM.");
        }

        String[] lines = content.split("\\r?\\n"); // Podziel na linie, obsługując CR LF i LF
        System.out.println("DEBUG Parser: Podzielono na " + lines.length + " linii.");

        if (lines.length < 2) { // Minimalnie 2 linie: linia kontrolna + 1 linia pytania/odpowiedzi
            System.out.println("DEBUG Parser: Za malo linii w pliku. Minimalnie 2 (linia kontrolna + 1 linia tresci/odpowiedzi).");
            return questions;
        }

        int currentLineIndex = 0;
        // Pętla do przetwarzania kolejnych pytań w pliku
        while (currentLineIndex < lines.length) {
            String controlLine = lines[currentLineIndex].trim();
            System.out.println("\nDEBUG Parser: --- Nowe pytanie ---");
            System.out.println("DEBUG Parser: Analiza linii kontrolnej (" + currentLineIndex + "): '" + controlLine + "'");

            if (controlLine.isEmpty()) { // Pusta linia, pomiń
                currentLineIndex++;
                continue;
            }

            // Walidacja linii kontrolnej: musi zaczynać się od X/x i mieć ciąg 0/1
            if (!(controlLine.startsWith("X") || controlLine.startsWith("x")) || controlLine.length() < 2 || !controlLine.substring(1).matches("^[01]+$")) {
                System.out.println("DEBUG Parser: Linia nie jest poprawna linia kontrolna: '" + controlLine + "'. Przerwano parsowanie lub pominieto.");
                currentLineIndex++; // Przejdź do następnej linii, bo ta nie jest pytaniem
                continue;
            }

            // Określ liczbę linii treści pytania (liczba 'X')
            int numQuestionLines = 0;
            while (numQuestionLines < controlLine.length() && (controlLine.charAt(numQuestionLines) == 'X' || controlLine.charAt(numQuestionLines) == 'x')) {
                numQuestionLines++;
            }
            System.out.println("DEBUG Parser: Wykryto " + numQuestionLines + " linii dla tresci pytania (z 'X').");

            // Określ wskaźniki poprawnych odpowiedzi (ciąg 0/1 po 'X')
            String answerIndicators = controlLine.substring(numQuestionLines); 
            System.out.println("DEBUG Parser: Wskazniki odpowiedzi: '" + answerIndicators + "'");
            int numAnswerOptions = answerIndicators.length(); // Liczba opcji odpowiedzi
            System.out.println("DEBUG Parser: Wykryto " + numAnswerOptions + " opcji odpowiedzi (z cyfr 0/1).");

            // Oblicz całkowitą liczbę linii wymaganą dla bieżącego pytania
            int requiredTotalLinesForQuestion = 1 + numQuestionLines + numAnswerOptions; // linia kontrolna + linie pytania + linie odpowiedzi
            if (currentLineIndex + requiredTotalLinesForQuestion > lines.length) {
                System.out.println("DEBUG Parser: Za malo linii w pliku dla oczekiwanego formatu pytania. Oczekiwano: " + requiredTotalLinesForQuestion + " linii od obecnej.");
                System.out.println("DEBUG Parser: Dostepnych: " + (lines.length - currentLineIndex) + ". Przerwano parsowanie.");
                break; // Nie ma wystarczającej liczby linii, więc przerywamy
            }

            StringBuilder questionTextBuilder = new StringBuilder();
            String foundImagePath = null; // Zmienna do przechowywania ścieżki obrazka

            // Pętla do zbierania linii treści pytania
            for (int i = 0; i < numQuestionLines; i++) {
                String line = lines[currentLineIndex + 1 + i]; // Pobierz linię (bez trim na razie, żeby nie usunąć spacji na końcu)
                System.out.println("DEBUG Parser: Linia tresci pytania (" + (currentLineIndex + 1 + i) + "): '" + line + "'");
                
                // Szukaj taga obrazka w każdej linii pytania
                Matcher matcher = IMAGE_TAG_PATTERN.matcher(line);
                if (matcher.find()) {
                    String imageName = matcher.group(1); // Nazwa pliku obrazka (np. "nazwa.png")
                    
                    // Zbuduj pełną ścieżkę do obrazka
                    if (baseDirectory != null) {
                        foundImagePath = new File(baseDirectory, imageName).getAbsolutePath();
                    } else {
                        // Jeśli baseDirectory jest null, zakładamy, że to ścieżka relatywna do katalogu uruchomienia
                        foundImagePath = imageName;
                    }
                    System.out.println("DEBUG Parser: Wykryto tag obrazka: '" + imageName + "', sciezka do przekazania do Question: " + foundImagePath);
                    
                    // Usuń tag [img]...[/img] z linii, która zostanie dodana do treści pytania
                    line = matcher.replaceAll("").trim(); 
                    System.out.println("DEBUG Parser: Linia po usunieciu tagu obrazka: '" + line + "'");
                }
                
                // Dodaj linię do treści pytania, tylko jeśli nie jest pusta po usunięciu tagu
                // Dodajemy spację między liniami pytania dla czytelności
                if (!line.isEmpty()) { 
                    questionTextBuilder.append(line).append(" "); 
                }
            }
            String questionText = questionTextBuilder.toString().trim(); // Usuń końcowe spacje
            System.out.println("DEBUG Parser: Ostateczna tresc pytania (po usunieciu img tagu i trim): '" + questionText + "'");

            // Zbierz odpowiedzi i ich poprawność
            List<String> answers = new ArrayList<>();
            Set<Integer> correctAnswers = new HashSet<>();
            for (int i = 0; i < numAnswerOptions; i++) {
                String answerLine = lines[currentLineIndex + 1 + numQuestionLines + i].trim();
                answers.add(answerLine);
                System.out.println("DEBUG Parser: Opcja odpowiedzi (" + (currentLineIndex + 1 + numQuestionLines + i) + "): '" + answerLine + "'");

                // Sprawdź, czy dana odpowiedź jest poprawna na podstawie wskaźnika
                if (i < answerIndicators.length() && answerIndicators.charAt(i) == '1') {
                    correctAnswers.add(i); // Indeks odpowiedzi (0-based)
                    System.out.println("DEBUG Parser: Odpowiedz nr " + i + " jest poprawna.");
                }
            }

            // Jeśli wszystkie wymagane elementy są obecne, dodaj pytanie do listy
            // Pytanie może mieć pustą treść, jeśli zawiera tylko obrazek
            if (!answers.isEmpty() && !correctAnswers.isEmpty() && (!questionText.isEmpty() || foundImagePath != null)) { 
                questions.add(new Question(questionText, answers, correctAnswers, foundImagePath)); // Użyj nowego konstruktora Question
                System.out.println("DEBUG Parser: Dodano pytanie. Obecnie: " + questions.size() + " pytan.");
            } else {
                System.out.println("DEBUG Parser: Niekompletne dane dla pytania, nie dodano. " + 
                                   "Tresc pytania (pusta jesli tylko obrazek): '" + questionText + "', " + 
                                   "Liczba odpowiedzi: " + answers.size() + ", " + 
                                   "Liczba poprawnych: " + correctAnswers.size() + ", " + 
                                   "Sciezka obrazka: " + (foundImagePath != null ? foundImagePath : "brak"));
            }

            // Przejdź do początku następnego potencjalnego pytania (poza bieżące pytanie i jego odpowiedzi)
            currentLineIndex += requiredTotalLinesForQuestion;
        }

        System.out.println("DEBUG Parser: Zakończono parsowanie. Łącznie znaleziono: " + questions.size() + " pytan.");
        return questions;
    }
}