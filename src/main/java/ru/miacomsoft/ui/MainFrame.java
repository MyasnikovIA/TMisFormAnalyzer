package ru.miacomsoft.ui;

import ru.miacomsoft.model.TableViewInfo;
import ru.miacomsoft.service.FileScannerService;
import ru.miacomsoft.service.ReportGeneratorService;
import ru.miacomsoft.service.TmisFormAnalyzerService;
import ru.miacomsoft.model.FormsListModel;
import ru.miacomsoft.model.SettingsModel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainFrame extends JFrame {
    private final SettingsModel settings;
    private final FormsListModel formsListModel;

    private JTextArea formsTextArea;
    private JTextArea logTextArea;
    private JProgressBar mainProgressBar;
    private JProgressBar subProgressBar;
    private JButton startButton;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton loadButton;
    private JButton clearButton;
    private JButton settingsButton;
    private JLabel statusLabel;
    private JLabel subStatusLabel;
    private JButton openOutputDirButton;  // Новая кнопка

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile boolean isCancelled = false;
    private ExecutorService executorService;
    private Future<?> currentTask;

    // Статистика
    private int processedForms = 0;
    private int totalForms = 0;
    private int successCount = 0;
    private int failCount = 0;

    public MainFrame() {
        settings = new SettingsModel();
        formsListModel = new FormsListModel();

        setTitle("TMIS Form Analyzer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        initComponents();
        loadFormsList();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Панель инструментов
        JPanel toolbarPanel = createToolbarPanel();
        add(toolbarPanel, BorderLayout.NORTH);

        // Центральная панель с разделителями
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplitPane.setResizeWeight(0.6);

        // Панель со списком форм
        JPanel formsPanel = createFormsPanel();

        // Панель с логами
        JPanel logPanel = createLogPanel();

        mainSplitPane.setTopComponent(formsPanel);
        mainSplitPane.setBottomComponent(logPanel);

        add(mainSplitPane, BorderLayout.CENTER);

        // Нижняя панель с прогрессом
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createToolbarPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        startButton = new JButton("Старт");
        pauseButton = new JButton("Пауза");
        stopButton = new JButton("Стоп");
        loadButton = new JButton("Загрузить из файла");
        clearButton = new JButton("Очистить список");
        settingsButton = new JButton("Настройки");
        openOutputDirButton = new JButton("Открыть каталог отчетов");

        pauseButton.setEnabled(false);
        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startAnalysis());
        pauseButton.addActionListener(e -> pauseAnalysis());
        stopButton.addActionListener(e -> stopAnalysis());
        loadButton.addActionListener(e -> loadFromFile());
        clearButton.addActionListener(e -> clearFormsList());
        settingsButton.addActionListener(e -> openSettings());
        openOutputDirButton.addActionListener(e -> openOutputDirectory());

        panel.add(startButton);
        panel.add(pauseButton);
        panel.add(stopButton);
        panel.add(loadButton);
        panel.add(clearButton);
        panel.add(settingsButton);
        panel.add(openOutputDirButton);

        return panel;
    }

    private JPanel createFormsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Список форм для анализа"));

        formsTextArea = new JTextArea();
        formsTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(formsTextArea);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
    /**
     * Открыть каталог с отчетами в файловом менеджере
     */
    private void openOutputDirectory() {
        String outputDir = settings.getOutputDir();
        if (outputDir == null || outputDir.trim().isEmpty()) {
            outputDir = "SQL_info";
        }

        File dir = new File(outputDir);

        // Если директория не существует, создаём её
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                appendLog("Создана директория: " + dir.getAbsolutePath());
            } else {
                appendLog("Не удалось создать директорию: " + dir.getAbsolutePath());
                JOptionPane.showMessageDialog(this,
                        "Не удалось создать директорию: " + dir.getAbsolutePath(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // Открываем директорию в файловом менеджере
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
                appendLog("Открыта директория: " + dir.getAbsolutePath());
            } else {
                // Для систем без Desktop support (например, серверные ОС)
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder processBuilder = null;

                if (os.contains("win")) {
                    processBuilder = new ProcessBuilder("explorer", dir.getAbsolutePath());
                } else if (os.contains("mac")) {
                    processBuilder = new ProcessBuilder("open", dir.getAbsolutePath());
                } else {
                    processBuilder = new ProcessBuilder("xdg-open", dir.getAbsolutePath());
                }

                if (processBuilder != null) {
                    processBuilder.start();
                    appendLog("Открыта директория: " + dir.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            appendLog("Ошибка при открытии директории: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Не удалось открыть директорию: " + e.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }


    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Лог процесса"));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logTextArea);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Основной прогресс-бар
        JPanel mainProgressPanel = new JPanel(new BorderLayout(5, 0));
        statusLabel = new JLabel("Готов к работе");
        mainProgressBar = new JProgressBar();
        mainProgressBar.setStringPainted(true);
        mainProgressBar.setMinimum(0);
        mainProgressBar.setValue(0);
        mainProgressPanel.add(statusLabel, BorderLayout.WEST);
        mainProgressPanel.add(mainProgressBar, BorderLayout.CENTER);

        // Дополнительный прогресс-бар для подпроцессов
        JPanel subProgressPanel = new JPanel(new BorderLayout(5, 0));
        subStatusLabel = new JLabel("");
        subProgressBar = new JProgressBar();
        subProgressBar.setStringPainted(true);
        subProgressBar.setMinimum(0);
        subProgressBar.setValue(0);
        subProgressBar.setVisible(false);
        subProgressPanel.add(subStatusLabel, BorderLayout.WEST);
        subProgressPanel.add(subProgressBar, BorderLayout.CENTER);

        JPanel progressPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        progressPanel.add(mainProgressPanel);
        progressPanel.add(subProgressPanel);

        panel.add(progressPanel, BorderLayout.CENTER);

        return panel;
    }

    private void loadFormsList() {
        StringBuilder sb = new StringBuilder();
        for (String form : formsListModel.getForms()) {
            sb.append(form).append("\n");
        }
        formsTextArea.setText(sb.toString());
    }

    private void saveFormsList() {
        String[] lines = formsTextArea.getText().split("\\r?\\n");
        List<String> forms = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                forms.add(line);
            }
        }
        formsListModel.setForms(forms);
        formsListModel.saveFormsList();
    }

    private void loadFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите файл со списком форм");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                List<String> lines = Files.readAllLines(chooser.getSelectedFile().toPath());
                StringBuilder sb = new StringBuilder(formsTextArea.getText());
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        sb.append(line).append("\n");
                    }
                }
                formsTextArea.setText(sb.toString());
                saveFormsList();
                appendLog("Загружено форм из файла: " + lines.size());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Ошибка загрузки файла: " + e.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void clearFormsList() {
        formsTextArea.setText("");
        saveFormsList();
        appendLog("Список форм очищен");
    }

    private void openSettings() {
        SettingsDialog dialog = new SettingsDialog(this, settings);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            appendLog("Настройки сохранены. Путь к проекту: " + settings.getProjectPath());
        }
    }

    private void startAnalysis() {
        if (isRunning) {
            appendLog("Анализ уже выполняется");
            return;
        }

        saveFormsList();

        isRunning = true;
        isPaused = false;
        isCancelled = false;
        pauseButton.setEnabled(true);
        stopButton.setEnabled(true);
        startButton.setEnabled(false);
        loadButton.setEnabled(false);
        clearButton.setEnabled(false);
        settingsButton.setEnabled(false);

        // Сброс статистики
        processedForms = 0;
        totalForms = 0;
        successCount = 0;
        failCount = 0;

        // Сброс прогресс-баров
        mainProgressBar.setValue(0);
        mainProgressBar.setString("Подготовка...");
        statusLabel.setText("Подготовка к анализу...");
        subProgressBar.setVisible(false);

        executorService = Executors.newSingleThreadExecutor();
        currentTask = executorService.submit(this::runAnalysis);
    }

    private void runAnalysis() {
        // Ссылка на анализатор для управления паузой
        final ru.miacomsoft.service.ViewDependencyAnalyzer[] viewAnalyzerHolder = new ru.miacomsoft.service.ViewDependencyAnalyzer[1];

        try {
            // Этап 1: Получение списка форм (с возможным сканированием каталогов)
            updateSubProgress("Сканирование форм и каталогов...", true, 0, 0);

            final List<String> formsToAnalyze = getFormsToAnalyze();
            totalForms = formsToAnalyze.size();

            if (totalForms == 0 || isCancelled) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("Нет форм для анализа");
                    finishAnalysis();
                });
                return;
            }

            // Обновляем основной прогресс-бар
            SwingUtilities.invokeLater(() -> {
                mainProgressBar.setMaximum(totalForms);
                mainProgressBar.setValue(0);
                mainProgressBar.setString("0 / " + totalForms);
                statusLabel.setText("Анализ форм: 0 из " + totalForms);
            });

            appendLog("Начало анализа. Всего форм: " + totalForms);
            appendLog("Путь к проекту: " + settings.getProjectPath());

            // Обновляем настройки подключения
            updateDatabaseSettings();

            // Скрываем подпрогресс для основного этапа
            updateSubProgress("", false, 0, 0);

            FileScannerService scannerService = new FileScannerService(settings.getProjectPath());
            TmisFormAnalyzerService analyzerService = new TmisFormAnalyzerService(settings.getProjectPath());
            ReportGeneratorService reportService = new ReportGeneratorService();

            processedForms = 0;
            successCount = 0;
            failCount = 0;

            // Этап 2: Анализ форм
            for (String formPath : formsToAnalyze) {
                // Проверка паузы
                while (isPaused && isRunning && !isCancelled) {
                    Thread.sleep(500);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("ПРИОСТАНОВЛЕН");
                    });
                }

                if (!isRunning || isCancelled) {
                    appendLog("Анализ прерван пользователем");
                    break;
                }

                processedForms++;

                // Обновляем прогресс
                final int current = processedForms;
                SwingUtilities.invokeLater(() -> {
                    mainProgressBar.setValue(current);
                    mainProgressBar.setString(current + " / " + totalForms);
                    statusLabel.setText(String.format("Анализ форм: %d/%d (успешно: %d, ошибок: %d)",
                            current, totalForms, successCount, failCount));
                });

                appendLog("[" + current + "/" + totalForms + "] Анализ: " + formPath);

                try {
                    var result = analyzerService.analyzeForm(formPath);
                    if (result != null) {
                        reportService.addAnalysisResult(result);
                        successCount++;
                        appendLog("  ✓ OK (SQL: " + result.getTotalSqlQueries() + ")");
                    } else {
                        failCount++;
                        appendLog("  ✗ ПРОПУЩЕН (форма не найдена)");
                    }
                } catch (Exception e) {
                    failCount++;
                    appendLog("  ✗ ОШИБКА: " + e.getMessage());
                }
            }

            if (!isRunning || isCancelled) {
                finishAnalysis();
                return;
            }

            // Этап 3: Генерация отчетов
            if (!isCancelled) {
                appendLog("");
                appendLog("=== ИТОГИ АНАЛИЗА ===");
                appendLog("Всего обработано: " + processedForms);
                appendLog("Успешно: " + successCount);
                appendLog("Ошибок/пропущено: " + failCount);
                appendLog("");
                appendLog("Генерация отчетов...");

                // Запускаем генерацию отчетов с подпрогрессом
                generateReportsWithProgress(reportService, viewAnalyzerHolder);
            }

            if (isCancelled) {
                appendLog("Анализ остановлен пользователем");
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Анализ остановлен");
                    mainProgressBar.setString("Остановлен");
                });
            } else {
                appendLog("✓ Анализ завершен. Отчеты сохранены в директории: " + settings.getOutputDir());
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Анализ завершен");
                    mainProgressBar.setValue(mainProgressBar.getMaximum());
                    mainProgressBar.setString("Завершено");
                });
            }

        } catch (Exception e) {
            if (!isCancelled) {
                appendLog("Ошибка: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            SwingUtilities.invokeLater(this::finishAnalysis);
        }
    }

    private void generateReportsWithProgress(ReportGeneratorService reportService,
                                             ru.miacomsoft.service.ViewDependencyAnalyzer[] viewAnalyzerHolder) {
        try {
            // Получаем методы генерации отчетов
            java.lang.reflect.Method[] methods = reportService.getClass().getDeclaredMethods();
            List<java.lang.reflect.Method> reportMethods = new ArrayList<>();

            for (java.lang.reflect.Method method : methods) {
                if (method.getName().startsWith("generate") &&
                        method.getParameterCount() == 0 &&
                        !method.getName().equals("generateAllReports")) {
                    reportMethods.add(method);
                }
            }

            int totalReports = reportMethods.size();
            int currentReport = 0;

            for (java.lang.reflect.Method method : reportMethods) {
                // Проверка на остановку
                if (isCancelled) {
                    appendLog("Генерация отчетов прервана пользователем");
                    return;
                }

                // Проверка на паузу
                while (isPaused && !isCancelled) {
                    Thread.sleep(500);
                    SwingUtilities.invokeLater(() -> {
                        subStatusLabel.setText("ПРИОСТАНОВЛЕН");
                    });
                }

                if (isCancelled) {
                    appendLog("Генерация отчетов прервана пользователем");
                    return;
                }

                currentReport++;
                final int curr = currentReport;
                final int total = totalReports;
                final String methodName = method.getName();

                SwingUtilities.invokeLater(() -> {
                    subStatusLabel.setText("Генерация: " + methodName);
                    subProgressBar.setMaximum(total);
                    subProgressBar.setValue(curr);
                    subProgressBar.setString(curr + " / " + total);
                    subProgressBar.setVisible(true);
                });

                appendLog("  Генерация " + methodName + "...");

                // Особый случай для generateViewDependenciesReport
                if (methodName.equals("generateViewDependenciesReport")) {
                    generateViewDependenciesReportWithProgress(reportService, viewAnalyzerHolder);
                    // Проверяем, не была ли остановка во время анализа вьюх
                    if (isCancelled) {
                        appendLog("  Генерация отчетов прервана");
                        return;
                    }
                } else {
                    try {
                        method.invoke(reportService);
                    } catch (Exception e) {
                        appendLog("    Ошибка: " + e.getMessage());
                    }
                }
            }

            updateSubProgress("", false, 0, 0);

        } catch (Exception e) {
            if (!isCancelled) {
                appendLog("Ошибка при генерации отчетов: " + e.getMessage());
            }
            updateSubProgress("", false, 0, 0);
        }
    }



    private void generateViewDependenciesReportWithProgress(ReportGeneratorService reportService,
                                                            ru.miacomsoft.service.ViewDependencyAnalyzer[] viewAnalyzerHolder) {
        // Сохраняем ссылку на текущий поток для возможности прерывания
        Thread analysisThread = null;

        try {
            // Получаем таблицы и вьюхи
            java.lang.reflect.Field allTablesViewsField = reportService.getClass().getDeclaredField("allTablesViews");
            allTablesViewsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> allTablesViews = (Map<String, Object>) allTablesViewsField.get(reportService);

            // Фильтруем вьюхи
            Map<String, Object> viewsOnly = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : allTablesViews.entrySet()) {
                Object viewInfo = entry.getValue();
                java.lang.reflect.Method getTypeMethod = viewInfo.getClass().getMethod("getType");
                Object type = getTypeMethod.invoke(viewInfo);
                if (type.toString().contains("VIEW")) {
                    viewsOnly.put(entry.getKey(), viewInfo);
                }
            }

            if (viewsOnly.isEmpty()) {
                appendLog("    Нет вьюх для анализа");
                return;
            }

            int totalViews = viewsOnly.size();
            appendLog("    Найдено вьюх для анализа: " + totalViews);

            // Создаем анализатор
            ru.miacomsoft.service.ViewDependencyAnalyzer analyzer =
                    new ru.miacomsoft.service.ViewDependencyAnalyzer();

            viewAnalyzerHolder[0] = analyzer;

            // Устанавливаем флаг отмены в анализатор, если анализ уже остановлен
            if (isCancelled) {
                analyzer.setCancelled(true);
                appendLog("    Анализ вьюх отменен (остановлен пользователем)");
                return;
            }

            // Устанавливаем колбэк
            analyzer.setProgressCallback(new ru.miacomsoft.service.ViewDependencyAnalyzer.ProgressCallback() {
                @Override
                public void onProgress(int current, int total, String viewName, int oracleTables, int postgresTables) {
                    SwingUtilities.invokeLater(() -> {
                        if (subProgressBar.isVisible() && !isCancelled) {
                            subProgressBar.setMaximum(total);
                            subProgressBar.setValue(current);
                            subProgressBar.setString(String.format("%d / %d (O:%d, PG:%d)",
                                    current, total, oracleTables, postgresTables));
                        }
                    });
                }

                @Override
                public void onLog(String message) {
                    appendLog(message);
                }

                @Override
                public void onCancelled() {
                    appendLog("    Анализ вьюх был отменен");
                    SwingUtilities.invokeLater(() -> {
                        subProgressBar.setVisible(false);
                        subStatusLabel.setText("");
                    });
                }
            });

            // Запускаем анализ в отдельном потоке
            analysisThread = new Thread(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, TableViewInfo> viewsMap = (Map<String, TableViewInfo>) (Map<?, ?>) viewsOnly;
                    Map<String, ru.miacomsoft.model.ViewTableDependencies> dependencies =
                            analyzer.analyzeAllViews(viewsMap);

                    // Проверяем отмену перед генерацией отчета
                    if (!isCancelled && !analyzer.isCancelled()) {
                        analyzer.generateViewDependenciesReport(dependencies);
                    } else {
                        appendLog("    Генерация отчета по вьюхам отменена");
                    }
                } catch (InterruptedException e) {
                    appendLog("    Анализ вьюх прерван пользователем");
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    if (!isCancelled && !analyzer.isCancelled()) {
                        appendLog("    Ошибка: " + e.getMessage());
                    }
                }
            });

            analysisThread.start();

            // Мониторинг состояния с возможностью остановки
            while (analysisThread.isAlive() && !isCancelled) {
                // Обновляем состояние паузы в анализаторе
                analyzer.setPaused(isPaused);

                // Небольшая задержка для снижения нагрузки на CPU
                Thread.sleep(100);
            }

            // Если была запрошена остановка
            if (isCancelled) {
                appendLog("    Остановка анализа вьюх...");

                // Устанавливаем флаг отмены в анализатор
                analyzer.setCancelled(true);

                // Прерываем поток
                if (analysisThread != null && analysisThread.isAlive()) {
                    analysisThread.interrupt();

                    // Ждем завершения потока с таймаутом
                    analysisThread.join(3000);

                    if (analysisThread.isAlive()) {
                        appendLog("    Принудительное завершение анализа вьюх (таймаут)");
                        // Не используем deprecated методы, просто логируем
                    }
                }

                appendLog("    Анализ вьюх остановлен");
            } else {
                // Нормальное завершение - ждем окончания потока
                if (analysisThread != null && analysisThread.isAlive()) {
                    analysisThread.join();
                }
                appendLog("    Анализ зависимостей вьюх завершен");
            }

        } catch (InterruptedException e) {
            appendLog("    Ожидание анализа вьюх было прервано");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isCancelled) {
                appendLog("    Ошибка при анализе вьюх: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            // Очистка
            if (viewAnalyzerHolder[0] != null) {
                viewAnalyzerHolder[0].setCancelled(true);
            }
            updateSubProgress("", false, 0, 0);
        }
    }










    private void updateSubProgress(String message, boolean visible, int value, int max) {
        SwingUtilities.invokeLater(() -> {
            if (visible) {
                subStatusLabel.setText(message);
                subProgressBar.setVisible(true);
                if (max > 0) {
                    subProgressBar.setMaximum(max);
                    subProgressBar.setValue(value);
                    subProgressBar.setString(value + " / " + max);
                } else {
                    subProgressBar.setIndeterminate(true);
                    subProgressBar.setString(message);
                }
            } else {
                subProgressBar.setVisible(false);
                subProgressBar.setIndeterminate(false);
                subStatusLabel.setText("");
            }
        });
    }

    private void updateDatabaseSettings() {
        try {
            Class<?> analyzerClass = Class.forName("ru.miacomsoft.service.ViewDependencyAnalyzer");

            java.lang.reflect.Field oracleUrlField = analyzerClass.getDeclaredField("ORACLE_URL");
            java.lang.reflect.Field oracleUserField = analyzerClass.getDeclaredField("ORACLE_USER");
            java.lang.reflect.Field oraclePassField = analyzerClass.getDeclaredField("ORACLE_PASSWORD");
            java.lang.reflect.Field postgresUrlField = analyzerClass.getDeclaredField("POSTGRES_URL");
            java.lang.reflect.Field postgresUserField = analyzerClass.getDeclaredField("POSTGRES_USER");
            java.lang.reflect.Field postgresPassField = analyzerClass.getDeclaredField("POSTGRES_PASSWORD");

            oracleUrlField.setAccessible(true);
            oracleUserField.setAccessible(true);
            oraclePassField.setAccessible(true);
            postgresUrlField.setAccessible(true);
            postgresUserField.setAccessible(true);
            postgresPassField.setAccessible(true);

            java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);

            modifiersField.setInt(oracleUrlField, oracleUrlField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            modifiersField.setInt(oracleUserField, oracleUserField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            modifiersField.setInt(oraclePassField, oraclePassField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            modifiersField.setInt(postgresUrlField, postgresUrlField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            modifiersField.setInt(postgresUserField, postgresUserField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            modifiersField.setInt(postgresPassField, postgresPassField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

            oracleUrlField.set(null, settings.getOracleUrl());
            oracleUserField.set(null, settings.getOracleUser());
            oraclePassField.set(null, settings.getOraclePassword());
            postgresUrlField.set(null, settings.getPostgresUrl());
            postgresUserField.set(null, settings.getPostgresUser());
            postgresPassField.set(null, settings.getPostgresPassword());

            appendLog("Настройки БД обновлены");
        } catch (Exception e) {
            appendLog("Предупреждение: не удалось обновить настройки БД: " + e.getMessage());
        }
    }

    /**
     * Получение списка форм для анализа с поддержкой сканирования каталогов
     * Если указанный путь является каталогом, сканирует его рекурсивно
     */
    private List<String> getFormsToAnalyze() {
        String formsText = formsTextArea.getText().trim();
        List<String> forms = new ArrayList<>();

        if (formsText.isEmpty()) {
            appendLog("Список форм пуст. Сканируем все формы из каталога Forms...");
            FileScannerService scanner = new FileScannerService(settings.getProjectPath());
            Set<String> allForms = scanner.findAllBaseForms();
            forms.addAll(allForms);
            appendLog("Найдено форм: " + allForms.size());
        } else {
            String[] lines = formsText.split("\\r?\\n");
            FileScannerService scanner = new FileScannerService(settings.getProjectPath());

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                appendLog("Обработка строки: " + line);

                // Проверяем, является ли путь каталогом (включая .d каталоги)
                List<String> foundForms = processPathEntry(line, scanner);
                for (String form : foundForms) {
                    if (!forms.contains(form)) {
                        forms.add(form);
                    }
                }
            }

            appendLog("Загружено форм из списка: " + forms.size());
        }

        return forms;
    }



    /**
     * Обработка одной строки пути (может быть файлом или каталогом)
     */
    private List<String> processPathEntry(String path, FileScannerService scanner) {
        List<String> result = new ArrayList<>();

        if (path == null || path.trim().isEmpty()) {
            return result;
        }

        String normalized = path.trim();

        // Убираем ведущий слеш
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        Path projectRoot = Paths.get(settings.getProjectPath());
        Path fullPath = projectRoot.resolve(normalized);

        // Проверяем различные варианты путей
        List<Path> possiblePaths = new ArrayList<>();
        possiblePaths.add(fullPath);

        // Если путь не начинается с UserForms, пробуем с Forms/
        if (!normalized.startsWith("UserForms")) {
            possiblePaths.add(projectRoot.resolve("Forms").resolve(normalized));
            possiblePaths.add(projectRoot.resolve(normalized));
        } else {
            // Для UserForms также пробуем разные варианты
            possiblePaths.add(projectRoot.resolve(normalized));
            possiblePaths.add(projectRoot.resolve("Forms").resolve(normalized));
        }

        // Специальная обработка для .d каталогов
        if (normalized.endsWith(".d") || normalized.contains(".d/")) {
            String dPath = normalized;
            if (!dPath.startsWith("UserForms") && !dPath.startsWith("Forms/")) {
                // Пробуем найти в Forms
                Path formsDPath = projectRoot.resolve("Forms").resolve(dPath);
                if (formsDPath.toFile().exists() && formsDPath.toFile().isDirectory()) {
                    fullPath = formsDPath;
                } else {
                    // Пробуем найти в UserForms
                    for (Path p : possiblePaths) {
                        if (p.toFile().exists() && p.toFile().isDirectory()) {
                            fullPath = p;
                            break;
                        }
                    }
                }
            }
        }

        for (Path checkPath : possiblePaths) {
            File file = checkPath.toFile();
            if (file.exists()) {
                if (file.isDirectory()) {
                    // Это каталог - сканируем его
                    appendLog("Обнаружен каталог: " + checkPath.toString());
                    scanDirectoryForAllForms(checkPath.toFile(), result, scanner);
                    break;
                } else if (file.isFile() && (file.getName().endsWith(".frm") || file.getName().endsWith(".dfrm"))) {
                    // Это файл формы
                    String relativePath = getRelativePathFromProject(checkPath);
                    if (relativePath != null) {
                        result.add(relativePath);
                        appendLog("    Добавлена форма: " + relativePath);
                    }
                    break;
                }
            }
        }
        // Если файл/каталог не найден, возможно это путь без расширения
        if (result.isEmpty() && !normalized.endsWith(".frm") && !normalized.endsWith(".dfrm") && !normalized.endsWith(".d")) {
            // Пробуем добавить .frm и проверить как файл
            String withExt = normalized + ".frm";
            Path withExtPath = projectRoot.resolve(withExt);
            Path withExtFormsPath = projectRoot.resolve("Forms").resolve(withExt);

            if (withExtPath.toFile().exists() && withExtPath.toFile().isFile()) {
                result.add(getRelativePathFromProject(withExtPath));
                appendLog("    Добавлена форма (с расширением): " + withExt);
            } else if (withExtFormsPath.toFile().exists() && withExtFormsPath.toFile().isFile()) {
                result.add(getRelativePathFromProject(withExtFormsPath));
                appendLog("    Добавлена форма (с расширением): " + withExtFormsPath);
            } else {
                appendLog("    Предупреждение: не найден файл или каталог: " + normalized);
            }
        }

        return result;
    }



    /**
     * Рекурсивное сканирование каталога для поиска .frm и .dfrm файлов
     * Поддерживает любые каталоги, включая .d
     */
    private void scanDirectoryForAllForms(File directory, List<String> forms, FileScannerService scanner) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        appendLog("    Сканирование каталога: " + directory.getPath());

        try {
            Files.walk(directory.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".frm") || path.toString().endsWith(".dfrm"))
                    .forEach(path -> {
                        String relativePath = getRelativePathFromProject(path);
                        if (relativePath != null && !forms.contains(relativePath)) {
                            forms.add(relativePath);
                            appendLog("        Найдена форма: " + relativePath);
                        } else if (relativePath != null) {
                            appendLog("        Пропущено (дубликат): " + relativePath);
                        }
                    });

            // Выводим статистику
            long foundCount = Files.walk(directory.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".frm") || path.toString().endsWith(".dfrm"))
                    .count();

            appendLog("    Найдено форм в каталоге: " + foundCount);

        } catch (IOException e) {
            appendLog("    Ошибка при сканировании каталога " + directory.getPath() + ": " + e.getMessage());
        }
    }

    /**
     * Рекурсивное сканирование каталога для поиска .frm и .dfrm файлов
     */
    private void scanDirectoryForForms(File directory, List<String> forms, FileScannerService scanner) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        try {
            Files.walk(directory.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".frm") || path.toString().endsWith(".dfrm"))
                    .forEach(path -> {
                        String relativePath = getRelativePathFromProject(path);
                        if (relativePath != null && !forms.contains(relativePath)) {
                            forms.add(relativePath);
                            appendLog("    Найдена форма: " + relativePath);
                        }
                    });
        } catch (IOException e) {
            appendLog("Ошибка при сканировании каталога " + directory.getPath() + ": " + e.getMessage());
        }
    }

    /**
     * Получить относительный путь от корня проекта
     */
    private String getRelativePathFromProject(Path absolutePath) {
        try {
            Path projectRoot = Paths.get(settings.getProjectPath());
            String relative = projectRoot.relativize(absolutePath).toString().replace("\\", "/");
            return relative;
        } catch (IllegalArgumentException e) {
            return absolutePath.toString();
        }
    }


    /**
     * Нормализация пути формы (обновленная версия с поддержкой .d каталогов)
     */
    private String normalizeFormPathForAnalysis(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        String normalized = path.trim();

        // Убираем ведущий слеш для обработки
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        // UserForms - оставляем как есть (это полный путь от корня)
        if (normalized.startsWith("UserForms")) {
            // Проверяем, является ли путь каталогом .d
            if (normalized.endsWith(".d") || normalized.contains(".d/") || normalized.endsWith(".d/")) {
                // Это каталог .d, возвращаем как есть для дальнейшей обработки
                return normalized;
            }

            if (!normalized.endsWith(".frm") && !normalized.endsWith(".dfrm")) {
                // Проверяем, может это каталог?
                Path fullPath = Paths.get(settings.getProjectPath()).resolve(normalized);
                if (fullPath.toFile().exists() && fullPath.toFile().isDirectory()) {
                    return normalized; // Возвращаем как каталог
                }
                normalized = normalized + ".frm";
            }
            return normalized;
        }

        // Forms пути
        if (normalized.startsWith("Forms/")) {
            normalized = normalized.substring(6);
        } else if (normalized.startsWith("Forms")) {
            normalized = normalized.substring(5);
        }

        // Проверка на каталог .d
        if (normalized.endsWith(".d") || normalized.contains(".d/") || normalized.endsWith(".d/")) {
            return "Forms/" + normalized;
        }

        // Проверяем, является ли путь каталогом (без расширения)
        if (!normalized.endsWith(".frm") && !normalized.endsWith(".dfrm")) {
            Path fullPath = Paths.get(settings.getProjectPath()).resolve("Forms").resolve(normalized);
            if (fullPath.toFile().exists() && fullPath.toFile().isDirectory()) {
                return "Forms/" + normalized; // Возвращаем как каталог
            }
            normalized = normalized + ".frm";
        }

        return normalized;
    }

    private void pauseAnalysis() {
        if (isRunning && !isPaused) {
            isPaused = true;
            pauseButton.setText("Возобновить");
            appendLog("Анализ ПРИОСТАНОВЛЕН");
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("ПРИОСТАНОВЛЕН");
                subStatusLabel.setText("ПРИОСТАНОВЛЕН (ожидание...)");
            });
        } else if (isRunning && isPaused) {
            isPaused = false;
            pauseButton.setText("Пауза");
            appendLog("Анализ ВОЗОБНОВЛЕН");
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Анализ продолжается...");
                subStatusLabel.setText("");
            });
        }
    }

    private void stopAnalysis() {
        if (isRunning) {
            isCancelled = true;
            isPaused = false;
            isRunning = false;
            appendLog("Остановка анализа...");

            // Прерываем основной поток выполнения
            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(true);
            }

            // Принудительно прерываем все потоки в executorService
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdownNow();
            }

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Остановка...");
                subStatusLabel.setText("Остановка...");
            });
        }
    }

    private void finishAnalysis() {
        isRunning = false;
        isPaused = false;

        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        loadButton.setEnabled(true);
        clearButton.setEnabled(true);
        settingsButton.setEnabled(true);

        subProgressBar.setVisible(false);
        subStatusLabel.setText("");

        // Принудительно завершаем executorService если он еще жив
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            executorService = null;
        }

        // Сбрасываем флаг отмены
        isCancelled = false;

        // Обновляем статус
        statusLabel.setText("Готов к работе");
        mainProgressBar.setString("");
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + message + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new MainFrame().setVisible(true);
        });
    }
    /**
     * Рекурсивное сканирование каталога UserForms для поиска .frm и .dfrm файлов
     * (дополнительный метод для более удобного сканирования)
     */
    private void scanUserFormsDirectory(File directory, List<String> forms, FileScannerService scanner) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        try {
            Files.walk(directory.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".frm") || path.toString().endsWith(".dfrm"))
                    .forEach(path -> {
                        String relativePath = getRelativePathFromProject(path);
                        if (relativePath != null && !forms.contains(relativePath)) {
                            forms.add(relativePath);
                            appendLog("    Найдена UserForm: " + relativePath);
                        }
                    });
        } catch (IOException e) {
            appendLog("Ошибка при сканировании UserForms каталога " + directory.getPath() + ": " + e.getMessage());
        }
    }
}