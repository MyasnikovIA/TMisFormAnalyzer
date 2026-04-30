package ru.miacomsoft.service;

import ru.miacomsoft.model.FormInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Сервис для разрешения переопределений форм из UserForms
 * Реализует логику:
 * 1. Полное переопределение - если в UserForms есть .frm файл по тому же пути
 * 2. Частичное переопределение - если есть .d каталог с .dfrm файлами
 */
public class UserFormsResolver {

    private final FileScannerService scannerService;
    private final DfmOverrideProcessor dfmProcessor;

    public UserFormsResolver(FileScannerService scannerService) {
        this.scannerService = scannerService;
        this.dfmProcessor = new DfmOverrideProcessor();
    }

    /**
     * Разрешить все переопределения для указанной формы
     * @param formPath Путь к форме (относительно Forms/)
     * @return FormInfo с информацией о переопределениях
     */
    public FormInfo resolveOverrides(String formPath) {
        FormInfo formInfo = new FormInfo(formPath);

        // Получаем корень проекта из scannerService
        Path projectRoot = scannerService.getProjectRoot();

        // Получаем все регионы UserForms
        List<String> regions = scannerService.findAllUserFormsRegions();

        // Сортируем регионы для предсказуемого порядка
        regions.sort(String::compareTo);

        for (String region : regions) {
            // Формируем путь к файлу в регионе UserForms
            Path regionFullOverride = projectRoot.resolve(region).resolve(formPath);

            // ПРОВЕРКА 1: Полное переопределение (.frm файл)
            if (Files.exists(regionFullOverride) && formPath.endsWith(".frm")) {
                formInfo.setFullyReplaced(true);
                formInfo.setReplacementPath(regionFullOverride.toString());
                formInfo.addOverride(new FormInfo.OverrideInfo(
                        region,
                        regionFullOverride.toString(),
                        FormInfo.OverrideInfo.OverrideType.FULL_OVERRIDE
                ));
                break;
            }

            // ПРОВЕРКА 2: Частичное переопределение (.d каталог)
            // Убираем .frm для поиска .d каталога
            String formPathWithoutExt = formPath;
            if (formPathWithoutExt.endsWith(".frm")) {
                formPathWithoutExt = formPathWithoutExt.substring(0, formPathWithoutExt.length() - 4);
            }

            Path dotDPath = projectRoot.resolve(region).resolve(formPathWithoutExt + ".d");
            if (Files.exists(dotDPath) && Files.isDirectory(dotDPath)) {
                try (Stream<Path> walk = Files.walk(dotDPath)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".dfrm"))
                            .forEach(dfrmFile -> {
                                String dfrmContent = scannerService.readFileContent(dfrmFile);
                                if (dfrmContent != null && !dfrmContent.isEmpty()) {
                                    List<DfmOverrideProcessor.OverrideOperation> operations =
                                            dfmProcessor.parseDfrmFile(dfrmContent);

                                    for (DfmOverrideProcessor.OverrideOperation op : operations) {
                                        formInfo.addOverride(new FormInfo.OverrideInfo(
                                                region,
                                                dfrmFile.toString(),
                                                FormInfo.OverrideInfo.OverrideType.DOT_D_OVERRIDE,
                                                op.getTarget(),
                                                op.getPosition()
                                        ));
                                    }
                                }
                            });
                } catch (IOException e) {
                    System.err.println("Error scanning .d directory: " + e.getMessage());
                }
            }
        }

        return formInfo;
    }

    /**
     * Получить финальное содержимое формы после применения всех переопределений
     * @param formInfo Информация о форме
     * @return Финальное XML содержимое
     */
    public String getFinalFormContent(FormInfo formInfo) {
        // Если форма полностью заменена - читаем файл замены
        if (formInfo.isFullyReplaced() && formInfo.getReplacementPath() != null) {
            Path replacementPath = Path.of(formInfo.getReplacementPath());
            String content = scannerService.readFileContent(replacementPath);
            if (content == null) {
                System.err.println("Cannot read replacement file: " + replacementPath);
                return null;
            }
            return content;
        }

        // Читаем базовую форму
        Path baseFormPath = scannerService.getBaseFormPath(formInfo.getFormPath());
        String baseContent = scannerService.readFileContent(baseFormPath);

        if (baseContent == null) {
            System.err.println("Cannot read base form: " + baseFormPath);
            return null;
        }

        // Применяем все .dfrm переопределения
        String finalContent = baseContent;
        for (FormInfo.OverrideInfo override : formInfo.getOverrides()) {
            if (override.getType() == FormInfo.OverrideInfo.OverrideType.DOT_D_OVERRIDE) {
                // Читаем .dfrm файл
                Path dfrmPath = Path.of(override.getOverridePath());
                String dfrmContent = scannerService.readFileContent(dfrmPath);
                if (dfrmContent != null && !dfrmContent.isEmpty()) {
                    try {
                        String applied = dfmProcessor.applyOverrides(finalContent, dfrmContent);
                        if (applied != null) {
                            finalContent = applied;
                        }
                    } catch (Exception e) {
                        System.err.println("Error applying override " + dfrmPath + ": " + e.getMessage());
                    }
                }
            }
        }

        return finalContent;
    }
}