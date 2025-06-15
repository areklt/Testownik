import javax.swing.*;

import javax.swing.border.Border;

import java.awt.*;

import java.awt.event.ComponentAdapter;

import java.awt.event.ComponentEvent;

import java.awt.event.ActionListener;   // <-- DODAJ TO

import java.awt.event.ActionEvent;    // <-- DODAJ TO

import java.io.*;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.*;

import java.util.List;

import java.util.zip.ZipEntry;

import java.util.zip.ZipInputStream;

import com.formdev.flatlaf.FlatDarculaLaf;



public class Main implements FileDrop.Listener {

    private JFrame frame;



    // Definicja kolorów dla ciemnego motywu

    private static final Color BACKGROUND_COLOR = new Color(40, 40, 40); // Nadal używane, OK

    private static final Color FOREGROUND_COLOR = new Color(240, 240, 240); // Nadal używane, OK

    private static final Color HIGHLIGHT_BORDER_COLOR = new Color(0, 120, 215);



    // Panele główne aplikacji

    private JPanel mainPanel;

    private JPanel mainMenuPanel;

    // Poniżej zmiany:

    private JPanel quizDisplayPanel; // Nowy główny panel dla quizu/egzaminu (do CardLayout)

    private JPanel quizExamContentPanel; // Właściwy panel z pytaniem, obrazkiem, odpowiedziami

    private JPanel examSummaryPanel;

    private JPanel answerPanel; // Panel na checkboxy odpowiedzi



    // Komponenty interfejsu quizu/egzaminu

    private JLabel questionLabel;

    private JLabel questionImageLabel; // DODANO: Etykieta do wyświetlania obrazka

    private List<JCheckBox> answerBoxes;

   

    // Przycisk "Dalej" (zmieniona nazwa z nextButton na nextQuestionButton dla czytelności)

    private JButton nextQuestionButton; // Zmieniono nazwę na bardziej opisową

   

    // Przycisk "Powrót do Menu" w trybie quizu/egzaminu

    private JButton returnToMenuButton; // DODANO: Przycisk strzałki powrotu



    // Referencje do przycisków w menu głównym

    private JButton quizButton;

    private JButton examButton;

    private JButton loadButton;

    // private JButton backToMenuButton; // USUNIĘTO: Ten przycisk jest teraz tylko w summary i quizu (returnToMenuButton)



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



    // Listener do sprawdzania odpowiedzi (dla checkboxów)

    private ActionListener answerCheckListener; // DODANO: Było używane, ale nie zadeklarowane



    // Stałe rozmiary dla przycisków w menu głównym (nie ruszamy)

    private static final int BUTTON_WIDTH = 500;

