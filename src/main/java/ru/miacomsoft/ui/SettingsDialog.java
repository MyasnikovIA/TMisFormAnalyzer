package ru.miacomsoft.ui;

import ru.miacomsoft.model.SettingsModel;
import javax.swing.*;
import java.awt.*;

public class SettingsDialog extends JDialog {
    private final SettingsModel settings;
    private JTextField projectPathField;
    private JTextField outputDirField;
    private JTextField oracleUrlField;
    private JTextField oracleUserField;
    private JPasswordField oraclePasswordField;
    private JTextField postgresUrlField;
    private JTextField postgresUserField;
    private JPasswordField postgresPasswordField;
    private boolean saved = false;
    
    public SettingsDialog(JFrame parent, SettingsModel settings) {
        super(parent, "Настройки", true);
        this.settings = settings;

        setLayout(new BorderLayout());
        setSize(600, 500);
        setLocationRelativeTo(parent);

        JPanel mainPanel = createMainPanel();
        JPanel buttonPanel = createButtonPanel();

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        loadSettings();
    }

    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Проект
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Путь к проекту:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        projectPathField = new JTextField();
        panel.add(projectPathField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton browseProjectBtn = new JButton("Обзор");
        browseProjectBtn.addActionListener(e -> browseProject());
        panel.add(browseProjectBtn, gbc);

        // Директория отчетов
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Директория отчетов:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        outputDirField = new JTextField();
        panel.add(outputDirField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton browseOutputBtn = new JButton("Обзор");
        browseOutputBtn.addActionListener(e -> browseOutputDir());
        panel.add(browseOutputBtn, gbc);

        // Разделитель
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        panel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;

        // Oracle секция
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        JLabel oracleLabel = new JLabel("OracleSQL");
        oracleLabel.setFont(new Font("Arial", Font.BOLD, 12));
        panel.add(oracleLabel, gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 4;
        panel.add(new JLabel("URL:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        oracleUrlField = new JTextField();
        panel.add(oracleUrlField, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 5;
        panel.add(new JLabel("User:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        oracleUserField = new JTextField();
        panel.add(oracleUserField, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 6;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        oraclePasswordField = new JPasswordField();
        panel.add(oraclePasswordField, gbc);
        gbc.gridwidth = 1;

        // Разделитель
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 3;
        panel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;

        // PostgreSQL секция
        gbc.gridy = 8;
        gbc.gridwidth = 3;
        JLabel postgresLabel = new JLabel("PostgreSQL");
        postgresLabel.setFont(new Font("Arial", Font.BOLD, 12));
        panel.add(postgresLabel, gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 9;
        panel.add(new JLabel("URL:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        postgresUrlField = new JTextField();
        panel.add(postgresUrlField, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 10;
        panel.add(new JLabel("User:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        postgresUserField = new JTextField();
        panel.add(postgresUserField, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 11;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        postgresPasswordField = new JPasswordField();
        panel.add(postgresPasswordField, gbc);
        gbc.gridwidth = 1;

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton saveButton = new JButton("Сохранить");
        JButton cancelButton = new JButton("Отмена");

        saveButton.addActionListener(e -> saveSettings());
        cancelButton.addActionListener(e -> dispose());

        panel.add(saveButton);
        panel.add(cancelButton);

        return panel;
    }

    private void browseProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Выберите корневой каталог проекта");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            projectPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void browseOutputDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Выберите директорию для отчетов");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadSettings() {
        projectPathField.setText(settings.getProjectPath());
        outputDirField.setText(settings.getOutputDir());
        oracleUrlField.setText(settings.getOracleUrl());
        oracleUserField.setText(settings.getOracleUser());
        oraclePasswordField.setText(settings.getOraclePassword());
        postgresUrlField.setText(settings.getPostgresUrl());
        postgresUserField.setText(settings.getPostgresUser());
        postgresPasswordField.setText(settings.getPostgresPassword());
    }

    private void saveSettings() {
        settings.setProjectPath(projectPathField.getText().trim());
        settings.setOutputDir(outputDirField.getText().trim());
        settings.setOracleUrl(oracleUrlField.getText().trim());
        settings.setOracleUser(oracleUserField.getText().trim());
        settings.setOraclePassword(new String(oraclePasswordField.getPassword()));
        settings.setPostgresUrl(postgresUrlField.getText().trim());
        settings.setPostgresUser(postgresUserField.getText().trim());
        settings.setPostgresPassword(new String(postgresPasswordField.getPassword()));
        settings.saveSettings();
        saved = true;
        dispose();
    }

    public boolean isSaved() {
        return saved;
    }
}