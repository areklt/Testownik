import java.util.List;

public class ExamEntry {
    public Question question;
    public List<Integer> selectedAnswers; // Indeksy odpowiedzi zaznaczonych przez użytkownika
    public boolean isCorrect;

    public ExamEntry(Question question, List<Integer> selectedAnswers, boolean isCorrect) {
        this.question = question;
        this.selectedAnswers = selectedAnswers;
        this.isCorrect = isCorrect;
    }
}