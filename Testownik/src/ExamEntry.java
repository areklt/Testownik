import java.util.List;

public class ExamEntry {
    Question question;
    List<Integer> selectedAnswers;
    boolean isCorrect;

    public ExamEntry(Question question, List<Integer> selectedAnswers, boolean isCorrect) {
        this.question = question;
        this.selectedAnswers = selectedAnswers;
        this.isCorrect = isCorrect;
    }
}
