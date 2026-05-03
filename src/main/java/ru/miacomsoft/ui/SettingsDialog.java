package ru.miacomsoft.ui;

import ru.miacomsoft.model.ReportConfig;
import ru.miacomsoft.model.SettingsModel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

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
        setSize(800, 600);
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

        // Project Path
        gbc.gridx = 0;
        gbc.gridy = 0;
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

        // Output Directory
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Каталог для отчетов:"), gbc);

        gbc.gridx = 1;
        outputDirField = new JTextField();
        panel.add(outputDirField, gbc);

        gbc.gridx = 2;
        JButton browseOutputButton = new JButton("Обзор...");
        browseOutputButton.addActionListener(e -> browseFolder(outputDirField));
        panel.add(browseOutputButton, gbc);

        // Separator
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        panel.add(createSeparator("Oracle Database"), gbc);

        // Oracle URL
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Oracle URL:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        oracleUrlField = new JTextField();
        panel.add(oracleUrlField, gbc);

        // Oracle User
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Oracle User:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        oracleUserField = new JTextField();
        panel.add(oracleUserField, gbc);

        // Oracle Password
        gbc.gridx = 0;
        gbc.gridy = 5;
        panel.add(new JLabel("Oracle Password:"), gbc);

        gbc.gridx = 1;
        oraclePasswordField = new JPasswordField();
        panel.add(oraclePasswordField, gbc);

        // Separator
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 3;
        panel.add(createSeparator("PostgreSQL Database"), gbc);

        // PostgreSQL URL
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 1;
        panel.add(new JLabel("PostgreSQL URL:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        postgresUrlField = new JTextField();
        panel.add(postgresUrlField, gbc);

        // PostgreSQL User
        // PostgreSQL User
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 1;
        panel.add(new JLabel("PostgreSQL User:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        postgresUserField = new JTextField();
        panel.add(postgresUserField, gbc);

        // PostgreSQL Password
        gbc.gridx = 0;
        gbc.gridy = 9;
        panel.add(new JLabel("PostgreSQL Password:"), gbc);

        gbc.gridx = 1;
        postgresPasswordField = new JPasswordField();
        panel.add(postgresPasswordField, gbc);

        // Fill remaining space
        gbc.gridy = 10;
        gbc.weighty = 1.0;
        panel.add(new JPanel(), gbc);

        return panel;
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

        // Create checkboxes with descriptions
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
        // Загружаем настройки подключений
        projectPathField.setText(settings.getProjectPath());
        outputDirField.setText(settings.getOutputDir());
        oracleUrlField.setText(settings.getOracleUrl());
        oracleUserField.setText(settings.getOracleUser());
        oraclePasswordField.setText(settings.getOraclePassword());
        postgresUrlField.setText(settings.getPostgresUrl());
        postgresUserField.setText(settings.getPostgresUser());
        postgresPasswordField.setText(settings.getPostgresPassword());

        // Загружаем настройки отчета из SettingsModel (которые уже загружены из файла)
        includeSqlContentCheckbox.setSelected(settings.isIncludeSqlContent());
        includeJsFormsCheckbox.setSelected(settings.isIncludeJsForms());
        includeTablesViewsCheckbox.setSelected(settings.isIncludeTablesViews());
        includeViewTablesCheckbox.setSelected(settings.isIncludeViewTables());
        includeJsUnitCompositionsCheckbox.setSelected(settings.isIncludeJsUnitCompositions());
        includeViewDetailsCheckbox.setSelected(settings.isIncludeViewDetails());
    }

    private void saveSettings() {
        // Сохраняем настройки подключений
        settings.setProjectPath(projectPathField.getText());
        settings.setOutputDir(outputDirField.getText());
        settings.setOracleUrl(oracleUrlField.getText());
        settings.setOracleUser(oracleUserField.getText());
        settings.setOraclePassword(new String(oraclePasswordField.getPassword()));
        settings.setPostgresUrl(postgresUrlField.getText());
        settings.setPostgresUser(postgresUserField.getText());
        settings.setPostgresPassword(new String(postgresPasswordField.getPassword()));

        // Сохраняем настройки отчета через сеттеры SettingsModel
        settings.setIncludeSqlContent(includeSqlContentCheckbox.isSelected());
        settings.setIncludeJsForms(includeJsFormsCheckbox.isSelected());
        settings.setIncludeTablesViews(includeTablesViewsCheckbox.isSelected());
        settings.setIncludeViewTables(includeViewTablesCheckbox.isSelected());
        settings.setIncludeJsUnitCompositions(includeJsUnitCompositionsCheckbox.isSelected());
        settings.setIncludeViewDetails(includeViewDetailsCheckbox.isSelected());

        // Сохраняем все в файл
        settings.saveSettings();
    }

    private void setPreset(ReportConfig.Preset preset) {
        ReportConfig.setPreset(preset);
        // Обновляем чекбоксы в соответствии с выбранным пресетом
        includeSqlContentCheckbox.setSelected(ReportConfig.isIncludeSqlContent());
        includeJsFormsCheckbox.setSelected(ReportConfig.isIncludeJsForms());
        includeTablesViewsCheckbox.setSelected(ReportConfig.isIncludeTablesViews());
        includeViewTablesCheckbox.setSelected(ReportConfig.isIncludeViewTables());
        includeJsUnitCompositionsCheckbox.setSelected(ReportConfig.isIncludeJsUnitCompositions());
        includeViewDetailsCheckbox.setSelected(ReportConfig.isIncludeViewDetails());
    }
}