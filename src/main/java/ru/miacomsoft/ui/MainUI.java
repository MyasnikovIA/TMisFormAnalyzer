package ru.miacomsoft.ui;

import ru.miacomsoft.model.AnalysisConfig;
import ru.miacomsoft.model.ReportConfig;
import ru.miacomsoft.model.SettingsModel;
import ru.miacomsoft.service.TmisFormAnalyzerService;
import ru.miacomsoft.service.ViewDependencyAnalyzer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainUI extends JFrame {

    private SettingsModel settings;
    private ExecutorService executorService;
    private Future<?> currentTask;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean stopRequested = new AtomicBoolean(false);

    // UI Components
    private JTextArea formsListTextArea;
    private JTextArea logTextArea;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton startButton;
    private JButton stopButton;
    private JButton settingsButton;
    private JLabel formsCountLabel;

    // Configuration
    private static final String USER_FORMS_LIST_FILE = "forms_list.txt";
    private static final String UI_STATE_FILE = "ui_state.properties";

    public MainUI() {
        settings = new SettingsModel();
        executorService = Executors.newSingleThreadExecutor();
        initUI();
        loadFormsListFromFile();
        loadUIState();

        // Убедимся, что AnalysisConfig использует правильный файл
        AnalysisConfig.setFormsListFile(USER_FORMS_LIST_FILE);

        // Add shutdown hook to save state on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> saveAllSettings()));
    }

    private void initUI() {
        setTitle("T-MIS Form Analyzer - Анализатор форм");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel with buttons
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel with split pane
        JSplitPane centerSplitPane = createCenterPanel();
        mainPanel.add(centerSplitPane, BorderLayout.CENTER);

        // Bottom panel with progress bar
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Load saved settings
        loadLogSettings();

        // Добавляем сохранение настроек при закрытии окна
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                saveAllSettings();
            }
        });
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));

        // Left side - title
        JLabel titleLabel = new JLabel("Анализатор форм T-MIS (M2/D3)");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        topPanel.add(titleLabel, BorderLayout.WEST);

        // Right side - buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        JButton saveListButton = new JButton("Сохранить список");
        saveListButton.setBackground(new Color(33, 150, 243));
        saveListButton.addActionListener(e -> saveFormsListToFile());

        settingsButton = new JButton("Настройки");
        settingsButton.addActionListener(e -> openSettingsDialog());

        startButton = new JButton("Запуск анализа");
        startButton.setBackground(new Color(76, 175, 80));
        startButton.addActionListener(e -> startAnalysis());

        stopButton = new JButton("Остановка");
        stopButton.setEnabled(false);
        stopButton.setBackground(new Color(244, 67, 54));
        stopButton.addActionListener(e -> stopAnalysis());

        buttonPanel.add(saveListButton);
        buttonPanel.add(settingsButton);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        topPanel.add(buttonPanel, BorderLayout.EAST);

        return topPanel;
    }

    private JSplitPane createCenterPanel() {
        // Left panel - Forms list
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(new TitledBorder("Список форм для анализа"));

        formsListTextArea = new JTextArea();
        formsListTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        formsListTextArea.setToolTipText("Введите пути форм (каждая с новой строки). Пример:\n#examples/barcode\nARMMainDoc/arm_director\n\nЕсли список пуст, будет проанализирован весь проект");

        // Add listener to auto-save when text changes (with debounce)
        formsListTextArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private Timer timer;
            {
                timer = new Timer(2000, e -> saveFormsListToFile());
                timer.setRepeats(false);
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleSave(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleSave(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleSave(); }

            private void scheduleSave() {
                timer.restart();
                updateFormCount();
            }
        });

        JScrollPane formsScrollPane = new JScrollPane(formsListTextArea);
        formsScrollPane.setPreferredSize(new Dimension(400, 0));
        leftPanel.add(formsScrollPane, BorderLayout.CENTER);

        // Hint label with form count
        JPanel hintPanel = new JPanel(new BorderLayout());
        JLabel hintLabel = new JLabel("<html><i>Подсказка: каждая форма с новой строки. Пустой список = весь проект</i></html>");
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(new Font("Arial", Font.ITALIC, 10));

        formsCountLabel = new JLabel("Форм: 0");
        formsCountLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        formsCountLabel.setForeground(Color.BLUE);

        hintPanel.add(hintLabel, BorderLayout.WEST);
        hintPanel.add(formsCountLabel, BorderLayout.EAST);
        leftPanel.add(hintPanel, BorderLayout.SOUTH);

        // Right panel - Log
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(new TitledBorder("Лог процесса анализа"));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logTextArea.setBackground(new Color(30, 30, 30));
        logTextArea.setForeground(new Color(0, 255, 0));

        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        rightPanel.add(logScrollPane, BorderLayout.CENTER);

        // Log control panel
        JPanel logControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearLogButton = new JButton("Очистить лог");
        clearLogButton.addActionListener(e -> logTextArea.setText(""));

        JButton saveLogButton = new JButton("Сохранить лог");
        saveLogButton.addActionListener(e -> saveLogToFile());

        logControlPanel.add(saveLogButton);
        logControlPanel.add(clearLogButton);
        rightPanel.add(logControlPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.3);

        return splitPane;
    }

    /**
     * Обновить счетчик форм в списке
     */
    private void updateFormCount() {
        String text = formsListTextArea.getText();
        String[] lines = text.split("\\r?\\n");
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                count++;
            }
        }
        formsCountLabel.setText("Форм: " + count + (count == 0 ? " (весь проект)" : ""));
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 5));
        bottomPanel.setBorder(new TitledBorder("Прогресс анализа"));

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Готов к работе");

        statusLabel = new JLabel("Статус: Ожидание");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 11));

        bottomPanel.add(progressBar, BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.EAST);

        return bottomPanel;
    }

    private void openSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, settings);
        dialog.setVisible(true);

        // Update connection settings in analyzer
        ViewDependencyAnalyzer.setOracleConfig(
                settings.getOracleUrl(),
                settings.getOracleUser(),
                settings.getOraclePassword()
        );
        ViewDependencyAnalyzer.setPostgresConfig(
                settings.getPostgresUrl(),
                settings.getPostgresUser(),
                settings.getPostgresPassword()
        );

        // Save settings immediately
        settings.saveSettings();
    }

    private void startAnalysis() {
        if (isRunning.get()) {
            appendLog("Анализ уже выполняется!");
            return;
        }

        // Сбрасываем флаг остановки
        stopRequested.set(false);

        try {
            String outputDir = settings.getOutputDir();
            if (outputDir == null || outputDir.trim().isEmpty()) {
                outputDir = "SQL_info";
            }
            File reportFile = new File(outputDir, "forms_report.txt");
            if (reportFile.exists()) {
                boolean deleted = reportFile.delete();
                if (deleted) {
                    appendLog("Удален старый файл отчета: " + reportFile.getPath());
                }
            }
        } catch (Exception e) {
            appendLog("Ошибка при удалении старого отчета: " + e.getMessage());
        }

        // Сохраняем список форм в файл
        saveFormsListToFile();

        // Обновляем настройку AnalysisConfig
        String formsListText = formsListTextArea.getText().trim();
        boolean hasForms = false;
        String[] lines = formsListText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                hasForms = true;
                break;
            }
        }

        AnalysisConfig.setScanAllFormsIfListEmpty(!hasForms);

        appendLog("Режим анализа: " + (hasForms ? "Только указанные формы" : "Весь проект"));
        if (hasForms) {
            appendLog("Форм в списке: " + getNonEmptyLinesCount(formsListText));
        }

        // Update UI state
        isRunning.set(true);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        settingsButton.setEnabled(false);
        formsListTextArea.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("Запуск анализа...");
        statusLabel.setText("Статус: Анализ запущен");

        // Start analysis in background thread
        currentTask = executorService.submit(() -> {
            try {
                runAnalysis();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("ОШИБКА: " + e.getMessage());
                    e.printStackTrace();
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    isRunning.set(false);
                    stopRequested.set(false);
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    settingsButton.setEnabled(true);
                    formsListTextArea.setEnabled(true);
                    progressBar.setString("Анализ завершен");
                    statusLabel.setText("Статус: Готов");
                });
            }
        });
    }

    private void stopAnalysis() {
        if (currentTask != null && !currentTask.isDone()) {
            appendLog("Запрос на остановку анализа...");

            // Устанавливаем флаг остановки для внутренних проверок
            stopRequested.set(true);

            // Прерываем поток
            boolean cancelled = currentTask.cancel(true);

            if (cancelled) {
                appendLog("Отправлен сигнал остановки...");
            } else {
                appendLog("Не удалось отправить сигнал остановки...");
            }

            stopButton.setEnabled(false);
            statusLabel.setText("Статус: Остановка...");
        }
    }

    private int getNonEmptyLinesCount(String text) {
        String[] lines = text.split("\\r?\\n");
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                count++;
            }
        }
        return count;
    }

    private void runAnalysis() {
        try {
            // Проверка на остановку перед началом
            if (stopRequested.get() || Thread.currentThread().isInterrupted()) {
                appendLog("Анализ был остановлен перед запуском");
                return;
            }

            String projectPath = settings.getProjectPath();
            Path projectPathObj = Paths.get(projectPath);

            if (!Files.exists(projectPathObj)) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("ОШИБКА: Путь к проекту не существует: " + projectPath);
                    JOptionPane.showMessageDialog(this,
                            "Путь к проекту не существует: " + projectPath,
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                });
                return;
            }

            appendLog("=".repeat(80));
            appendLog("=== АНАЛИЗАТОР ФОРМ T-MIS (M2/D3) ===");
            appendLog("=".repeat(80));
            appendLog("Корневой каталог проекта: " + projectPath);
            appendLog("Каталог для отчетов: " + settings.getOutputDir());
            appendLog("Файл списка форм: " + USER_FORMS_LIST_FILE);
            appendLog("");

            // Перенаправляем вывод в лог
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;

            PipedInputStream pipedOut = new PipedInputStream();
            PipedOutputStream pipeOut = new PipedOutputStream();
            pipedOut.connect(pipeOut);

            PrintStream customOut = new PrintStream(pipeOut);
            System.setOut(customOut);
            System.setErr(customOut);

            Thread logReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(pipedOut))) {
                    String line;
                    while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                        final String finalLine = line;
                        SwingUtilities.invokeLater(() -> appendLog(finalLine));
                    }
                } catch (IOException e) {
                    // Stream closed
                }
            });
            logReader.start();

            TmisFormAnalyzerService analyzerService = new TmisFormAnalyzerService(settings);

            // Устанавливаем флаг остановки в сервис
            analyzerService.setStopRequested(() -> stopRequested.get());

            // Set progress callback
            analyzerService.setProgressCallback((processed, total, currentForm) -> {
                SwingUtilities.invokeLater(() -> {
                    int percent = total > 0 ? (processed * 100 / total) : 0;
                    progressBar.setValue(percent);
                    progressBar.setString(String.format("Обработано %d из %d форм (%.1f%%)",
                            processed, total, (double) percent));
                    statusLabel.setText("Статус: " + currentForm);
                });
            });

            analyzerService.runFullAnalysis();

            logReader.interrupt();
            System.setOut(originalOut);
            System.setErr(originalErr);

            if (!stopRequested.get()) {
                appendLog("");
                appendLog("=".repeat(80));
                appendLog("=== АНАЛИЗ ЗАВЕРШЕН ===");
                appendLog("=".repeat(80));
                appendLog("Результаты сохранены в директории: " + settings.getOutputDir() + "/");

                // Выводим статистику кэша
                appendLog("");
                appendLog("=== СТАТИСТИКА КЭША ВЬЮХ ===");
                appendLog(ViewDependencyAnalyzer.getCacheStats());
                appendLog("Размер кэша: " + ViewDependencyAnalyzer.getCacheSize() + " вьюх");

                SwingUtilities.invokeLater(() -> {
                    int result = JOptionPane.showConfirmDialog(this,
                            "Анализ завершен успешно!\nОткрыть папку с отчетами?",
                            "Анализ завершен",
                            JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        try {
                            Desktop.getDesktop().open(new File(settings.getOutputDir()));
                        } catch (IOException e) {
                            appendLog("Не удалось открыть папку: " + e.getMessage());
                        }
                    }
                });
            } else {
                appendLog("");
                appendLog("=".repeat(80));
                appendLog("=== АНАЛИЗ ОСТАНОВЛЕН ПОЛЬЗОВАТЕЛЕМ ===");
                appendLog("=".repeat(80));
            }
        } catch (Exception e) {
            if (!(e instanceof InterruptedException) && !stopRequested.get()) {
                appendLog("ОШИБКА: " + e.getMessage());
                e.printStackTrace();
            } else {
                appendLog("Анализ остановлен пользователем");
            }
        }
    }

    /**
     * Добавить сообщение в лог
     */
    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + message + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    /**
     * Сохранить лог в файл
     */
    private void saveLogToFile() {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "analysis_log_" + timestamp + ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(logTextArea.getText());
            appendLog("Лог сохранен в файл: " + filename);
            JOptionPane.showMessageDialog(this,
                    "Лог сохранен в файл: " + filename,
                    "Успешно", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            appendLog("Ошибка сохранения лога: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Ошибка сохранения лога: " + e.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Сохранить список форм в файл
     */
    private void saveFormsListToFile() {
        String text = formsListTextArea.getText();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FORMS_LIST_FILE))) {
            writer.write(text);
        } catch (IOException e) {
            appendLog("Ошибка сохранения списка форм: " + e.getMessage());
        }
    }

    /**
     * Загрузить список форм из файла
     */
    private void loadFormsListFromFile() {
        File file = new File(USER_FORMS_LIST_FILE);
        if (file.exists()) {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                formsListTextArea.setText(content.toString());
                updateFormCount();
                appendLog("Загружен список форм из " + USER_FORMS_LIST_FILE);
            } catch (IOException e) {
                appendLog("Ошибка загрузки списка форм: " + e.getMessage());
            }
        } else {
            updateFormCount();
            appendLog("Файл " + USER_FORMS_LIST_FILE + " не найден. Будет создан при сохранении.");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FORMS_LIST_FILE))) {
                writer.write("# Примеры форматов путей:\n");
                writer.write("# #examples/barcode\n");
                writer.write("# ARMMainDoc/arm_director\n");
                writer.write("# /Forms/ARMMainDoc/arm_director.frm\n");
                writer.write("#\n");
                writer.write("# Раскомментируйте нужные строки или добавьте свои\n");
            } catch (IOException e) {
                appendLog("Ошибка создания файла списка форм: " + e.getMessage());
            }
        }
    }

    /**
     * Сохранить состояние UI
     */
    private void saveUIState() {
        Properties props = new Properties();

        // Сохраняем позицию разделителя
        Component centerPanel = getContentPane().getComponent(0);
        if (centerPanel instanceof JPanel) {
            JPanel mainPanel = (JPanel) centerPanel;
            Component center = ((BorderLayout) mainPanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            if (center instanceof JSplitPane) {
                JSplitPane splitPane = (JSplitPane) center;
                props.setProperty("divider_location", String.valueOf(splitPane.getDividerLocation()));
            }
        }

        // Сохраняем размер и позицию окна
        props.setProperty("window_width", String.valueOf(getWidth()));
        props.setProperty("window_height", String.valueOf(getHeight()));
        props.setProperty("window_x", String.valueOf(getX()));
        props.setProperty("window_y", String.valueOf(getY()));
        props.setProperty("window_extended_state", String.valueOf(getExtendedState()));

        try (FileOutputStream out = new FileOutputStream(UI_STATE_FILE)) {
            props.store(out, "UI State");
        } catch (IOException e) {
            System.err.println("Ошибка сохранения состояния UI: " + e.getMessage());
        }
    }

    /**
     * Загрузить состояние UI
     */
    private void loadUIState() {
        File file = new File(UI_STATE_FILE);
        if (!file.exists()) return;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);

            // Восстанавливаем размер и позицию окна
            try {
                int width = Integer.parseInt(props.getProperty("window_width", "1200"));
                int height = Integer.parseInt(props.getProperty("window_height", "800"));
                int x = Integer.parseInt(props.getProperty("window_x", "100"));
                int y = Integer.parseInt(props.getProperty("window_y", "100"));
                setBounds(x, y, width, height);

                int extendedState = Integer.parseInt(props.getProperty("window_extended_state", "0"));
                setExtendedState(extendedState);
            } catch (NumberFormatException e) {
                // Используем значения по умолчанию
            }

            // Восстанавливаем позицию разделителя после отображения окна
            SwingUtilities.invokeLater(() -> {
                Component centerPanel = getContentPane().getComponent(0);
                if (centerPanel instanceof JPanel) {
                    JPanel mainPanel = (JPanel) centerPanel;
                    Component center = ((BorderLayout) mainPanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
                    if (center instanceof JSplitPane) {
                        JSplitPane splitPane = (JSplitPane) center;
                        try {
                            int dividerLocation = Integer.parseInt(props.getProperty("divider_location", "400"));
                            splitPane.setDividerLocation(dividerLocation);
                        } catch (NumberFormatException e) {
                            // Используем значение по умолчанию
                        }
                    }
                }
            });

        } catch (IOException e) {
            System.err.println("Ошибка загрузки состояния UI: " + e.getMessage());
        }
    }

    /**
     * Сохранить все настройки приложения
     */
    private void saveAllSettings() {
        // Сохраняем настройки отчета через SettingsModel
        settings.setIncludeSqlContent(ReportConfig.isIncludeSqlContent());
        settings.setIncludeJsForms(ReportConfig.isIncludeJsForms());
        settings.setIncludeTablesViews(ReportConfig.isIncludeTablesViews());
        settings.setIncludeViewTables(ReportConfig.isIncludeViewTables());
        settings.setIncludeJsUnitCompositions(ReportConfig.isIncludeJsUnitCompositions());
        settings.setIncludeViewDetails(ReportConfig.isIncludeViewDetails());

        // Сохраняем все настройки в файл
        settings.saveSettings();

        // Сохраняем список форм
        saveFormsListToFile();

        // Сохраняем состояние UI
        saveUIState();

        System.out.println("Настройки сохранены при закрытии приложения");
    }

    /**
     * Загрузить настройки отчета при запуске
     */
    private void loadLogSettings() {
        appendLog("Анализатор форм T-MIS v1.0");
        appendLog("=".repeat(60));
        appendLog("Настройки загружены:");
        appendLog("  - Проект: " + settings.getProjectPath());
        appendLog("  - Отчеты: " + settings.getOutputDir());
        appendLog("  - Oracle: " + settings.getOracleUrl());
        appendLog("  - PostgreSQL: " + settings.getPostgresUrl());
        appendLog("  - Файл списка форм: " + USER_FORMS_LIST_FILE);
        appendLog("");
        appendLog("Настройки отчета:");
        appendLog("  - SQL содержимое: " + (settings.isIncludeSqlContent() ? "ВКЛ" : "ВЫКЛ"));
        appendLog("  - JS формы: " + (settings.isIncludeJsForms() ? "ВКЛ" : "ВЫКЛ"));
        appendLog("  - Таблицы/вьюхи: " + (settings.isIncludeTablesViews() ? "ВКЛ" : "ВЫКЛ"));
        appendLog("  - Таблицы через вьюхи: " + (settings.isIncludeViewTables() ? "ВКЛ" : "ВЫКЛ"));
        appendLog("  - Композиции JS: " + (settings.isIncludeJsUnitCompositions() ? "ВКЛ" : "ВЫКЛ"));
        appendLog("  - Детали вьюх: " + (settings.isIncludeViewDetails() ? "ВКЛ" : "ВЫКЛ"));
        appendLog("");
        appendLog("Функционал:");
        appendLog("  ✓ Список форм автоматически сохраняется при изменении");
        appendLog("  ✓ Настройки подключений сохраняются в analyzer_settings.properties");
        appendLog("  ✓ Настройки отчета сохраняются в analyzer_settings.properties");
        appendLog("  ✓ Позиция и размер окна сохраняются между запусками");
        appendLog("  ✓ При пустом списке анализируется весь проект");
        appendLog("  ✓ Для остановки анализа нажмите кнопку 'Остановка'");
        appendLog("");
        appendLog("Готов к работе. Нажмите 'Запуск анализа' для начала.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new MainUI().setVisible(true);
        });
    }
}