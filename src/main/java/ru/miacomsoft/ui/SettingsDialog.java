package ru.miacomsoft.ui;

import ru.miacomsoft.model.ReportConfig;
import ru.miacomsoft.model.SettingsModel;
import ru.miacomsoft.service.ViewDependencyAnalyzer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.sql.*;
import java.util.Properties;

public class SettingsDialog extends JDialog {

    private SettingsModel settings;
    private boolean saved = false;

    // Connection fields
    private JTextField projectPathField;
    private JTextField outputDirField;
    private JTextField oracleUrlField;
    private JTextField oracleUserField;
    private JPasswordField oraclePasswordField;
    private JTextField postgresUrlField;
    private JTextField postgresUserField;
    private JPasswordField postgresPasswordField;

    // Status labels and details
    private JLabel oracleStatusLabel;
    private JTextArea oracleDetailsArea;
    private JLabel postgresStatusLabel;
    private JTextArea postgresDetailsArea;

    // Report config checkboxes
    private JCheckBox includeSqlContentCheckbox;
    private JCheckBox includeJsFormsCheckbox;
    private JCheckBox includeTablesViewsCheckbox;
    private JCheckBox includeViewTablesCheckbox;
    private JCheckBox includeJsUnitCompositionsCheckbox;
    private JCheckBox includeViewDetailsCheckbox;

    public SettingsDialog(JFrame parent, SettingsModel settings) {
        super(parent, "Настройки анализатора", true);
        this.settings = settings;
        initUI();
        loadSettings();
    }

    public boolean isSaved() {
        return saved;
    }

    private void initUI() {
        setSize(900, 800);
        setLocationRelativeTo(getParent());

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Подключения к БД", createConnectionPanel());
        tabbedPane.addTab("Настройки отчета", createReportConfigPanel());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Сохранить");
        JButton cancelButton = new JButton("Отмена");

        saveButton.addActionListener(e -> {
            saveSettings();
            saved = true;
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        int row = 0;

        // Project Path
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Путь к проекту T-MIS:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        projectPathField = new JTextField();
        panel.add(projectPathField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton browseProjectButton = new JButton("Обзор...");
        browseProjectButton.addActionListener(e -> browseFolder(projectPathField));
        panel.add(browseProjectButton, gbc);

        row++;

        // Output Directory
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Каталог для отчетов:"), gbc);

        gbc.gridx = 1;
        outputDirField = new JTextField();
        panel.add(outputDirField, gbc);

        gbc.gridx = 2;
        JButton browseOutputButton = new JButton("Обзор...");
        browseOutputButton.addActionListener(e -> browseFolder(outputDirField));
        panel.add(browseOutputButton, gbc);

        row++;

        // Separator Oracle
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        panel.add(createSeparator("Oracle Database"), gbc);

        row++;
        gbc.gridwidth = 1;

        // Oracle URL
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Oracle URL:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 1;
        oracleUrlField = new JTextField();
        panel.add(oracleUrlField, gbc);

        // Oracle Test Button
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        JButton testOracleButton = new JButton("Проверить");
        testOracleButton.setBackground(new Color(33, 150, 243));
        testOracleButton.addActionListener(e -> testOracleConnection());
        panel.add(testOracleButton, gbc);

        row++;

        // Oracle User
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Oracle User:"), gbc);

        gbc.gridx = 1;
        oracleUserField = new JTextField();
        panel.add(oracleUserField, gbc);

        row++;

        // Oracle Password
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Oracle Password:"), gbc);

        gbc.gridx = 1;
        oraclePasswordField = new JPasswordField();
        panel.add(oraclePasswordField, gbc);

        row++;

        // Oracle Status
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Статус:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        oracleStatusLabel = new JLabel("Не проверено");
        oracleStatusLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        panel.add(oracleStatusLabel, gbc);

        row++;

        // Oracle Details
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.3;

        oracleDetailsArea = new JTextArea();
        oracleDetailsArea.setEditable(false);
        oracleDetailsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        oracleDetailsArea.setBackground(new Color(255, 245, 245));
        oracleDetailsArea.setForeground(Color.RED);
        oracleDetailsArea.setRows(4);
        JScrollPane oracleScrollPane = new JScrollPane(oracleDetailsArea);
        oracleScrollPane.setBorder(BorderFactory.createTitledBorder("Детали подключения"));
        panel.add(oracleScrollPane, gbc);

        row++;

        // Separator PostgreSQL
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        panel.add(createSeparator("PostgreSQL Database"), gbc);

        row++;
        gbc.gridwidth = 1;

        // PostgreSQL URL
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("PostgreSQL URL:"), gbc);

        gbc.gridx = 1;
        postgresUrlField = new JTextField();
        panel.add(postgresUrlField, gbc);

        // PostgreSQL Test Button
        gbc.gridx = 2;
        JButton testPostgresButton = new JButton("Проверить");
        testPostgresButton.setBackground(new Color(33, 150, 243));
        testPostgresButton.addActionListener(e -> testPostgresConnection());
        panel.add(testPostgresButton, gbc);

        row++;

        // PostgreSQL User
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("PostgreSQL User:"), gbc);

        gbc.gridx = 1;
        postgresUserField = new JTextField();
        panel.add(postgresUserField, gbc);

        row++;

        // PostgreSQL Password
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("PostgreSQL Password:"), gbc);

        gbc.gridx = 1;
        postgresPasswordField = new JPasswordField();
        panel.add(postgresPasswordField, gbc);

        row++;

        // PostgreSQL Status
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Статус:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        postgresStatusLabel = new JLabel("Не проверено");
        postgresStatusLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        panel.add(postgresStatusLabel, gbc);

        row++;

        // PostgreSQL Details
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.3;

        postgresDetailsArea = new JTextArea();
        postgresDetailsArea.setEditable(false);
        postgresDetailsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        postgresDetailsArea.setBackground(new Color(255, 245, 245));
        postgresDetailsArea.setForeground(Color.RED);
        postgresDetailsArea.setRows(4);
        JScrollPane postgresScrollPane = new JScrollPane(postgresDetailsArea);
        postgresScrollPane.setBorder(BorderFactory.createTitledBorder("Детали подключения"));
        panel.add(postgresScrollPane, gbc);

        row++;

        // Fill remaining space
        gbc.gridy = row;
        gbc.weighty = 1.0;
        panel.add(new JPanel(), gbc);

        return panel;
    }