    private static final int BUTTON_HEIGHT = 100;



    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> new Main().createAndShowGUI());

    }



    private void createAndShowGUI() {

        try {

            FlatDarculaLaf.setup();

        } catch (Exception e) {

            System.err.println("Failed to initialize FlatLaf");

            e.printStackTrace();

        }



        frame = new JFrame("Quiz App");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setLayout(new BorderLayout());



        defaultMainPanelBorder = BorderFactory.createEmptyBorder(10, 10, 10, 10);

        highlightBorder = BorderFactory.createLineBorder(HIGHLIGHT_BORDER_COLOR, 3);



        mainPanel = new JPanel(new CardLayout());

        frame.add(mainPanel, BorderLayout.CENTER);



        createMainMenuPanel();

        mainPanel.add(mainMenuPanel, "MainMenu");



        createQuizExamPanel(); // Ta metoda zostanie zmieniona

        // WAŻNE: Dodajemy quizDisplayPanel, a nie quizExamPanel

        mainPanel.add(quizDisplayPanel, "QuizExam"); // Zmieniono z quizExamPanel na quizDisplayPanel



        createExamSummaryPanel();

        mainPanel.add(examSummaryPanel, "ExamSummary");



        CardLayout cl = (CardLayout)(mainPanel.getLayout());

        cl.show(mainPanel, "MainMenu");



        frame.setSize(800, 600);

        frame.setLocationRelativeTo(null);

        frame.setVisible(true);

       

        // --- DODANO: ComponentListener dla dynamicznego rozmiaru przycisku "Dalej" ---

        frame.addComponentListener(new ComponentAdapter() {

            @Override

            public void componentResized(ComponentEvent e) {

                if (nextQuestionButton != null) {

                    // Szerokość to połowa szerokości content pane (bez insets)

                    // Pobieramy width od głównego panelu, ktory jest centrujacy

                    int newWidth = frame.getContentPane().getWidth() / 2;

                    // Jeśli przycisk jest włożony w FlowLayout, setPreferredSize wystarczy

                    // Jeśli w BorderLayout, musimy to dopilnować min/max size

                    nextQuestionButton.setPreferredSize(new Dimension(newWidth, 60));

                    nextQuestionButton.setMinimumSize(new Dimension(newWidth, 60));

                    nextQuestionButton.setMaximumSize(new Dimension(newWidth, 60));



                    // Wymuś ponowne przeliczenie layoutu rodzica, aby zmiana była widoczna

                    if (nextQuestionButton.getParent() != null) {

                        nextQuestionButton.getParent().revalidate();

                        nextQuestionButton.getParent().repaint();

                    }

                }

            }

        });

        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupTempImages));

       

        new FileDrop(frame.getContentPane(), this);

    }



    private void createMainMenuPanel() {

        // #1 Inicjalizujemy panel tylko raz i ustawiamy mu GridBagLayout

        mainMenuPanel = new JPanel(new GridBagLayout());

        // mainMenuPanel.setBackground(BACKGROUND_COLOR); // Zakomentowane ze względu na FlatLaf



        // #2 Ustawiamy GridBagConstraints dla poszczególnych komponentów

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(10, 10, 10, 10); // Odstępy między komponentami

        gbc.fill = GridBagConstraints.HORIZONTAL; // Komponenty rozciągają się w poziomie

        gbc.gridx = 0; // Wszystkie komponenty w pierwszej (i jedynej) kolumnie

        gbc.weightx = 1.0; // Pozwala na rozciąganie się w poziomie i centr.

        gbc.anchor = GridBagConstraints.CENTER; // Domyślne wyrównanie w komórce do środka



        // #3 Tytuł

        JLabel titleLabel = new JLabel("Witaj w aplikacji Quiz!", SwingConstants.CENTER);

        titleLabel.setForeground(FOREGROUND_COLOR);

        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));

        gbc.gridy = 0; // Pierwszy wiersz

        // gbc.gridwidth = 1; // Już domyślnie jest 1, można pominąć

        mainMenuPanel.add(titleLabel, gbc);



        // #4 Informacja o przeciąganiu

        JLabel dragDropInfo = new JLabel("Przeciągnij pliki (.txt lub .zip) tutaj, aby wczytać pytania, lub użyj przycisku.", SwingConstants.CENTER);

        dragDropInfo.setForeground(FOREGROUND_COLOR);

        dragDropInfo.setFont(new Font("Arial", Font.PLAIN, 14));

        gbc.gridy = 1; // Drugi wiersz

        mainMenuPanel.add(dragDropInfo, gbc);



        // #5 Przycisk "Wczytaj pliki"

        loadButton = new JButton("Wczytaj pliki (.txt / .zip)");

        styleButton(loadButton); // StyleButton już ustawi preferowany rozmiar i usunie ramki

        // Usuwamy jawne ustawianie fontu i rozmiaru tutaj, bo to duplikuje styleButton

        // loadButton.setFont(new Font("Arial", Font.BOLD, 16));

        // loadButton.setPreferredSize(new Dimension(250, 50));

        loadButton.addActionListener(e -> loadQuestionsDialog());

        gbc.gridy = 2; // Trzeci wiersz

        gbc.fill = GridBagConstraints.NONE; // Nie rozciągamy przycisku, żeby zachować jego preferowany rozmiar

        mainMenuPanel.add(loadButton, gbc);



        // #6 Przycisk "Tryb Quizu"

        quizButton = new JButton("Tryb Quizu");

        styleButton(quizButton); // StyleButton już ustawi preferowany rozmiar i usunie ramki

        // Usuwamy jawne ustawianie fontu i rozmiaru tutaj

        // quizButton.setFont(new Font("Arial", Font.BOLD, 16));

        // quizButton.setPreferredSize(new Dimension(250, 50));

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

        gbc.gridy = 3; // Czwarty wiersz

        gbc.fill = GridBagConstraints.NONE; // Nie rozciągamy przycisku

        mainMenuPanel.add(quizButton, gbc);

        quizButton.setEnabled(false);



        // #7 Przycisk "Tryb Egzaminu"

        examButton = new JButton("Tryb Egzaminu (20 pytań)");

        styleButton(examButton); // StyleButton już ustawi preferowany rozmiar i usunie ramki

        // Usuwamy jawne ustawianie fontu i rozmiaru tutaj

        // examButton.setFont(new Font("Arial", Font.BOLD, 16));

        // examButton.setPreferredSize(new Dimension(250, 50));

        examButton.addActionListener(e -> {

            if (allQuestions == null || allQuestions.size() < 20) {

                JOptionPane.showMessageDialog(frame, "Za mało pytań do trybu egzaminu (wymagane minimum 20). Wczytaj więcej pytań.", "Brak pytań", JOptionPane.WARNING_MESSAGE);

                return;

            }

            showQuizExamPanel();

            startExamMode();

        });

        gbc.gridy = 4; // Piąty wiersz

        gbc.fill = GridBagConstraints.NONE; // Nie rozciągamy przycisku

        mainMenuPanel.add(examButton, gbc);

        examButton.setEnabled(false);

    }



    private void createQuizExamPanel() {

        // quizDisplayPanel to główny panel, który będzie dodany do CardLayout w mainPanel.

        // Będzie zawierał przycisk powrotu na górze i resztę quizu w centrum.

        quizDisplayPanel = new JPanel(new BorderLayout());



        // --- Przycisk POWROTU (strzałka w lewo w lewym górnym rogu) ---

        returnToMenuButton = new JButton("◀"); // Używamy znaku strzałki Unicode

        returnToMenuButton.setPreferredSize(new Dimension(45, 40)); // Stały rozmiar

        returnToMenuButton.setBorderPainted(false); // Bez ramki

        returnToMenuButton.setFocusPainted(false); // Bez obrysu focusu

        returnToMenuButton.setContentAreaFilled(false); // Przezroczysty

        returnToMenuButton.setForeground(UIManager.getColor("Label.foreground")); // Kolor z FlatLaf

        returnToMenuButton.setFont(new Font("Arial", Font.BOLD, 24)); // Odpowiedni rozmiar fontu

        returnToMenuButton.addActionListener(e -> {

            int confirm = JOptionPane.showConfirmDialog(frame,

                "Czy na pewno chcesz wrócić do menu głównego? Postępy w obecnym trybie zostaną utracone.",

                "Powrót do Menu",

                JOptionPane.YES_NO_OPTION,

                JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {

                showMainMenuPanel();

            }

        });



        // Panel do umieszczenia przycisku powrotu w lewym górnym rogu

        JPanel topLeftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)); // Margines 10px

        topLeftButtonPanel.setOpaque(false); // Ustaw panel na przezroczysty

        topLeftButtonPanel.add(returnToMenuButton);

        quizDisplayPanel.add(topLeftButtonPanel, BorderLayout.NORTH); // Dodajemy do górnej części quizDisplayPanel



        // --- quizExamContentPanel: Będzie zawierał pytanie, obrazek, odpowiedzi i przycisk "Dalej" ---

        // Ten panel będzie centralnym elementem quizDisplayPanel

        quizExamContentPanel = new JPanel(new BorderLayout()); // Zmieniono nazwę zmiennej

        // quizExamContentPanel.setBackground(UIManager.getColor("Panel.background")); // FlatLaf to obsłuży



        // --- Panel na pytanie i obrazek (wewnątrz quizExamContentPanel) ---

        // Używamy GridBagLayout, żeby pytanie i obrazek były dobrze rozmieszczone

        JPanel questionImageAndTextPanel = new JPanel(new GridBagLayout());

        // questionImageAndTextPanel.setBackground(UIManager.getColor("Panel.background")); // FlatLaf to obsłuży



        // questionLabel (tekst pytania)

        questionLabel = new JLabel("Pytanie będzie tutaj."); // Upewnij się, że questionLabel jest polem klasy

        questionLabel.setForeground(UIManager.getColor("Label.foreground"));

        questionLabel.setFont(new Font("Arial", Font.PLAIN, 18)); // Użyjemy większego fontu

        // Ustawienia dla JLabel pozwalające na zawijanie tekstu w HTML, jeśli jest bardzo długi

        questionLabel.setHorizontalAlignment(SwingConstants.LEFT);

        questionLabel.setVerticalAlignment(SwingConstants.TOP);

        questionLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10)); // Marginesy



        GridBagConstraints gbcQ = new GridBagConstraints();

        gbcQ.gridx = 0;

        gbcQ.gridy = 0;

        gbcQ.weightx = 1.0;

        gbcQ.weighty = 0.0; // Pytanie nie rozciąga się w pionie

        gbcQ.fill = GridBagConstraints.HORIZONTAL; // Wypełnia w poziomie

        gbcQ.anchor = GridBagConstraints.NORTHWEST; // Wyrównanie do góry-lewo

        gbcQ.insets = new Insets(0, 10, 5, 10); // Marginesy (góra, lewo, dół, prawo)

        questionImageAndTextPanel.add(questionLabel, gbcQ);



        // questionImageLabel (obrazek pytania)

        questionImageLabel = new JLabel(); // Upewnij się, że questionImageLabel jest polem klasy

        // questionImageLabel.setBackground(UIManager.getColor("Panel.background")); // FlatLaf



        gbcQ.gridy = 1;

        gbcQ.weighty = 1.0; // Obrazek może rozciągać się w pionie

        gbcQ.fill = GridBagConstraints.BOTH; // Wypełnia w obu kierunkach

        gbcQ.insets = new Insets(5, 10, 10, 10); // Marginesy

        questionImageAndTextPanel.add(questionImageLabel, gbcQ);



        // ScrollPane dla pytania i obrazka

        JScrollPane questionScrollPane = new JScrollPane(questionImageAndTextPanel);

        questionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        questionScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        questionScrollPane.setBorder(BorderFactory.createEmptyBorder()); // Usuń domyślne obramowanie

        // questionScrollPane.setBackground(UIManager.getColor("Panel.background")); // FlatLaf

        // questionScrollPane.getViewport().setBackground(UIManager.getColor("Panel.background")); // FlatLaf



        quizExamContentPanel.add(questionScrollPane, BorderLayout.NORTH); // Pytanie i obrazek na górze quizExamContentPanel



        // --- Panel na opcje odpowiedzi (wewnątrz quizExamContentPanel) ---

        answerPanel = new JPanel(); // Upewnij się, że answerPanel jest polem klasy

        answerPanel.setLayout(new BoxLayout(answerPanel, BoxLayout.Y_AXIS));

        answerPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20)); // Większe marginesy dla checkboxów

        // answerPanel.setBackground(UIManager.getColor("Panel.background")); // FlatLaf



        answerBoxes = new ArrayList<>(); // Upewnij się, że answerBoxes jest polem klasy

        for (int i = 0; i < 5; i++) {

            JCheckBox checkBox = new JCheckBox();

            styleCheckBox(checkBox); // Twoja metoda styleCheckBox

            checkBox.setFont(new Font("Arial", Font.PLAIN, 16)); // Większy font dla odpowiedzi

            answerBoxes.add(checkBox);

            answerPanel.add(checkBox);

        }

        quizExamContentPanel.add(answerPanel, BorderLayout.CENTER); // Opcje odpowiedzi w centrum quizExamContentPanel



        // --- Przycisk DALEJ (na środku, na samym dole, połowa szerokości) ---

        nextQuestionButton = new JButton("Dalej"); // Upewnij się, że nextQuestionButton jest polem klasy

        nextQuestionButton.setFont(new Font("Arial", Font.BOLD, 24)); // Duży font dla przycisku Dalej

        nextQuestionButton.setBackground(new Color(0, 100, 180)); // Niebieski kolor (FlatLaf to ładnie ostylizuje)

        nextQuestionButton.setForeground(new Color(255, 255, 255)); // Biały tekst

        nextQuestionButton.setBorderPainted(false); // Bez ramki

        nextQuestionButton.setFocusPainted(false); // Bez obrysu focusu

        nextQuestionButton.setContentAreaFilled(true); // Wypełnij obszar kolorem

        nextQuestionButton.addActionListener(e -> checkAnswer()); // Akcja sprawdzenia odpowiedzi



        // Panel do centrowania przycisku "Dalej" na dole

        JPanel nextButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        nextButtonPanel.setOpaque(false); // Przezroczysty

        nextButtonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0)); // Marginesy pionowe

        nextButtonPanel.add(nextQuestionButton);



        quizExamContentPanel.add(nextButtonPanel, BorderLayout.SOUTH); // Dodaj panel z przyciskiem Dalej na dole quizExamContentPanel



        // Dodaj quizExamContentPanel do głównego quizDisplayPanel w centrum

        quizDisplayPanel.add(quizExamContentPanel, BorderLayout.CENTER);



        // Listener do sprawdzania odpowiedzi (dla checkboxów, jeśli używasz)

        answerCheckListener = new ActionListener() {

            @Override

            public void actionPerformed(ActionEvent e) {

                updateQuestionNavigationButtons(); // Ta metoda wymaga aktualizacji

            }

        };

        // Dodaj answerCheckListener do każdego CheckBoxa

        for (JCheckBox cb : answerBoxes) {

            cb.addActionListener(answerCheckListener);

        }

    }



    private void createExamSummaryPanel() {

        examSummaryPanel = new JPanel(new BorderLayout(10, 10));

        // examSummaryPanel.setBackground(BACKGROUND_COLOR); // Zakomentowano, FlatLaf zajmie się tłem

        examSummaryPanel.setBorder(defaultMainPanelBorder); // Nadal używasz swojej ramki, więc zostawiamy



        JTextArea summaryTextArea = new JTextArea();

        summaryTextArea.setEditable(false);

        // summaryTextArea.setBackground(BACKGROUND_COLOR); // Zakomentowano

        // summaryTextArea.setForeground(FOREGROUND_COLOR); // Zakomentowano, FlatLaf ustawi kolor tekstu

        summaryTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Pozostawiamy dla spójności



        // --- Ustawienia zawijania tekstu (pozostają bez zmian) ---

        summaryTextArea.setLineWrap(true);

        summaryTextArea.setWrapStyleWord(true);

        // --- KONIEC ustawień zawijania tekstu ---



        JScrollPane scrollPane = new JScrollPane(summaryTextArea);

        // scrollPane.getViewport().setBackground(BACKGROUND_COLOR); // Zakomentowano

        // scrollPane.setBackground(BACKGROUND_COLOR); // Zakomentowano



        // --- Ukrywanie poziomego paska przewijania (pozostaje bez zmian) ---

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // --- KONIEC ukrywania poziomego paska przewijania ---



        examSummaryPanel.add(scrollPane, BorderLayout.CENTER);



        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

        // buttonsPanel.setBackground(BACKGROUND_COLOR); // Zakomentowano



        JButton backToMenuButton = new JButton("Powrót do Menu Głównego");

        styleButton(backToMenuButton); // Twoja metoda styleButton zajmie się stylem

        backToMenuButton.addActionListener(e -> showMainMenuPanel()); // Zmieniono na showMainMenu() dla spójności

        buttonsPanel.add(backToMenuButton);



        JButton retryExamButton = new JButton("Następny Egzamin");

        styleButton(retryExamButton); // Twoja metoda styleButton zajmie się stylem

        retryExamButton.addActionListener(e -> {

            if (examQuestions != null && !examQuestions.isEmpty()) {

                if (allQuestions.size() < 20) { // Sprawdzenie czy jest wystarczająco pytań

                    JOptionPane.showMessageDialog(frame, "Za mało pytań do trybu egzaminu (wymagane minimum 20). Wczytaj więcej pytań.", "Brak pytań", JOptionPane.WARNING_MESSAGE);

                    return;

                }

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

            } else if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif")) {

                // To jest plik obrazka, ignorujemy go cicho, bo QuestionParser go znajdzie.

                System.out.println("DEBUG Main: Ignorowano plik obrazka (oczekiwany zasob): " + file.getName());

            } else {

                // To jest inny nieobsługiwany typ pliku, o którym chcemy ostrzegać

                JOptionPane.showMessageDialog(frame, "Ignorowano nieobsługiwany typ pliku: " + file.getName(), "Nieobsługiwany typ pliku", JOptionPane.WARNING_MESSAGE);

                System.out.println("DEBUG Main: Ignorowano nieznany typ pliku: " + file.getName());

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

        // Ustaw tekst pytania

        // Użyj HTML, aby wymusić zawijanie tekstu i wyśrodkowanie

        questionLabel.setText("<html><body style='width: 95%; text-align: center;'>" + q.questionText + "</body></html>");

        // Wyrównanie w pionie dla questionLabel jest już ustawione w createQuizExamPanel (TOP)



        // Usuń stary obrazek, jeśli jakiś był

        questionImageLabel.setIcon(null); // Usunięcie poprzedniego obrazka

        questionImageLabel.setText(""); // Upewnij się, że nie ma starego tekstu



        // Dodaj obrazek, jeśli istnieje

        if (q.imagePath != null && !q.imagePath.isEmpty()) {

            File imageFile = new File(q.imagePath);

            // Sprawdź, czy obrazek istnieje fizycznie

            if (imageFile.exists()) {

                try {

                    ImageIcon originalIcon = new ImageIcon(q.imagePath);

                    Image image = originalIcon.getImage();



                    // Ustaw maksymalną szerokość i wysokość dla obrazka

                    // Pobieramy szerokość panelu, w którym znajduje się obrazek

                    // Zakładamy, że quizDisplayPanel jest już dodany do ramki

                    // A questionImageLabel jest w quizExamContentPanel, który jest w quizDisplayPanel

                    // Użyjemy frame.getWidth() i frame.getHeight() jako punkt odniesienia,

                    // a następnie skalujemy na ich podstawie, by uniknąć problemów z layoutem przed validacją



                    // Dostosowujemy wartości procentowe do faktycznej przestrzeni, która będzie dostępna

                    // Zauważ, że BorderLayout NORTH (dla strzałki) zabiera trochę miejsca z góry

                    // i BorderLayout SOUTH (dla przycisku Dalej) zabiera trochę z dołu.

                    // Trzeba to brać pod uwagę przy skalowaniu.



                    // Przybliżone dostępne wymiary dla obszaru z pytaniem i obrazkiem

                    // (można je doprecyzować, mierząc faktyczne rozmiary komponentów po ich ułożeniu)

                    int availableWidth = (int) (frame.getWidth() * 0.9); // 90% szerokości ramki (uwzględniając marginesy)

                    int availableHeight = (int) (frame.getHeight() * 0.5); // 50% wysokości ramki dla pytania+obrazka



                    int newWidth = image.getWidth(null);

                    int newHeight = image.getHeight(null);



                    // Skaluj obrazek, zachowując proporcje

                    if (newWidth > availableWidth || newHeight > availableHeight) {

                        double scaleX = (double) availableWidth / newWidth;

                        double scaleY = (double) availableHeight / newHeight;

                        double scale = Math.min(scaleX, scaleY); // Weź mniejszy współczynnik, by zmieścić w obu wymiarach



                        newWidth = (int) (newWidth * scale);

                        newHeight = (int) (newHeight * scale);

                    }



                    Image scaledImage = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);

                    ImageIcon scaledIcon = new ImageIcon(scaledImage);

                    questionImageLabel.setIcon(scaledIcon);

                    // questionImageLabel.setAlignmentX(Component.CENTER_ALIGNMENT); // Wyrównanie jest obsługiwane przez GridBagLayout

                } catch (Exception e) {

                    System.err.println("Błąd ładowania obrazka: " + q.imagePath + " - " + e.getMessage());

                    e.printStackTrace();

                    questionImageLabel.setText("Błąd ładowania obrazka");

                    questionImageLabel.setFont(new Font("Arial", Font.ITALIC, 12));

                    questionImageLabel.setForeground(Color.RED);

                }

            } else {

                 System.err.println("Obrazek nie istnieje: " + q.imagePath);

                 questionImageLabel.setText("Obrazek nie znaleziono");

                 questionImageLabel.setFont(new Font("Arial", Font.ITALIC, 12));

                 questionImageLabel.setForeground(Color.ORANGE);

            }

        }



        // Odśwież panel pytania i obrazka

        // Potrzebujemy uzyskać referencję do questionImageAndTextPanel, który jest widokiem w scrollPane

        // Pamiętaj, że quizExamContentPanel.getComponent(0) to questionScrollPane

        // a getView() zwraca widok z viewportu.

        JScrollPane questionScrollPane = (JScrollPane) quizExamContentPanel.getComponent(0);

        JPanel questionImageAndTextPanel = (JPanel) questionScrollPane.getViewport().getView();



        questionImageAndTextPanel.revalidate();

        questionImageAndTextPanel.repaint();



        // Ustaw tekst i widoczność dla checkboxów odpowiedzi

        for (int i = 0; i < answerBoxes.size(); i++) {

            if (i < q.answers.size()) {

                answerBoxes.get(i).setText((char) ('A' + i) + ". " + q.answers.get(i));

                answerBoxes.get(i).setVisible(true);

                answerBoxes.get(i).setSelected(false);

                // stylCheckBox(answerBoxes.get(i)); // Zwykle nie musisz tego wywoływać przy każdym odświeżeniu, chyba że styl ma się zmieniać

            } else {

                answerBoxes.get(i).setVisible(false);

            }

        }

        // Upewnij się, że nextQuestionButton ma poprawny tekst (jeśli zmieniasz go w checkAnswer)

        nextQuestionButton.setText("Dalej");

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

        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));

        button.setMinimumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT)); // Upewnij się, że nie będzie mniejszy

        button.setMaximumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT)); // Upewnij się, że nie będzie większy

        button.setHorizontalAlignment(SwingConstants.CENTER);

        button.setVerticalAlignment(SwingConstants.CENTER);

        button.setFont(new Font("Arial", Font.BOLD, 20));

        // Usuń malowanie ramki przycisku

        button.setBorderPainted(false); // To usunie główną ramkę

        button.setFocusPainted(false);  // To usunie ramkę pojawiającą się po focusie/kliknięciu

        button.setBackground(UIManager.getColor("Panel.background"));

        button.setForeground(UIManager.getColor("Panel.foreground"));

        button.setContentAreaFilled(true);

    }



    private void styleCheckBox(JCheckBox checkBox) {

        checkBox.setBackground(BACKGROUND_COLOR);

        checkBox.setForeground(FOREGROUND_COLOR);

    }

    private void updateQuestionNavigationButtons() {

        // Ta metoda jest wywoływana, gdy użytkownik zaznaczy/odznaczy checkbox.

        // W trybie quizu (losowe pytania), możemy aktywować przycisk "Dalej"

        // dopiero po wybraniu jakiejś odpowiedzi.

        // W trybie egzaminu, "Dalej" jest zawsze aktywny, bo sprawdzenie następuje po kliknięciu.



        if (!examMode) {

            boolean anyAnswerSelected = false;

            for (JCheckBox cb : answerBoxes) {

                if (cb.isSelected()) {

                    anyAnswerSelected = true;

                    break;

                }

            }

            nextQuestionButton.setEnabled(anyAnswerSelected);

        } else {

            // W trybie egzaminu przycisk "Dalej" powinien być zawsze aktywny,

            // ponieważ użytkownik może nie wybrać żadnej odpowiedzi i przejść dalej.

            nextQuestionButton.setEnabled(true);

        }

    }

}
