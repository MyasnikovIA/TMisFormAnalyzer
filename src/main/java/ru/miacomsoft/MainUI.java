package ru.miacomsoft;

import javax.swing.*;
import ru.miacomsoft.ui.MainFrame;

public class MainUI {
    public static void main(String[] args) {
        // Запускаем UI
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);

            // Автоматически запускаем анализ при старте (опционально)
            // mainFrame.startAnalysisAutomatically();
        });
    }
}