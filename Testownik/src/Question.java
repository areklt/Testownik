// Question.java
import java.util.List;
import java.util.Set;

public class Question {
    public String questionText;
    public List<String> answers;
    public Set<Integer> correctAnswers;
    public String imagePath; // Ścieżka do pliku obrazka

    public Question(String questionText, List<String> answers, Set<Integer> correctAnswers, String imagePath) {
        this.questionText = questionText;
        this.answers = answers;
        this.correctAnswers = correctAnswers;
        this.imagePath = imagePath; // Może być null, jeśli brak obrazka
    }

    // Konstruktor bez obrazka, używany dla pytań bez grafiki
    public Question(String questionText, List<String> answers, Set<Integer> correctAnswers) {
        this(questionText, answers, correctAnswers, null);
    }

    @Override
    public String toString() {
        return "Pytanie: '" + questionText + "', Odpowiedzi: " + answers + 
               ", Poprawne: " + correctAnswers + 
               (imagePath != null ? ", Obrazek: '" + imagePath + "'" : "");
    }
}