import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main implements FileDrop.Listener {
    private JFrame frame;

    // Definicja kolorów dla ciemnego motywu
    private static final Color BACKGROUND_COLOR = new Color(40, 40, 40);
    private static final Color FOREGROUND_COLOR = new Color(240, 240, 240);
    private static final Color BUTTON_BACKGROUND_COLOR = new Color(60, 60, 60);
    private static final Color BUTTON_FOREGROUND_COLOR = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(90, 90, 90);
    private static final Color HIGHLIGHT_BORDER_COLOR = new Color(0, 120, 215);

    // Panele główne aplikacji
    private JPanel mainPanel;
    private JPanel mainMenuPanel;
    private JPanel quizExamPanel;
    private JPanel examSummaryPanel;
    private JPanel answerPanel;

    // Komponenty interfejsu quizu/egzaminu
    private JLabel questionLabel;
    private List<JCheckBox> answerBoxes;
    private JButton nextButton;

    // Referencje do przycisków w menu głównym
    private JButton quizButton;
    private JButton examButton;
    private JButton loadButton;
    private JButton backToMenuButton;

    // Dane aplikacji (pytania, tryb egzaminu, wyniki)
    private List<Question> allQuestions;
    private Question currentQuestion;
    private boolean examMode = false;
    private List<Question> examQuestions;
    private List<ExamEntry> examResults;
    private int currentExamIndex;
    private int correctExamAnswers;

    // Obramowania dla wizualizacji przeciągania plików
    private Border defaultMainPanelBorder;
    private Border highlightBorder;

    // Parser do wczytywania pytań z plików
    private QuestionParser questionParser = new QuestionParser();
    private Path tempImageDir = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Quiz App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        frame.getContentPane().setBackground(BACKGROUND_COLOR);

        defaultMainPanelBorder = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        highlightBorder = BorderFactory.createLineBorder(HIGHLIGHT_BORDER_COLOR, 3);

        // --- Ustawienia dla scrollbarów za pomocą UIManager (dla domyślnego L&F) ---
        // Te ustawienia muszą być po zainicjalizowaniu domyślnego LookAndFeel,
        // jeśli używasz jakiegoś specyficznego. Dla domyślnego Java L&F to jest ok tutaj.
        UIManager.put("ScrollBar.background", BACKGROUND_COLOR);
        UIManager.put("ScrollBar.foreground", FOREGROUND_COLOR);
        UIManager.put("ScrollBar.thumb", BORDER_COLOR);
        UIManager.put("ScrollBar.thumbHighlight", BORDER_COLOR);
        UIManager.put("ScrollBar.thumbDarkShadow", BORDER_COLOR);
        UIManager.put("ScrollBar.thumbLightShadow", BORDER_COLOR);
        UIManager.put("ScrollBar.track", BACKGROUND_COLOR);
        UIManager.put("ScrollBar.trackHighlight", BACKGROUND_COLOR);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.incrementButton", UIManager.get("null"));
        UIManager.put("ScrollBar.decrementButton", UIManager.get("null"));
        // --- KONIEC USTAWIEŃ SCROLLBARÓW ---

        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBackground(BACKGROUND_COLOR);
        frame.add(mainPanel, BorderLayout.CENTER);

        createMainMenuPanel();
        mainPanel.add(mainMenuPanel, "MainMenu");

        createQuizExamPanel(); // Ta metoda wymaga modyfikacji
        mainPanel.add(quizExamPanel, "QuizExam");

        createExamSummaryPanel();
        mainPanel.add(examSummaryPanel, "ExamSummary");

        CardLayout cl = (CardLayout)(mainPanel.getLayout());
        cl.show(mainPanel, "MainMenu");

        frame.setSize(700, 450);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupTempImages));
        
        new FileDrop(frame.getContentPane(), this);
    }

    private void createMainMenuPanel() {
        mainMenuPanel = new JPanel();
        mainMenuPanel.setBackground(BACKGROUND_COLOR);
        mainMenuPanel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;

        JLabel titleLabel = new JLabel("Witaj w aplikacji Quiz!", SwingConstants.CENTER);
        titleLabel.setForeground(FOREGROUND_COLOR);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        mainMenuPanel.add(titleLabel, gbc);

        JLabel dragDropInfo = new JLabel("Przeciągnij pliki (.txt lub .zip) tutaj, aby wczytać pytania, lub użyj przycisku.", SwingConstants.CENTER);
        dragDropInfo.setForeground(FOREGROUND_COLOR);
        dragDropInfo.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridy = 1;
        mainMenuPanel.add(dragDropInfo, gbc);

        loadButton = new JButton("Wczytaj pliki (.txt / .zip)");
        styleButton(loadButton);
        loadButton.setFont(new Font("Arial", Font.BOLD, 16));
        loadButton.setPreferredSize(new Dimension(250, 50));
        loadButton.addActionListener(e -> loadQuestionsDialog());
        gbc.gridy = 2;
        mainMenuPanel.add(loadButton, gbc);

        quizButton = new JButton("Tryb Quizu");
        styleButton(quizButton);
        quizButton.setFont(new Font("Arial", Font.BOLD, 16));
        quizButton.setPreferredSize(new Dimension(250, 50));
        quizButton.addActionListener(e -> {
            if (allQuestions == null || allQuestions.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Najpierw wczytaj pytania!", "Brak pytań", JOptionPane.WARNING_MESSAGE);
                return;
            }
            examMode = false;
            showQuizExamPanel();
            showNextRandomQuestion();
            updateFrameTitle("Quiz App - Tryb Quizu");
        });
        gbc.gridy = 3;
        mainMenuPanel.add(quizButton, gbc);
        quizButton.setEnabled(false);

        examButton = new JButton("Tryb Egzaminu (20 pytań)");
        styleButton(examButton);
        examButton.setFont(new Font("Arial", Font.BOLD, 16));
        examButton.setPreferredSize(new Dimension(250, 50));
        examButton.addActionListener(e -> {
            if (allQuestions == null || allQuestions.size() < 20) {
                JOptionPane.showMessageDialog(frame, "Za mało pytań do trybu egzaminu (wymagane minimum 20). Wczytaj więcej pytań.", "Brak pytań", JOptionPane.WARNING_MESSAGE);
                return;
            }
            showQuizExamPanel();
            startExamMode();
        });
        gbc.gridy = 4;
        mainMenuPanel.add(examButton, gbc);
        examButton.setEnabled(false);
    }

    private void createQuizExamPanel() {
        quizExamPanel = new JPanel(new BorderLayout(10, 10));
        quizExamPanel.setBackground(BACKGROUND_COLOR);
        quizExamPanel.setBorder(defaultMainPanelBorder);

        // Pytanie i obrazek będą w tym samym panelu, który może się scrollować
        JPanel questionImagePanel = new JPanel();
        questionImagePanel.setLayout(new BoxLayout(questionImagePanel, BoxLayout.Y_AXIS));
        questionImagePanel.setBackground(BACKGROUND_COLOR);

        questionLabel = new JLabel("Pytanie będzie tutaj.", SwingConstants.CENTER);
        questionLabel.setForeground(FOREGROUND_COLOR);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 16));
        questionLabel.setAlignmentX(Component.CENTER_ALIGNMENT); // Wyśrodkuj
        questionImagePanel.add(questionLabel);

        // Używamy JScrollPane dla questionImagePanel, aby obsługiwać długie pytania LUB duże obrazy
        JScrollPane questionScrollPane = new JScrollPane(questionImagePanel);
        questionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        questionScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // Niepotrzebny horyzontalny scroll
        questionScrollPane.setBackground(BACKGROUND_COLOR);
        questionScrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        questionScrollPane.setBorder(BorderFactory.createEmptyBorder()); // Usuń domyślne obramowanie

        quizExamPanel.add(questionScrollPane, BorderLayout.NORTH); // Dodaj scrollowany panel do NORTH

        answerPanel = new JPanel();
        answerPanel.setBackground(BACKGROUND_COLOR);
        answerPanel.setLayout(new BoxLayout(answerPanel, BoxLayout.Y_AXIS));
        answerBoxes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            JCheckBox checkBox = new JCheckBox();
            styleCheckBox(checkBox);
            checkBox.setFont(new Font("Arial", Font.PLAIN, 14));
            answerBoxes.add(checkBox);
            answerPanel.add(checkBox);
        }
        quizExamPanel.add(answerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomPanel.setBackground(BACKGROUND_COLOR);
        
        nextButton = new JButton("Dalej");
        styleButton(nextButton);
        nextButton.setFont(new Font("Arial", Font.BOLD, 14));
        nextButton.addActionListener(e -> checkAnswer());
        bottomPanel.add(nextButton);

        backToMenuButton = new JButton("Powrót do Menu Głównego");
        styleButton(backToMenuButton);
        backToMenuButton.setFont(new Font("Arial", Font.PLAIN, 12));
        backToMenuButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(frame, 
                "Czy na pewno chcesz wrócić do menu głównego? Postępy w obecnym trybie zostaną utracone.", 
                "Powrót do Menu", 
                JOptionPane.YES_NO_OPTION, 
                JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                showMainMenuPanel();
            }
        });
        bottomPanel.add(backToMenuButton);

        quizExamPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void createExamSummaryPanel() {
        examSummaryPanel = new JPanel(new BorderLayout(10, 10));
        examSummaryPanel.setBackground(BACKGROUND_COLOR);
        examSummaryPanel.setBorder(defaultMainPanelBorder);

        JTextArea summaryTextArea = new JTextArea();
        summaryTextArea.setEditable(false);
        summaryTextArea.setBackground(BACKGROUND_COLOR);
        summaryTextArea.setForeground(FOREGROUND_COLOR);
        summaryTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // --- Ustawienia zawijania tekstu (pozostają bez zmian) ---
        summaryTextArea.setLineWrap(true);
        summaryTextArea.setWrapStyleWord(true);
        // --- KONIEC ustawień zawijania tekstu ---
        
        JScrollPane scrollPane = new JScrollPane(summaryTextArea);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        scrollPane.setBackground(BACKGROUND_COLOR);
        
        // --- Ukrywanie poziomego paska przewijania (pozostaje bez zmian) ---
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // --- KONIEC ukrywania poziomego paska przewijania ---
        
        examSummaryPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonsPanel.setBackground(BACKGROUND_COLOR);

        JButton backToMenuButton = new JButton("Powrót do Menu Głównego");
        styleButton(backToMenuButton);
        backToMenuButton.addActionListener(e -> showMainMenuPanel());
        buttonsPanel.add(backToMenuButton);

        JButton retryExamButton = new JButton("Powtórz ten Egzamin");
        styleButton(retryExamButton);
        retryExamButton.addActionListener(e -> {
            if (examQuestions != null && !examQuestions.isEmpty()) {
                startExamMode();
                CardLayout cl = (CardLayout) mainPanel.getLayout();
                cl.show(mainPanel, "QuizExam");
                updateFrameTitle("Quiz App - Tryb Egzaminu (" + (currentExamIndex + 1) + "/" + examQuestions.size() + ")");
            } else {
                JOptionPane.showMessageDialog(frame, "Brak pytań do powtórzenia egzaminu.", "Błąd", JOptionPane.WARNING_MESSAGE);
            }
        });
        buttonsPanel.add(retryExamButton);

        examSummaryPanel.add(buttonsPanel, BorderLayout.SOUTH);
    }

    private void showQuizExamPanel() {
        CardLayout cl = (CardLayout)(mainPanel.getLayout());
        cl.show(mainPanel, "QuizExam");
    }

    private void showMainMenuPanel() {
        CardLayout cl = (CardLayout)(mainPanel.getLayout());
        cl.show(mainPanel, "MainMenu");
        updateFrameTitle("Quiz App");
        updateMainMenuButtonsState();
    }

    private void updateFrameTitle(String title) {
        frame.setTitle(title);
    }

    private void updateMainMenuButtonsState() {
        if (allQuestions != null && !allQuestions.isEmpty()) {
            quizButton.setEnabled(true);
            examButton.setEnabled(allQuestions.size() >= 20);
        } else {
            quizButton.setEnabled(false);
            examButton.setEnabled(false);
        }
    }

    @Override
    public void filesDropped(File[] files) {
        processSelectedFiles(files);
        mainMenuPanel.setBorder(defaultMainPanelBorder);
    }

    @Override
    public void dragEnter() {
        mainMenuPanel.setBorder(highlightBorder);
    }

    @Override
    public void dragExit() {
        mainMenuPanel.setBorder(defaultMainPanelBorder);
    }

    private void loadQuestionsDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt") || f.getName().toLowerCase().endsWith(".zip");
            }

            public String getDescription() {
                return "Pliki tekstowe (.txt) i archiwa Zip (.zip)";
            }
        });

        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            processSelectedFiles(chooser.getSelectedFiles());
        }
    }

    // --- PRZYWRÓCONA WERSJA loadQuestionsFromZip, KTÓRA DZIAŁAŁA ---
    private List<Question> loadQuestionsFromZip(File zipFile) {
        List<Question> questionsFromZip = new ArrayList<>();
        System.out.println("DEBUG Main: Rozpoczynam wczytywanie z pliku ZIP: " + zipFile.getName());

        try {
            // Tworzymy tymczasowy katalog na wypakowane obrazki i pliki TXT
            tempImageDir = Files.createTempDirectory("quiz_images_");
            System.out.println("DEBUG Main: Utworzono tymczasowy katalog dla ZIP: " + tempImageDir.toAbsolutePath());

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = tempImageDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        // Upewnij się, że katalog dla pliku istnieje
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zis, entryPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("DEBUG Main: Wypakowano plik z ZIP: " + entryPath.toAbsolutePath());
                    }
                    zis.closeEntry();
                }
            }

            // Teraz, gdy wszystko jest wypakowane, parsowanie plików TXT
            // Używamy walk, aby znaleźć wszystkie .txt w tymczasowym katalogu
            try (java.util.stream.Stream<Path> stream = Files.walk(tempImageDir)) { 
            stream.filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                  .forEach(p -> {
                      try {
                          questionsFromZip.addAll(questionParser.parseFile(p.toFile()));
                      } catch (Exception e) {
                          System.err.println("DEBUG Main: Blad podczas parsowania wypakowanego pliku TXT: " + p.getFileName() + " - " + e.getMessage());
                          e.printStackTrace();
                      }
                  });
        }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Błąd podczas wczytywania pliku ZIP: " + zipFile.getName() + "\nSzczegóły: " + e.getMessage(), "Błąd ZIP", JOptionPane.ERROR_MESSAGE);
            System.err.println("DEBUG Main: Błąd podczas wczytywania pliku ZIP: " + zipFile.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("DEBUG Main: Zakonczono wczytywanie z ZIP. Wczytano lacznie " + questionsFromZip.size() + " pytan.");
        return questionsFromZip;
    }

    private void processSelectedFiles(File[] files) {
        // Usuń poprzedni katalog tymczasowy z obrazkami, jeśli istniał
        if (tempImageDir != null && Files.exists(tempImageDir)) {
            try (java.util.stream.Stream<Path> stream = Files.walk(tempImageDir)) { 
                stream.sorted(Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(File::delete);
            } catch (IOException e) {
                System.err.println("Blad usuwania tymczasowego katalogu obrazkow: " + e.getMessage());
            }
            tempImageDir = null;
        }

        List<Question> loadedQuestions = new ArrayList<>();
        int txtFilesCount = 0;
        int zipFilesCount = 0;
        int questionsLoaded = 0;

        System.out.println("DEBUG Main: Rozpoczynam przetwarzanie wybranych plikow. Ilosc: " + files.length);
        for (File file : files) {
            String fileName = file.getName().toLowerCase();
            System.out.println("DEBUG Main: Przetwarzam plik: " + file.getAbsolutePath());
            if (fileName.endsWith(".txt")) {
                // Dla pojedynczych plików .txt, katalog bazowy to katalog rodzic
                loadedQuestions.addAll(questionParser.parseFile(file)); // parseFile już przekazuje parentFile
                txtFilesCount++;
                System.out.println("DEBUG Main: Z pliku TXT (" + file.getName() + ") wczytano: " + loadedQuestions.size() + " pytan.");
            } else if (fileName.endsWith(".zip")) {
                loadedQuestions.addAll(loadQuestionsFromZip(file)); // Ta metoda teraz zwraca ścieżki do tymczasowych plików
                zipFilesCount++;
                System.out.println("DEBUG Main: Z pliku ZIP (" + file.getName() + ") wczytano: " + loadedQuestions.size() + " pytan.");
            } else {
                JOptionPane.showMessageDialog(frame, "Ignorowano nieobsługiwany typ pliku: " + file.getName(), "Nieobsługiwany typ pliku", JOptionPane.WARNING_MESSAGE);
                System.out.println("DEBUG Main: Ignorowano nieobsługiwany typ pliku: " + file.getName());
            }
        }

        if (loadedQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Nie wczytano żadnych pytań. Sprawdź format plików.", "Brak pytań", JOptionPane.WARNING_MESSAGE);
            allQuestions = new ArrayList<>();
            System.out.println("DEBUG Main: loadedQuestions jest puste po przetworzeniu wszystkich plikow.");
        } else {
            allQuestions = loadedQuestions;
            questionsLoaded = allQuestions.size();
            JOptionPane.showMessageDialog(frame, String.format("Wczytano %d pytań z %d plików TXT i %d plików ZIP.", questionsLoaded, txtFilesCount, zipFilesCount), "Pytania wczytane", JOptionPane.INFORMATION_MESSAGE);
            System.out.println("DEBUG Main: Łącznie wczytano: " + questionsLoaded + " pytań do allQuestions.");
        }
        updateMainMenuButtonsState();
    }
    

    private void showNextRandomQuestion() {
        if (allQuestions == null || allQuestions.isEmpty()) {
            questionLabel.setText("Brak wczytanych pytań. Wczytaj je z menu.");
            return;
        }
        currentQuestion = allQuestions.get(new Random().nextInt(allQuestions.size()));
        displayQuestion(currentQuestion);
    }

    private void startExamMode() {
        if (allQuestions == null || allQuestions.size() < 20) {
            JOptionPane.showMessageDialog(frame, "Za mało pytań do trybu egzaminu (wymagane minimum 20). Wczytaj więcej pytań.", "Brak pytań", JOptionPane.WARNING_MESSAGE);
            return;
        }
        examQuestions = new ArrayList<>(allQuestions); 
        Collections.shuffle(examQuestions);
        examQuestions = examQuestions.subList(0, Math.min(20, examQuestions.size()));

        examResults = new ArrayList<>();
        currentExamIndex = 0;
        correctExamAnswers = 0;
        examMode = true;
        currentQuestion = examQuestions.get(currentExamIndex);
        displayQuestion(currentQuestion);
        updateFrameTitle("Quiz App - Tryb Egzaminu (" + (currentExamIndex + 1) + "/" + examQuestions.size() + ")");
    }

    private void displayQuestion(Question q) {
        // Usuń wszystkie komponenty z panelu pytania (w tym stary obrazek)
        JPanel questionImagePanel = (JPanel) ((JScrollPane) quizExamPanel.getComponent(0)).getViewport().getView();
        questionImagePanel.removeAll();

        // Dodaj label z pytaniem
        questionLabel = new JLabel("<html><body style='width: 500px'>" + q.questionText + "</body></html>", SwingConstants.CENTER);
        questionLabel.setForeground(FOREGROUND_COLOR);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 16));
        questionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        questionImagePanel.add(questionLabel);

        // Dodaj obrazek, jeśli istnieje
        if (q.imagePath != null && !q.imagePath.isEmpty()) {
            try {
                ImageIcon originalIcon = new ImageIcon(q.imagePath);
                // Skaluj obrazek, jeśli jest za duży (np. max szerokość 400px)
                Image image = originalIcon.getImage();
                int newWidth = image.getWidth(null);
                int newHeight = image.getHeight(null);
                
                int maxWidth = 400; // Maksymalna szerokość obrazka
                if (newWidth > maxWidth) {
                    newHeight = (newHeight * maxWidth) / newWidth;
                    newWidth = maxWidth;
                }
                
                Image scaledImage = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                ImageIcon scaledIcon = new ImageIcon(scaledImage);
                JLabel imageLabel = new JLabel(scaledIcon);
                imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT); // Wyśrodkuj obrazek
                questionImagePanel.add(Box.createRigidArea(new Dimension(0, 10))); // Mały odstęp
                questionImagePanel.add(imageLabel);
            } catch (Exception e) {
                System.err.println("Blad ladowania obrazka: " + q.imagePath + " - " + e.getMessage());
                e.printStackTrace();
                // Opcjonalnie: wyświetl placeholder lub komunikat o błędzie obrazka
            }
        }
        questionImagePanel.add(Box.createRigidArea(new Dimension(0, 10))); // Odstęp po pytaniu/obrazku

        // Odśwież panel pytania
        questionImagePanel.revalidate();
        questionImagePanel.repaint();

        for (int i = 0; i < answerBoxes.size(); i++) {
            if (i < q.answers.size()) {
                answerBoxes.get(i).setText((char) ('A' + i) + ". " + q.answers.get(i));
                answerBoxes.get(i).setVisible(true);
                answerBoxes.get(i).setSelected(false);
                styleCheckBox(answerBoxes.get(i));
            } else {
                answerBoxes.get(i).setVisible(false);
            }
        }
        nextButton.setText("Dalej");
    }

    private void cleanupTempImages() {
        if (tempImageDir != null && Files.exists(tempImageDir)) {
            try {
                try (java.util.stream.Stream<Path> stream = Files.walk(tempImageDir)) { // ZMIANA TUTAJ: var na java.util.stream.Stream<Path>
                    stream.sorted(Comparator.reverseOrder())
                          .map(Path::toFile)
                          .forEach(File::delete);
                }
                System.out.println("DEBUG Main: Usunieto tymczasowy katalog obrazkow: " + tempImageDir.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("DEBUG Main: Blad podczas usuwania tymczasowego katalogu obrazkow: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void checkAnswer() {
        List<Integer> selectedIndices = new ArrayList<>();
        for (int i = 0; i < answerBoxes.size(); i++) {
            if (answerBoxes.get(i).isSelected()) selectedIndices.add(i);
        }
        Set<Integer> correct = currentQuestion.correctAnswers;
        boolean isCorrect = new HashSet<>(selectedIndices).equals(correct);

        if (examMode) {
            if (isCorrect) correctExamAnswers++;
            examResults.add(new ExamEntry(currentQuestion, new ArrayList<>(selectedIndices), isCorrect));
            currentExamIndex++;
            if (currentExamIndex >= examQuestions.size()) {
                examMode = false;
                showExamSummary();
                return;
            }
            currentQuestion = examQuestions.get(currentExamIndex);
            displayQuestion(currentQuestion);
            updateFrameTitle("Quiz App - Tryb Egzaminu (" + (currentExamIndex + 1) + "/" + examQuestions.size() + ")");
        } else {
            StringBuilder result = new StringBuilder();
            result.append(isCorrect ? "DOBRZE!" : "ŹLE!").append("\nPoprawna odpowiedź: ");
            for (int i : correct) {
                result.append((char) ('A' + i)).append(", ");
            }
            if (!correct.isEmpty()) result.setLength(result.length() - 2);
            
            JOptionPane.showMessageDialog(frame, result.toString());
            showNextRandomQuestion();
        }
    }

    private void showExamSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Wynik egzaminu: ").append(correctExamAnswers).append(" / ").append(examQuestions.size())
                .append(" (").append(String.format("%.2f", (double) correctExamAnswers * 100 / examQuestions.size())).append("%)\n\n");

        for (int i = 0; i < examResults.size(); i++) {
            ExamEntry entry = examResults.get(i);
            summary.append("Pytanie ").append(i + 1).append(": ")
                    .append(entry.isCorrect ? "✔️ DOBRZE" : "❌ ŹLE").append("\n")
                    .append("Treść pytania: ").append(entry.question.questionText).append("\n");
            
            for (int j = 0; j < entry.question.answers.size(); j++) {
                char letter = (char) ('A' + j);
                String prefix;
                if (entry.selectedAnswers.contains(j)) {
                    prefix = "[X]";
                } else {
                    prefix = "[ ]";
                }
                
                String answerLine = prefix + " " + letter + ": " + entry.question.answers.get(j);
                if (entry.question.correctAnswers.contains(j)) {
                    answerLine += " (Poprawna)";
                }
                summary.append(answerLine).append("\n");
            }

            Set<Integer> correct = entry.question.correctAnswers;
            summary.append("Poprawne odpowiedzi: ");
            if (correct.isEmpty()) {
                summary.append("Brak");
            } else {
                for (int c : correct) summary.append((char) ('A' + c)).append(", ");
                summary.setLength(summary.length() - 2);
            }
            summary.append("\n\n");
        }

        JTextArea summaryTextArea = (JTextArea) ((JScrollPane) examSummaryPanel.getComponent(0)).getViewport().getView();
        summaryTextArea.setText(summary.toString());
        
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "ExamSummary");
        updateFrameTitle("Quiz App - Podsumowanie Egzaminu");
    }

    private void styleButton(JButton button) {
        button.setBackground(BUTTON_BACKGROUND_COLOR);
        button.setForeground(BUTTON_FOREGROUND_COLOR);
        button.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
    }

    private void styleCheckBox(JCheckBox checkBox) {
        checkBox.setBackground(BACKGROUND_COLOR);
        checkBox.setForeground(FOREGROUND_COLOR);
    }
}