    /**
     * Проверка подключения к Oracle с детальным выводом ошибок
     */
    private void testOracleConnection() {
        String url = oracleUrlField.getText().trim();
        String user = oracleUserField.getText().trim();
        String password = new String(oraclePasswordField.getPassword());

        if (url.isEmpty() || user.isEmpty()) {
            oracleStatusLabel.setText("❌ Заполните URL и User");
            oracleStatusLabel.setForeground(Color.RED);
            oracleDetailsArea.setText("Пожалуйста, заполните:\n- URL подключения\n- Имя пользователя");
            return;
        }

        oracleStatusLabel.setText("⏳ Проверка подключения...");
        oracleStatusLabel.setForeground(Color.ORANGE);
        oracleDetailsArea.setText("");

        // Запускаем в отдельном потоке
        new Thread(() -> {
            StringBuilder details = new StringBuilder();
            boolean success = false;
            String errorType = "";
            String errorMessage = "";

            try {
                Class.forName("oracle.jdbc.OracleDriver");
                details.append("✓ JDBC драйвер загружен\n");

                Properties props = new Properties();
                props.setProperty("user", user);
                props.setProperty("password", password);
                props.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
                props.setProperty("oracle.jdbc.ReadTimeout", "10000");

                details.append("✓ Параметры подключения установлены\n");
                details.append("  URL: ").append(url).append("\n");
                details.append("  User: ").append(user).append("\n");
                details.append("  Timeout: 5 сек\n");

                long startTime = System.currentTimeMillis();

                try (Connection conn = DriverManager.getConnection(url, props)) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    details.append("✓ Соединение установлено за ").append(elapsed).append(" мс\n");

                    // Пробуем выполнить простой запрос
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {
                        if (rs.next()) {
                            details.append("✓ Тестовый запрос выполнен успешно\n");
                            details.append("  Результат: ").append(rs.getInt(1)).append("\n");
                            success = true;
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                errorType = "JDBC Driver Error";
                errorMessage = "Oracle JDBC драйвер не найден в classpath";
                details.append("❌ ").append(errorMessage).append("\n");
                details.append("  Решение: Добавьте ojdbc11.jar в зависимости проекта\n");
            } catch (SQLException e) {
                String sqlState = e.getSQLState();
                int errorCode = e.getErrorCode();
                errorMessage = e.getMessage();

                details.append("❌ SQL Ошибка:\n");
                details.append("  Error Code: ").append(errorCode).append("\n");
                details.append("  SQL State: ").append(sqlState).append("\n");
                details.append("  Message: ").append(errorMessage).append("\n\n");

                // Детальный анализ ошибки
                if (errorMessage.contains("ORA-12505")) {
                    errorType = "SID/Service Name Error";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Неверный SID или Service Name в URL\n");
                    details.append("  - Пример правильного URL: jdbc:oracle:thin:@host:1521/XE\n");
                    details.append("  - Проверьте доступные сервисы: lsnrctl services\n");
                } else if (errorMessage.contains("ORA-12514")) {
                    errorType = "Listener Error";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Listener не знает указанный SID/Service\n");
                    details.append("  - Проверьте на сервере: lsnrctl status\n");
                    details.append("  - Убедитесь, что база данных запущена\n");
                } else if (errorMessage.contains("ORA-12541")) {
                    errorType = "Connection Refused";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Oracle Listener не запущен на сервере\n");
                    details.append("  - Неправильный хост или порт\n");
                    details.append("  - Брандмауэр блокирует порт 1521\n");
                    details.append("  - Решение: Запустите lsnrctl start на сервере\n");
                } else if (errorMessage.contains("ORA-01017")) {
                    errorType = "Authentication Error";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Неверное имя пользователя или пароль\n");
                    details.append("  - Пользователь заблокирован\n");
                    details.append("  - Срок действия пароля истек\n");
                } else if (errorMessage.contains("IO Error") || errorMessage.contains("The Network Adapter could not establish the connection")) {
                    errorType = "Network Error";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Сервер недоступен по сети\n");
                    details.append("  - Проверьте: ping ").append(url.split("@")[1].split(":")[0]).append("\n");
                    details.append("  - Проверьте: telnet ").append(url.split("@")[1].split(":")[0]).append(" 1521\n");
                    details.append("  - Брандмауэр блокирует соединение\n");
                } else if (errorMessage.contains("ORA-28000")) {
                    errorType = "Account Locked";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Учетная запись заблокирована\n");
                    details.append("  - Решение: ALTER USER ").append(user).append(" ACCOUNT UNLOCK;\n");
                } else {
                    details.append("📋 Рекомендации:\n");
                    details.append("  - Проверьте правильность URL\n");
                    details.append("  - Убедитесь, что сервер доступен\n");
                    details.append("  - Проверьте логи Oracle на сервере\n");
                }
            } catch (Exception e) {
                errorType = "Unknown Error";
                errorMessage = e.getMessage();
                details.append("❌ Неизвестная ошибка: ").append(errorMessage).append("\n");
                details.append("  Тип: ").append(e.getClass().getSimpleName()).append("\n");
            }

            final boolean finalSuccess = success;
            final String finalDetails = details.toString();
            final String finalErrorType = errorType;

            SwingUtilities.invokeLater(() -> {
                if (finalSuccess) {
                    oracleStatusLabel.setText("✅ Подключение успешно!");
                    oracleStatusLabel.setForeground(new Color(0, 150, 0));
                    oracleDetailsArea.setForeground(new Color(0, 100, 0));
                    oracleDetailsArea.setBackground(new Color(240, 255, 240));
                } else {
                    oracleStatusLabel.setText("❌ Ошибка: " + (finalErrorType.isEmpty() ? "Подключение не удалось" : finalErrorType));
                    oracleStatusLabel.setForeground(Color.RED);
                    oracleDetailsArea.setForeground(Color.RED);
                    oracleDetailsArea.setBackground(new Color(255, 245, 245));
                }
                oracleDetailsArea.setText(finalDetails);
                oracleDetailsArea.setCaretPosition(0);
            });
        }).start();
    }

    /**
     * Проверка подключения к PostgreSQL с детальным выводом ошибок
     */
    private void testPostgresConnection() {
        String url = postgresUrlField.getText().trim();
        String user = postgresUserField.getText().trim();
        String password = new String(postgresPasswordField.getPassword());

        if (url.isEmpty() || user.isEmpty()) {
            postgresStatusLabel.setText("❌ Заполните URL и User");
            postgresStatusLabel.setForeground(Color.RED);
            postgresDetailsArea.setText("Пожалуйста, заполните:\n- URL подключения\n- Имя пользователя");
            return;
        }

        postgresStatusLabel.setText("⏳ Проверка подключения...");
        postgresStatusLabel.setForeground(Color.ORANGE);
        postgresDetailsArea.setText("");

        // Запускаем в отдельном потоке
        new Thread(() -> {
            StringBuilder details = new StringBuilder();
            boolean success = false;
            String errorType = "";

            try {
                Class.forName("org.postgresql.Driver");
                details.append("✓ JDBC драйвер загружен\n");

                DriverManager.setLoginTimeout(5);
                details.append("✓ Таймаут подключения установлен: 5 сек\n");

                long startTime = System.currentTimeMillis();

                try (Connection conn = DriverManager.getConnection(url, user, password)) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    details.append("✓ Соединение установлено за ").append(elapsed).append(" мс\n");
                    details.append("  URL: ").append(url).append("\n");
                    details.append("  User: ").append(user).append("\n");

                    // Получаем информацию о сервере
                    try (Statement stmt = conn.createStatement();) {
                        // Версия PostgreSQL
                        ResultSet rs = stmt.executeQuery("SELECT version()");
                        if (rs.next()) {
                            String version = rs.getString(1);
                            details.append("✓ PostgreSQL версия: ").append(version.substring(0, Math.min(50, version.length()))).append("...\n");
                        }

                        // Тестовый запрос
                        rs = stmt.executeQuery("SELECT 1");
                        if (rs.next()) {
                            details.append("✓ Тестовый запрос выполнен успешно\n");
                            success = true;
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                errorType = "JDBC Driver Error";
                details.append("❌ PostgreSQL JDBC драйвер не найден в classpath\n");
                details.append("  Решение: Добавьте postgresql-42.x.x.jar в зависимости проекта\n");
            } catch (SQLException e) {
                String sqlState = e.getSQLState();
                int errorCode = e.getErrorCode();
                String errorMessage = e.getMessage();

                details.append("❌ SQL Ошибка:\n");
                details.append("  Error Code: ").append(errorCode).append("\n");
                details.append("  SQL State: ").append(sqlState).append("\n");
                details.append("  Message: ").append(errorMessage).append("\n\n");

                // Детальный анализ ошибки
                if (errorMessage.contains("Connection refused")) {
                    errorType = "Connection Refused";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - PostgreSQL сервер не запущен\n");
                    details.append("  - Неправильный хост или порт\n");
                    details.append("  - Брандмауэр блокирует порт 5432\n");
                    details.append("  - Решение: sudo systemctl start postgresql\n");
                } else if (errorMessage.contains("password authentication failed")) {
                    errorType = "Authentication Error";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Неверное имя пользователя или пароль\n");
                    details.append("  - Метод аутентификации требует другого пароля\n");
                    details.append("  - Решение: Проверьте pg_hba.conf настройки\n");
                } else if (errorMessage.contains("database") && errorMessage.contains("does not exist")) {
                    errorType = "Database Not Found";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Указанная база данных не существует\n");
                    details.append("  - Решение: CREATE DATABASE ").append(url.substring(url.lastIndexOf("/") + 1)).append(";\n");
                } else if (errorMessage.contains("timeout")) {
                    errorType = "Timeout Error";
                    details.append("📋 Возможные причины:\n");
                    details.append("  - Сервер не отвечает на запросы\n");
                    details.append("  - Сетевая задержка слишком большая\n");
                    details.append("  - Решение: Увеличьте таймаут или проверьте сеть\n");
                } else {
                    details.append("📋 Рекомендации:\n");
                    details.append("  - Проверьте правильность URL: jdbc:postgresql://host:port/database\n");
                    details.append("  - Убедитесь, что сервер доступен\n");
                    details.append("  - Проверьте логи PostgreSQL: tail -f /var/log/postgresql/postgresql.log\n");
                }

                // Добавляем информацию о pg_hba.conf
                if (errorMessage.contains("no pg_hba.conf entry")) {
                    details.append("\n📋 Проблема с pg_hba.conf:\n");
                    details.append("  - Добавьте запись для вашего IP в pg_hba.conf\n");
                    details.append("  - Пример: host all all 0.0.0.0/0 md5\n");
                    details.append("  - Затем перезагрузите: sudo systemctl reload postgresql\n");
                }
            } catch (Exception e) {
                details.append("❌ Неизвестная ошибка: ").append(e.getMessage()).append("\n");
                details.append("  Тип: ").append(e.getClass().getSimpleName()).append("\n");
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            final String finalDetails = details.toString();
            final String finalErrorType = errorType;

            SwingUtilities.invokeLater(() -> {
                if (finalSuccess) {
                    postgresStatusLabel.setText("✅ Подключение успешно!");
                    postgresStatusLabel.setForeground(new Color(0, 150, 0));
                    postgresDetailsArea.setForeground(new Color(0, 100, 0));
                    postgresDetailsArea.setBackground(new Color(240, 255, 240));
                } else {
                    postgresStatusLabel.setText("❌ Ошибка: " + (finalErrorType.isEmpty() ? "Подключение не удалось" : finalErrorType));
                    postgresStatusLabel.setForeground(Color.RED);
                    postgresDetailsArea.setForeground(Color.RED);
                    postgresDetailsArea.setBackground(new Color(255, 245, 245));
                }
                postgresDetailsArea.setText(finalDetails);
                postgresDetailsArea.setCaretPosition(0);
            });
        }).start();
    }

    private JPanel createReportConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Scroll pane for checkboxes
        JPanel checkboxesPanel = new JPanel();
        checkboxesPanel.setLayout(new BoxLayout(checkboxesPanel, BoxLayout.Y_AXIS));

        // Preset buttons
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presetPanel.setBorder(new TitledBorder("Быстрые настройки"));

        JButton minimalButton = new JButton("Минимальный");
        JButton standardButton = new JButton("Стандартный");
        JButton fullButton = new JButton("Полный");

        minimalButton.addActionListener(e -> setPreset(ReportConfig.Preset.MINIMAL));
        standardButton.addActionListener(e -> setPreset(ReportConfig.Preset.STANDARD));
        fullButton.addActionListener(e -> setPreset(ReportConfig.Preset.FULL));

        presetPanel.add(minimalButton);
        presetPanel.add(standardButton);
        presetPanel.add(fullButton);

        checkboxesPanel.add(presetPanel);
        checkboxesPanel.add(Box.createVerticalStrut(10));

        // Separator
        JLabel separator = new JLabel("Детальные настройки:");
        separator.setFont(separator.getFont().deriveFont(Font.BOLD));
        checkboxesPanel.add(separator);
        checkboxesPanel.add(Box.createVerticalStrut(10));

        // Create checkboxes
        includeSqlContentCheckbox = new JCheckBox("Показывать SQL запросы");
        includeJsFormsCheckbox = new JCheckBox("Список вызываемых форм в JS");
        includeTablesViewsCheckbox = new JCheckBox("Используемые таблицы и вьюхи");
        includeViewTablesCheckbox = new JCheckBox("Таблицы через вьюхи");
        includeJsUnitCompositionsCheckbox = new JCheckBox("Композиции из JS (UniversalComposition)");
        includeViewDetailsCheckbox = new JCheckBox("Детальное содержимое вьюх");

        // Add checkboxes with descriptions
        checkboxesPanel.add(createCheckboxWithDescription(includeSqlContentCheckbox,
                "Выводить полное содержимое SQL запросов в отчете.\n" +
                        "Включает: SELECT, INSERT, UPDATE, DELETE, BEGIN...END блоки"));
        checkboxesPanel.add(Box.createVerticalStrut(5));

        checkboxesPanel.add(createCheckboxWithDescription(includeJsFormsCheckbox,
                "Извлекать и выводить формы, вызываемые через JS функции:\n" +
                        "- openWindow()\n" +
                        "- openD3Form()\n" +
                        "- D3Api.showForm()"));
        checkboxesPanel.add(Box.createVerticalStrut(5));

        checkboxesPanel.add(createCheckboxWithDescription(includeTablesViewsCheckbox,
                "Выводить список всех таблиц (D_*) и представлений (D_V_*),\n" +
                        "используемых в SQL запросах формы"));
        checkboxesPanel.add(Box.createVerticalStrut(5));

        checkboxesPanel.add(createCheckboxWithDescription(includeViewTablesCheckbox,
                "Анализировать вьюхи и показывать какие таблицы\n" +
                        "используются внутри каждой вьюхи (требуется подключение к БД)"));
        checkboxesPanel.add(Box.createVerticalStrut(5));

        checkboxesPanel.add(createCheckboxWithDescription(includeJsUnitCompositionsCheckbox,
                "Извлекать композиции UnitEdit из JS вызовов\n" +
                        "UniversalComposition в openWindow/openD3Form"));
        checkboxesPanel.add(Box.createVerticalStrut(5));

        checkboxesPanel.add(createCheckboxWithDescription(includeViewDetailsCheckbox,
                "Выводить полный список таблиц для каждой вьюхи\n" +
                        "(требуется подключение к Oracle БД)"));

        // Add to scroll pane
        JScrollPane scrollPane = new JScrollPane(checkboxesPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCheckboxWithDescription(JCheckBox checkBox, String description) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        checkBox.setFont(checkBox.getFont().deriveFont(Font.BOLD));

        JTextArea descArea = new JTextArea(description);
        descArea.setEditable(false);
        descArea.setBackground(panel.getBackground());
        descArea.setFont(new Font("Dialog", Font.PLAIN, 11));
        descArea.setForeground(Color.GRAY);
        descArea.setWrapStyleWord(true);
        descArea.setLineWrap(true);

        panel.add(checkBox, BorderLayout.NORTH);
        panel.add(descArea, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSeparator(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setForeground(new Color(0, 100, 200));
        panel.add(label, BorderLayout.WEST);
        return panel;
    }

    private void browseFolder(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String currentPath = field.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            chooser.setCurrentDirectory(new File(currentPath));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadSettings() {
        // Load connection settings
        projectPathField.setText(settings.getProjectPath());
        outputDirField.setText(settings.getOutputDir());
        oracleUrlField.setText(settings.getOracleUrl());
        oracleUserField.setText(settings.getOracleUser());
        oraclePasswordField.setText(settings.getOraclePassword());
        postgresUrlField.setText(settings.getPostgresUrl());
        postgresUserField.setText(settings.getPostgresUser());
        postgresPasswordField.setText(settings.getPostgresPassword());

        // Load report settings
        includeSqlContentCheckbox.setSelected(settings.isIncludeSqlContent());
        includeJsFormsCheckbox.setSelected(settings.isIncludeJsForms());
        includeTablesViewsCheckbox.setSelected(settings.isIncludeTablesViews());
        includeViewTablesCheckbox.setSelected(settings.isIncludeViewTables());
        includeJsUnitCompositionsCheckbox.setSelected(settings.isIncludeJsUnitCompositions());
        includeViewDetailsCheckbox.setSelected(settings.isIncludeViewDetails());

        // Reset status labels
        oracleStatusLabel.setText("Не проверено");
        oracleStatusLabel.setForeground(Color.GRAY);
        oracleDetailsArea.setText("");
        oracleDetailsArea.setBackground(new Color(255, 255, 245));

        postgresStatusLabel.setText("Не проверено");
        postgresStatusLabel.setForeground(Color.GRAY);
        postgresDetailsArea.setText("");
        postgresDetailsArea.setBackground(new Color(255, 255, 245));
    }

    private void saveSettings() {
        // Save connection settings
        settings.setProjectPath(projectPathField.getText());
        settings.setOutputDir(outputDirField.getText());
        settings.setOracleUrl(oracleUrlField.getText());
        settings.setOracleUser(oracleUserField.getText());
        settings.setOraclePassword(new String(oraclePasswordField.getPassword()));
        settings.setPostgresUrl(postgresUrlField.getText());
        settings.setPostgresUser(postgresUserField.getText());
        settings.setPostgresPassword(new String(postgresPasswordField.getPassword()));

        // Save report settings
        settings.setIncludeSqlContent(includeSqlContentCheckbox.isSelected());
        settings.setIncludeJsForms(includeJsFormsCheckbox.isSelected());
        settings.setIncludeTablesViews(includeTablesViewsCheckbox.isSelected());
        settings.setIncludeViewTables(includeViewTablesCheckbox.isSelected());
        settings.setIncludeJsUnitCompositions(includeJsUnitCompositionsCheckbox.isSelected());
        settings.setIncludeViewDetails(includeViewDetailsCheckbox.isSelected());

        // Save to file
        settings.saveSettings();

        // Update global configs
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

        JOptionPane.showMessageDialog(this,
                "Настройки сохранены!\nПерезапуск анализа не требуется.",
                "Успешно", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setPreset(ReportConfig.Preset preset) {
        ReportConfig.setPreset(preset);
        includeSqlContentCheckbox.setSelected(ReportConfig.isIncludeSqlContent());
        includeJsFormsCheckbox.setSelected(ReportConfig.isIncludeJsForms());
        includeTablesViewsCheckbox.setSelected(ReportConfig.isIncludeTablesViews());
        includeViewTablesCheckbox.setSelected(ReportConfig.isIncludeViewTables());
        includeJsUnitCompositionsCheckbox.setSelected(ReportConfig.isIncludeJsUnitCompositions());
        includeViewDetailsCheckbox.setSelected(ReportConfig.isIncludeViewDetails());
    }
}